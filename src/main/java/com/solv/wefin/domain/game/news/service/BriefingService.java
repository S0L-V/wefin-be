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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    private final NewsCrawlService newsCrawlService;
    private final OpenAiBriefingClient openAiBriefingClient;
    private final BriefingCacheRepository briefingCacheRepository;

    /** 날짜별 in-process 락 — 같은 날짜 동시 요청의 크롤링/OpenAI 중복 호출을 직렬화한다. */
    private final ConcurrentHashMap<LocalDate, Object> dateLocks = new ConcurrentHashMap<>();

    /** 특정 날짜의 AI 브리핑을 briefing_cache에서 조회하거나, 없으면 크롤링 + OpenAI로 생성해 저장한다. */
    public String getBriefingForDate(LocalDate date) {
        Optional<BriefingCache> cached = briefingCacheRepository.findByTargetDate(date);
        if (cached.isPresent()) {
            return cached.get().getBriefingText();
        }

        Object lock = dateLocks.computeIfAbsent(date, k -> new Object());
        synchronized (lock) {
            try {
                Optional<BriefingCache> rechecked = briefingCacheRepository.findByTargetDate(date);
                if (rechecked.isPresent()) {
                    return rechecked.get().getBriefingText();
                }

                log.info("[브리핑] 생성 시작: date={}", date);

                List<GameNewsArchive> news = newsCrawlService.crawlAndSave(date);
                log.info("[브리핑] 수집된 뉴스: {}건 (date={})", news.size(), date);

                if (news.isEmpty()) {
                    log.warn("[브리핑] 뉴스 없음 → 폴백 반환, briefing_cache 미저장: date={}", date);
                    return buildDefaultBriefing(date);
                }

                String briefingText;
                try {
                    briefingText = generateBriefingViaOpenAi(date, news);
                } catch (Exception e) {
                    log.error("[브리핑] OpenAI 호출 실패 → 폴백 반환, briefing_cache 미저장: date={}, error={}",
                            date, e.getMessage());
                    return buildDefaultBriefing(date, news);
                }

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
            } finally {
                dateLocks.remove(date);
            }
        }
    }

    private String generateBriefingViaOpenAi(LocalDate date, List<GameNewsArchive> news) {
        List<ArticleSummary> summaries = news.stream()
                .map(n -> new ArticleSummary(
                        n.getTitle(), n.getSummary(),
                        n.getOriginalUrl(), n.getCategory()))
                .toList();

        return openAiBriefingClient.generateBriefing(date, summaries);
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
