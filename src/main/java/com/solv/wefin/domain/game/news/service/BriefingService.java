package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.entity.BriefingCache;
import com.solv.wefin.domain.game.news.entity.GameNewsArchive;
import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.openai.OpenAiBriefingClient;
import com.solv.wefin.domain.game.openai.OpenAiBriefingClient.ArticleSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    private final NewsCrawlService newsCrawlService;
    private final OpenAiBriefingClient openAiBriefingClient;
    private final BriefingCacheRepository briefingCacheRepository;

    /**
     * 특정 날짜의 AI 브리핑을 조회하거나 생성한다.
     * 1. briefing_cache에서 캐시 조회
     * 2. 캐시 미스 → 뉴스 크롤링 + OpenAI 브리핑 생성 → 캐시 저장
     *
     * @return 브리핑 텍스트
     */
    @Transactional
    public String getBriefingForDate(LocalDate date) {
        // 1. 캐시 조회
        Optional<BriefingCache> cached = briefingCacheRepository.findByTargetDate(date);
        if (cached.isPresent()) {
            log.debug("[브리핑] 캐시 히트: date={}", date);
            return cached.get().getBriefingText();
        }

        log.info("[브리핑] 생성 시작: date={}", date);

        // 2. 뉴스 크롤링 + DB 저장
        List<GameNewsArchive> news = newsCrawlService.crawlAndSave(date);
        log.info("[브리핑] 수집된 뉴스: {}건 (date={})", news.size(), date);

        // 3. OpenAI 브리핑 생성
        String briefingText = generateBriefing(date, news);

        // 4. 캐시 저장 (동시 요청 시 UNIQUE 위반 → 이미 저장된 데이터 반환)
        try {
            BriefingCache cache = BriefingCache.create(date, briefingText);
            briefingCacheRepository.save(cache);
        } catch (DataIntegrityViolationException e) {
            log.info("[브리핑] 동시 생성 감지, 기존 캐시 사용: date={}", date);
            return briefingCacheRepository.findByTargetDate(date)
                    .map(BriefingCache::getBriefingText)
                    .orElse(briefingText);
        }

        return briefingText;
    }

    private String generateBriefing(LocalDate date, List<GameNewsArchive> news) {
        if (news.isEmpty()) {
            return buildDefaultBriefing(date);
        }

        try {
            // Entity → ArticleSummary 변환 (openai 패키지가 news Entity에 의존하지 않도록)
            List<ArticleSummary> summaries = news.stream()
                    .map(n -> new ArticleSummary(
                            n.getTitle(), n.getSummary(),
                            n.getOriginalUrl(), n.getCategory()))
                    .toList();

            return openAiBriefingClient.generateBriefing(date, summaries);
        } catch (Exception e) {
            log.error("[브리핑] OpenAI 호출 실패: date={}, error={}", date, e.getMessage());
            return buildDefaultBriefing(date, news);
        }
    }

    private String buildDefaultBriefing(LocalDate date) {
        return String.format("[%s 시장 브리핑]\n이 날짜의 뉴스 데이터가 없습니다. "
                + "주식 차트와 거래 데이터를 직접 분석하여 투자 결정을 내려보세요.", date);
    }

    private String buildDefaultBriefing(LocalDate date, List<GameNewsArchive> news) {
        String headlines = news.stream()
                .limit(3)
                .map(n -> "• " + n.getTitle())
                .collect(Collectors.joining("\n"));

        return String.format("[%s 시장 브리핑]\n\n주요 뉴스:\n%s\n\n"
                + "차트와 거래량을 참고하여 신중하게 투자 결정을 내리세요.", date, headlines);
    }
}
