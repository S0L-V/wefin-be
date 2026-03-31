package com.solv.wefin.domain.news.ingestion.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultArticleContentExtractor implements ArticleContentExtractor {

    private static final int MIN_CONTENT_LENGTH = 200;

    private static final String[] CONTENT_SELECTORS = {
            "article",
            "div.article_body", "div.article-body",
            "div#articleBody", "div#article-body",
            "div.news_body", "div.story-body",
            "div[itemprop=articleBody]",
            "div.article_txt", "div.article-txt",
            "div#newsContent", "div.news-content",
            "div.view_article", "div.view-article"
    };

    private static final String NOISE_SELECTORS =
            "script, style, nav, aside, footer, .ad, .advertisement, .related";

    private static final Set<String> SKIP_TAGS = Set.of("nav", "aside", "footer");

    @Override
    public boolean supports(String url) {
        return true;
    }

    @Override
    public String extract(Document document) {
        // 1. 공통 CSS 셀렉터로 본문 탐색
        String content = extractBySelectors(document);
        if (content != null) {
            return content;
        }

        // 2. 가장 큰 텍스트 블록 탐색
        content = findLargestTextBlock(document);
        if (content != null && content.length() >= MIN_CONTENT_LENGTH) {
            return content;
        }

        // 3. og:description 폴백
        return extractOgDescription(document);
    }

    private String extractBySelectors(Document document) {
        for (String selector : CONTENT_SELECTORS) {
            Elements elements = document.select(selector);
            if (!elements.isEmpty()) {
                elements.select(NOISE_SELECTORS).remove();
                String text = elements.first().text().trim();
                if (text.length() >= MIN_CONTENT_LENGTH) {
                    return text;
                }
            }
        }
        return null;
    }

    private String findLargestTextBlock(Document document) {
        String longest = "";
        for (Element div : document.select("div")) {
            if (isSkipElement(div)) {
                continue;
            }
            String text = div.text().trim();
            if (text.length() > longest.length()) {
                longest = text;
            }
        }
        return longest.isEmpty() ? null : longest;
    }

    private boolean isSkipElement(Element element) {
        if (SKIP_TAGS.contains(element.tagName())) {
            return true;
        }
        Element parent = element.parent();
        return parent != null && SKIP_TAGS.contains(parent.tagName());
    }

    private String extractOgDescription(Document document) {
        String ogDesc = document.select("meta[property=og:description]").attr("content");
        return ogDesc.isBlank() ? null : ogDesc;
    }
}
