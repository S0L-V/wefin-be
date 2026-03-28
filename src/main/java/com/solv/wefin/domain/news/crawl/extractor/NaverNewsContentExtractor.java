package com.solv.wefin.domain.news.crawl.extractor;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NaverNewsContentExtractor implements ArticleContentExtractor {

    private static final int MIN_CONTENT_LENGTH = 50;
    private static final String NAVER_NEWS_HOST = "n.news.naver.com";

    private static final String[] BODY_SELECTORS = {
            "div#newsct_article",
            "div#articeBody",
            "div._article_body"
    };

    private static final String NOISE_SELECTORS =
            "script, style, .reporter_area, .byline, .copyright, .source";

    @Override
    public boolean supports(String url) {
        return url != null && url.contains(NAVER_NEWS_HOST);
    }

    @Override
    public String extract(Document document) {
        Elements body = findArticleBody(document);
        if (body.isEmpty()) {
            return null;
        }

        body.select(NOISE_SELECTORS).remove();
        String text = body.text().trim();
        return text.length() > MIN_CONTENT_LENGTH ? text : null;
    }

    private Elements findArticleBody(Document document) {
        for (String selector : BODY_SELECTORS) {
            Elements body = document.select(selector);
            if (!body.isEmpty()) {
                return body;
            }
        }
        return new Elements();
    }
}
