package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticle.CrawlStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticle.RelevanceStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticle.TaggingStatus;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.tagging.client.OpenAiTaggingClient;
import com.solv.wefin.domain.news.tagging.dto.TaggingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 태깅 생성 전체 흐름을 관리하는 서비스.
 * 외부 API 호출은 트랜잭션 밖에서 수행하고,
 * DB 저장은 TaggingPersistenceService에서 트랜잭션으로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaggingService {

    private static final int BATCH_SIZE = 500;
    private static final int PROCESS_CHUNK_SIZE = 20;
    private static final int MAX_RETRY = 3;
    private static final int STALE_PROCESSING_MINUTES = 30;

    private final NewsArticleRepository newsArticleRepository;
    private final OpenAiTaggingClient openAiTaggingClient;
    private final TaggingPersistenceService persistenceService;

    /**
     * 크롤링 완료 + 태깅 미완료 기사를 조회하여 태그를 생성한다.
     */
    public void tagPendingArticles() {
        List<NewsArticle> targets = findTaggingTargets();
        log.info("태깅 대상 기사 수: {}", targets.size());

        if (targets.isEmpty()) {
            return;
        }

        persistenceService.markProcessing(targets);

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < targets.size(); i += PROCESS_CHUNK_SIZE) {
            List<NewsArticle> chunk = targets.subList(i, Math.min(i + PROCESS_CHUNK_SIZE, targets.size()));
            int[] result = processChunk(chunk);
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
                PageRequest.of(0, BATCH_SIZE));
    }

    private int[] processChunk(List<NewsArticle> articles) {
        List<NewsArticleTag> allTags = new ArrayList<>();
        List<NewsArticle> successArticles = new ArrayList<>();
        int failCount = 0;

        for (NewsArticle article : articles) {
            try {
                List<NewsArticleTag> tags = generateTagsForArticle(article);
                allTags.addAll(tags);
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

    private List<NewsArticleTag> generateTagsForArticle(NewsArticle article) {
        TaggingResult result = openAiTaggingClient.analyzeTags(article.getTitle(), article.getContent());

        if (result.isEmpty()) {
            throw new IllegalStateException("태깅 결과가 비어있습니다");
        }

        article.updateSummary(result.getSummary());
        article.updateRelevance(RelevanceStatus.from(result.getRelevance()));

        List<NewsArticleTag> tags = new ArrayList<>();

        if (result.getStocks() != null) {
            for (TaggingResult.TagItem item : result.getStocks()) {
                tags.add(NewsArticleTag.builder()
                        .newsArticleId(article.getId())
                        .tagType(TagType.STOCK)
                        .tagCode(item.getCode())
                        .tagName(item.getName())
                        .build());
            }
        }

        if (result.getSectors() != null) {
            for (TaggingResult.TagItem item : result.getSectors()) {
                tags.add(NewsArticleTag.builder()
                        .newsArticleId(article.getId())
                        .tagType(TagType.SECTOR)
                        .tagCode(item.getCode())
                        .tagName(item.getName())
                        .build());
            }
        }

        if (result.getTopics() != null) {
            for (TaggingResult.TagItem item : result.getTopics()) {
                tags.add(NewsArticleTag.builder()
                        .newsArticleId(article.getId())
                        .tagType(TagType.TOPIC)
                        .tagCode(item.getCode())
                        .tagName(item.getName())
                        .build());
            }
        }

        return tags;
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
