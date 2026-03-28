package com.solv.wefin.domain.news.crawl.extractor;

import org.jsoup.nodes.Document;

public interface ArticleContentExtractor {

    /**
     * 주어진 URL을 이 extractor로 처리할 수 있는지 판단한다.
     *
     * @param url 기사 원문 URL
     * @return 처리 가능하면 true, 아니면 false
     */
    boolean supports(String url);

    /**
     * HTML Document에서 기사 본문 텍스트를 추출한다.
     *
     * @param document Jsoup으로 파싱된 HTML Document
     * @return 추출된 본문 텍스트, 추출 실패 시 null
     */
    String extract(Document document);
}
