package com.solv.wefin.domain.news.ingestion.service;

import com.solv.wefin.domain.news.ingestion.crawler.ArticleContentExtractor;
import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticle.CrawlStatus;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.config.NewsBatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.data.domain.PageRequest;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCrawlService {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; WefinBot/1.0)";
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;
    private static final int MAX_RETRY = 3;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final NewsArticleRepository newsArticleRepository;
    private final ArticleCrawlPersistenceService persistenceService;
    private final List<ArticleContentExtractor> extractors;
    private final RestTemplate newsRestTemplate;
    private final NewsBatchProperties batchProperties;

    /**
     * PENDING/FAILED 상태의 기사를 조회하여 원문 HTML을 크롤링하고 본문/썸네일을 저장한다.
     */
    public void crawlPendingArticles() {
        List<NewsArticle> targets = findCrawlTargets();
        log.info("크롤링 대상 기사 수: {}", targets.size());

        int successCount = 0;
        int failCount = 0;

        for (NewsArticle article : targets) {
            if (crawlSingleArticle(article)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("크롤링 완료 - 성공: {}, 실패: {}", successCount, failCount);
    }

    private List<NewsArticle> findCrawlTargets() {
        return newsArticleRepository
                .findByCrawlStatusInAndCrawlRetryCountLessThanOrderByCollectedAtDesc(
                        List.of(CrawlStatus.PENDING, CrawlStatus.FAILED),
                        MAX_RETRY,
                        PageRequest.of(0, batchProperties.crawlSize()));
    }

    private boolean crawlSingleArticle(NewsArticle article) {
        try {
            String html = fetchHtml(article.getOriginalUrl());
            if (html == null || html.isBlank()) {
                persistenceService.saveCrawlFailure(article.getId(), "Empty HTML response");
                return false;
            }

            Document doc = Jsoup.parse(html, article.getOriginalUrl());
            return extractAndSave(article, doc);

        } catch (Exception e) {
            handleCrawlException(article, e);
            return false;
        }
    }

    private boolean extractAndSave(NewsArticle article, Document doc) {
        Optional<String> content = extractContent(article.getOriginalUrl(), doc);
        if (content.isEmpty()) {
            persistenceService.saveCrawlFailure(article.getId(), "Content extraction returned empty");
            return false;
        }

        String thumbnailUrl = extractOgImage(doc).orElse(null);
        persistenceService.saveCrawlSuccess(article.getId(), content.get(), thumbnailUrl);
        log.debug("크롤링 성공 - id: {}, contentLength: {}", article.getId(), content.get().length());
        return true;
    }

    private void handleCrawlException(NewsArticle article, Exception e) {
        String errorMessage = truncateErrorMessage(e);
        persistenceService.saveCrawlFailure(article.getId(), errorMessage);
        log.warn("크롤링 실패 - id: {}, url: {}, error: {}",
                article.getId(), article.getOriginalUrl(), e.getMessage());
    }

    private String truncateErrorMessage(Exception e) {
        if (e.getMessage() == null) {
            return "Unknown error";
        }
        return e.getMessage().substring(0, Math.min(e.getMessage().length(), ERROR_MESSAGE_MAX_LENGTH));
    }

    private String fetchHtml(String url) {
        validateUrl(url);
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        ResponseEntity<String> response = newsRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getBody();
    }

    /**
     * SSRF 방지를 위해 URL 스킴과 호스트를 검증한다.
     * http/https만 허용하고, 내부망/루프백/링크로컬 대역은 차단한다.
     */
    private void validateUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme) || uri.getHost() == null) {
            throw new IllegalArgumentException("Unsupported crawl URL scheme: " + url);
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                    throw new IllegalArgumentException("Blocked internal network URL: " + url);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve crawl URL: " + url, e);
        }
    }

    private Optional<String> extractOgImage(Document doc) {
        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage == null) {
            return Optional.empty();
        }
        String content = ogImage.attr("content");
        return content.isBlank() ? Optional.empty() : Optional.of(content);
    }

    private Optional<String> extractContent(String url, Document doc) {
        for (ArticleContentExtractor extractor : extractors) {
            if (extractor.supports(url)) {
                String content = extractor.extract(doc);
                if (content != null && !content.isBlank()) {
                    return Optional.of(content);
                }
            }
        }
        return Optional.empty();
    }
}
