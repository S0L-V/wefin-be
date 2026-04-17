package com.solv.wefin.domain.interest.service;

import com.solv.wefin.domain.interest.dto.InterestInfo;
import com.solv.wefin.domain.market.trend.service.UserMarketTrendCacheService;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SECTOR / TOPIC 관심사 등록·조회·해제 서비스
 *
 * STOCK은 trading 도메인의 {@link com.solv.wefin.domain.trading.watchlist.service.WatchlistService}가
 * 담당하며, 본 서비스는 동일한 {@code user_interest} 테이블을 interest_type으로 구분해 공유한다.
 *
 * 수동 등록 관심사와 피드백 기반 추천 가중치는 {@code manual_registered} 컬럼으로 분리되어,
 * 본 서비스의 목록/개수 제한/삭제는 {@code manual_registered = true} row만 대상으로 한다
 */
@Service
@RequiredArgsConstructor
public class InterestService {

    public static final BigDecimal ADD_WEIGHT = new BigDecimal(5); // 관심사 등록 가중치
    public static final int MAX_INTERESTS_PER_TYPE = 10; // 타입별 수동 등록 관심사 최대 개수
    private static final int MAX_CODE_LENGTH = 100; // 태그 코드 최대 길이

    private final UserInterestRepository userInterestRepository;
    private final NewsArticleTagRepository newsArticleTagRepository;
    private final ManualInterestLockService manualInterestLockService;
    private final UserMarketTrendCacheService userMarketTrendCacheService;

    @Transactional(readOnly = true)
    public List<InterestInfo> list(UUID userId, InterestType type) {
        assertSectorOrTopic(type);

        List<UserInterest> interests = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, type.name());
        if (interests.isEmpty()) return List.of();

        List<String> codes = interests.stream().map(UserInterest::getInterestValue).toList();
        Map<String, String> nameByCode = newsArticleTagRepository
                .findTagNamesByTagTypeAndTagCodes(type.name(), codes)
                .stream()
                .collect(Collectors.toMap(
                        NewsArticleTagRepository.TagNameProjection::getCode,
                        NewsArticleTagRepository.TagNameProjection::getName));

        return interests.stream()
                .map(interest -> {
                    String code = interest.getInterestValue();
                    return new InterestInfo(code, nameByCode.getOrDefault(code, code));
                })
                .toList();
    }

    @Transactional
    public void add(UUID userId, InterestType type, String rawCode) {
        assertSectorOrTopic(type);
        String code = normalize(rawCode);

        // 자유 입력 방지 — AI가 실제 부여한 태그만 관심사로 등록 가능
        if (!newsArticleTagRepository.existsByTagTypeAndTagCode(toTagType(type), code)) {
            throw new BusinessException(ErrorCode.INTEREST_TAG_NOT_FOUND);
        }

        // count + insert가 별도 쿼리라 동시 요청에서 한도가 깨질 수 있어 (userId, type) 단위 직렬화
        manualInterestLockService.acquire(userId, type);

        if (userInterestRepository.existsByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
                userId, type.name(), code)) {
            throw new BusinessException(ErrorCode.INTEREST_ALREADY_EXISTS);
        }

        if (userInterestRepository.countByUserIdAndInterestTypeAndManualRegisteredTrue(userId, type.name())
                >= MAX_INTERESTS_PER_TYPE) {
            throw new BusinessException(ErrorCode.INTEREST_LIMIT_EXCEEDED);
        }

        userInterestRepository.save(UserInterest.createManual(userId, type.name(), code, ADD_WEIGHT));
        // 관심사 변경 시 맞춤 동향 캐시 무효화 → 다음 personalized 호출이 새 관심사로 AI 재호출
        userMarketTrendCacheService.invalidateToday(userId);
    }

    @Transactional
    public void delete(UUID userId, InterestType type, String rawCode) {
        assertSectorOrTopic(type);
        String code = normalize(rawCode);
        // 피드백 기반 추천 가중치(manual_registered=false) row는 그대로 두고 수동 등록 row만 삭제
        userInterestRepository
                .deleteByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
                        userId, type.name(), code);
        userMarketTrendCacheService.invalidateToday(userId);
    }

    private void assertSectorOrTopic(InterestType type) {
        if (type != InterestType.SECTOR && type != InterestType.TOPIC) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalize(String rawCode) {
        if (rawCode == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        String trimmed = rawCode.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CODE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private NewsArticleTag.TagType toTagType(InterestType type) {
        return NewsArticleTag.TagType.valueOf(type.name());
    }
}
