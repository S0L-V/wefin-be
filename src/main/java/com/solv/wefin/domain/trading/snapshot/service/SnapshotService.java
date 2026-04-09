package com.solv.wefin.domain.trading.snapshot.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.snapshot.entity.DailySnapshot;
import com.solv.wefin.domain.trading.snapshot.repository.DailySnapshotRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SnapshotService {

	private final DailySnapshotRepository snapshotRepository;
	private final VirtualAccountService accountService;
	private final SnapshotPersistenceService snapshotPersistenceService;

	/**
	 * 전체 계좌의 일별 스냅샷을 생성
	 * 계좌별 독립 트랜잭셥으로 한 계좌 실패가 다른 계좌에 영향 없음
	 */
	public void createAllSnapshots(LocalDate date) {
		// TODO: 유저 수 증가 시 페이징 처리 고려 (findAll -> Slice/Page)
		List<VirtualAccount> accounts = accountService.getAllAccounts();
		int success = 0, skip = 0, fail = 0;
		for (VirtualAccount account : accounts) {
			try {
				boolean created = snapshotPersistenceService.createSnapshot(account, date);
				if (created) success++;
				else skip++;
			} catch (Exception e) {
				log.error("스냅샷 생성 실패: accountId={}", account.getVirtualAccountId(), e);
				fail++;
			}
		}
		log.info("스냅샷 생성 완료: 성공={}, 스킵={}, 실패={}", success, skip, fail);
	}

	/**
	 * 자산 추이 조회. from/to 미입력 시 최근 30일 기본값 적용
	 */
	@Transactional(readOnly = true)
	public List<DailySnapshot> getAssetHistory(Long virtualAccountId, LocalDate from, LocalDate to) {
		if (to == null) {
			to = LocalDate.now();
		}
		if (from == null) {
			from = to.minusDays(30);
		}
		if (from.isAfter(to)) {
			throw new BusinessException(ErrorCode.MARKET_INVALID_DATE);
		}

		return snapshotRepository.findByVirtualAccountIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
			virtualAccountId, from, to);
	}
}
