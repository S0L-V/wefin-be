package com.solv.wefin.domain.trading.ranking.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.ranking.dto.DailyRankingInfo;
import com.solv.wefin.domain.trading.ranking.dto.DailyRankingInfo.MyRankInfo;
import com.solv.wefin.domain.trading.ranking.dto.DailyRankingInfo.RankingItemInfo;
import com.solv.wefin.domain.trading.ranking.dto.DailyRankingRow;
import com.solv.wefin.domain.trading.ranking.repository.RankingQueryRepository;
import com.solv.wefin.domain.user.service.UserService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

	private static final int TOP_N = 10;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final RankingQueryRepository rankingQueryRepository;
	private final VirtualAccountService virtualAccountService;
	private final UserService userService;

	/**
	 * 오늘 (KST)의 일일 수익 랭킹을 조회
	 * TOP 10 + 호출자의 myRank 정보를 포함하며, callerUserId가 null 이거나
	 * 본인 거래가 없으면 myRank 는 null
	 */
	public DailyRankingInfo getDailyRanking(UUID callerUserId) {
		LocalDate today = LocalDate.now(KST);
		OffsetDateTime startInclusive = today.atStartOfDay(KST).toOffsetDateTime();
		OffsetDateTime endExclusive = today.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

		List<DailyRankingRow> allRows = rankingQueryRepository.findDailySellAggregates(
			startInclusive, endExclusive);

		List<RankingItemInfo> rankings = buildTopRanking(allRows);

		MyRankInfo myRank = buildMyRank(callerUserId, allRows);

		return new DailyRankingInfo(rankings, myRank);
	}

	/**
	 * 집계 결과에서 상위 N개를 추출하고 닉네임을 매핑하여 RankingItemInfo 리스트로 변환
	 * 닉네임 매핑이 누락된 행은 응답에서 제외되며, 그 경우 rank 는 연속 번호로 부여됨
	 */
	private List<RankingItemInfo> buildTopRanking(List<DailyRankingRow> allRows) {
		// 1. 상위 N개 추출
		List<DailyRankingRow> topRows = allRows.subList(0, Math.min(TOP_N, allRows.size()));

		// 2. virtualAccount -> userId 매핑
		List<Long> accountIds = topRows.stream()
			.map(DailyRankingRow::virtualAccountId)
			.toList();
		Map<Long, UUID> accountToUser = virtualAccountService.findAllByIdIn(accountIds).stream()
			.collect(Collectors.toMap(VirtualAccount::getVirtualAccountId, VirtualAccount::getUserId));

		// 3. userId -> nickname 매핑 (UserService 머지 전 임시)
		List<UUID> userIds = accountToUser.values().stream().distinct().toList();
		Map<UUID, String> userToNickname = userService.findAllByIdIn(userIds).stream()
			.collect(Collectors.toMap(User::getUserId, User::getNickname));

		// 4. RankingItemInfo 빌드 (rank 1부터 부여, 매핑 누락은 제외)
		List<RankingItemInfo> result = new ArrayList<>();
		int rank = 1;
		for (DailyRankingRow row : topRows) {
			UUID userId = accountToUser.get(row.virtualAccountId());
			String nickname = (userId != null) ? userToNickname.get(userId) : null;

			if (nickname == null) {
				log.warn("닉네임 매핑 누락 - virtualAccountId={}, userId={}", row.virtualAccountId(), userId);
				continue;
			}

			result.add(new RankingItemInfo(
				rank++,
				nickname,
				row.realizedProfitSum(),
				row.tradeCount().intValue()
			));
		}
		return result;
	}

	/**
	 * 호출자의 본인 순위를 계산
	 * 비로그인, 계좌 미생성, 오늘 매도 거래 없음 케이스는 모두 null 을 반환함
	 */
	private MyRankInfo buildMyRank(UUID callerUserId, List<DailyRankingRow> allRows) {
		if (callerUserId == null) {
			return null;
		}

		Long myAccountId;
		try {
			myAccountId = virtualAccountService.getAccountByUserId(callerUserId).getVirtualAccountId();
		} catch (BusinessException e) {
			if (e.getErrorCode() != ErrorCode.ACCOUNT_NOT_FOUND) {
				throw e; // 예상 외 에러는 그대로 전파
			}
			log.warn("myRank 계산 - 계좌 조회 실패: userId={}, code={}", callerUserId, e.getErrorCode());
			return null;
		}

		for (int i = 0; i < allRows.size(); i++) {
			DailyRankingRow row = allRows.get(i);
			if (row.virtualAccountId().equals(myAccountId)) {
				return new MyRankInfo(i + 1, row.realizedProfitSum());
			}
		}

		return null;
	}
}
