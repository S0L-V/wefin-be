package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticle.CrawlStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticle.RelevanceStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticle.TaggingStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.config.NewsBatchProperties;
import com.solv.wefin.domain.news.tagging.client.OpenAiTaggingClient;
import com.solv.wefin.domain.news.tagging.dto.TaggingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 태깅 생성 전체 흐름을 관리하는 서비스.
 * 외부 API 호출은 트랜잭션 밖에서 수행하고,
 * DB 저장은 TaggingPersistenceService에서 트랜잭션으로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaggingService {

    private static final int PROCESS_CHUNK_SIZE = 20;
    private static final int MAX_RETRY = 3;
    private static final int STALE_PROCESSING_MINUTES = 30;

    private final NewsArticleRepository newsArticleRepository;
    private final OpenAiTaggingClient openAiTaggingClient;
    private final TaggingPersistenceService persistenceService;
    private final StockCodeValidator stockCodeValidator;
    private final NewsBatchProperties batchProperties;

    /**
     * 크롤링 완료 + 태깅 미완료 기사를 조회하여 태그를 생성한다.
     */
    public void tagPendingArticles() {
        List<NewsArticle> targets = findTaggingTargets();
        log.info("태깅 대상 기사 수: {}", targets.size());

        if (targets.isEmpty()) {
            return;
        }

        // 종목 마스터 스냅샷을 markProcessing 전에 로드한다 (배치 1회 로드, 배치 종료 시 GC).
        Map<String, String> stockMap = stockCodeValidator.loadStockMap();
        log.debug("종목 마스터 스냅샷 로드 완료 - 코드 수: {}", stockMap.size());

        persistenceService.markProcessing(targets);

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < targets.size(); i += PROCESS_CHUNK_SIZE) {
            List<NewsArticle> chunk = targets.subList(i, Math.min(i + PROCESS_CHUNK_SIZE, targets.size()));
            int[] result = processChunk(chunk, stockMap);
            successCount += result[0];
            failCount += result[1];
        }

        log.info("태깅 완료 - 성공: {}, 실패: {}", successCount, failCount);
    }

    private List<NewsArticle> findTaggingTargets() {
        OffsetDateTime staleBefore = OffsetDateTime.now().minusMinutes(STALE_PROCESSING_MINUTES);
        return newsArticleRepository.findTaggingTargets(
                CrawlStatus.SUCCESS,
                List.of(TaggingStatus.PENDING, TaggingStatus.FAILED),
                TaggingStatus.PROCESSING,
                MAX_RETRY,
                staleBefore,
                PageRequest.of(0, batchProperties.taggingSize()));
    }

    private int[] processChunk(List<NewsArticle> articles, Map<String, String> stockMap) {
        List<NewsArticleTag> allTags = new ArrayList<>();
        List<NewsArticle> successArticles = new ArrayList<>();
        int failCount = 0;

        for (NewsArticle article : articles) {
            try {
                TagExtraction extracted = extractTags(article, stockMap);

                // 필터링 후 유효 태그가 0개면 FAILED로 전환
                if (extracted.tags().isEmpty()) {
                    log.warn("유효 태그 0개 - articleId: {}", article.getId());
                    persistenceService.markFailed(article.getId(), "유효 태그 0개 (AI 응답 품질)");
                    failCount++;
                    continue;
                }

                article.updateSummary(extracted.summary());
                article.updateRelevance(extracted.relevance());

                allTags.addAll(extracted.tags());
                successArticles.add(article);
            } catch (Exception e) {
                log.warn("태깅 실패 - articleId: {}", article.getId(), e);
                persistenceService.markFailed(article.getId(), e.getMessage());
                failCount++;
            }
        }

        if (!allTags.isEmpty()) {
            try {
                persistenceService.saveTagsBatch(allTags, successArticles);
            } catch (Exception e) {
                log.error("태깅 배치 저장 실패, 개별 fallback 시도: {}", e.getMessage());
                failCount += handleBatchSaveFailure(successArticles, e.getMessage());
                return new int[]{0, failCount};
            }
        }

        return new int[]{successArticles.size(), failCount};
    }

    /**
     * AI 응답을 태그 목록 + 요약 + 관련성으로 추출한다. 엔티티 부작용 없음(pure)
     *
     * <p>STOCK: 마스터에 없는 코드는 스킵, 정상 코드는 마스터의 canonical name으로 강제.
     * SECTOR/TOPIC: 대소문자/공백 정규화 + 중복 제거. 3종 태그 모두 (type+code) 기준 dedup</p>
     */
    private TagExtraction extractTags(NewsArticle article, Map<String, String> stockMap) {
        TaggingResult result = openAiTaggingClient.analyzeTags(article.getTitle(), article.getContent());

        if (result.isEmpty()) {
            throw new IllegalStateException("태깅 결과가 비어있습니다");
        }

        List<NewsArticleTag> tags = new ArrayList<>();

        if (result.getStocks() != null) {
            Set<String> seenStockCodes = new HashSet<>();
            for (TaggingResult.TagItem item : result.getStocks()) {
                if (item == null) {
                    log.warn("STOCK 배열 내 null 원소 스킵 - articleId: {}", article.getId());
                    continue;
                }
                String rawCode = item.getCode();
                String code = rawCode == null ? null : rawCode.trim();
                if (code == null || code.isEmpty() || !stockMap.containsKey(code)) {
                    log.warn("마스터에 없는 STOCK 코드 스킵 - articleId: {}, rejectedCode: {}, tagName: {}",
                            article.getId(), rawCode, item.getName());
                    continue;
                }
                if (!seenStockCodes.add(code)) {
                    continue; // AI가 같은 종목을 2번 반환한 경우 dedup
                }
                // 종목명은 AI 응답 대신 마스터의 canonical name으로 강제한다
                // (code/name 불일치로 인기 태그 집계가 쪼개지는 문제를 방지)
                tags.add(NewsArticleTag.builder()
                        .newsArticleId(article.getId())
                        .tagType(TagType.STOCK)
                        .tagCode(code)
                        .tagName(stockMap.get(code))
                        .build());
            }
        }

        if (result.getSectors() != null) {
            addNormalizedTags(tags, result.getSectors(), TagType.SECTOR, article.getId());
        }
        if (result.getTopics() != null) {
            addNormalizedTags(tags, result.getTopics(), TagType.TOPIC, article.getId());
        }

        return new TagExtraction(
                tags,
                result.getSummary(),
                RelevanceStatus.from(result.getRelevance())
        );
    }

    /**
     * SECTOR/TOPIC 태그를 정규화(대문자/trim)하여 중복 제거 후 추가한다.
     *
     * LLM이 같은 코드를 "TECH" / "Tech" / "  TECH " 같이 다양하게 반환할 수 있어
     * 인기 태그 집계에서 그룹이 쪼개지는 것을 방지한다. 이름은 AI 응답 그대로 사용
     * (SECTOR/TOPIC는 마스터 테이블이 없음)
     */
    private void addNormalizedTags(List<NewsArticleTag> tags, List<TaggingResult.TagItem> items,
                                   TagType type, Long articleId) {
        Set<String> seen = new HashSet<>();
        for (TaggingResult.TagItem item : items) {
            if (item == null) {
                log.warn("{} 배열 내 null 원소 스킵 - articleId: {}", type, articleId);
                continue;
            }
            String rawCode = item.getCode();
            if (rawCode == null) continue;
            String code = rawCode.trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) continue;
            if (!seen.add(code)) continue;

            tags.add(NewsArticleTag.builder()
                    .newsArticleId(articleId)
                    .tagType(type)
                    .tagCode(code)
                    .tagName(item.getName())
                    .build());
        }
    }

    /**
     * 태그 추출 결과 (엔티티 부작용 없이 순수 값으로 전달)
     */
    private record TagExtraction(List<NewsArticleTag> tags, String summary, RelevanceStatus relevance) {
    }

    private int handleBatchSaveFailure(List<NewsArticle> articles, String errorMessage) {
        int failCount = 0;
        for (NewsArticle article : articles) {
            try {
                persistenceService.markFailed(article.getId(), "배치 저장 실패: " + errorMessage);
                failCount++;
            } catch (Exception e) {
                log.error("개별 실패 마킹도 실패 - articleId: {}, error: {}", article.getId(), e.getMessage());
                failCount++;
            }
        }
        return failCount;
    }
}
