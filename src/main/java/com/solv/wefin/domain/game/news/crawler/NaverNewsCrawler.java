package com.solv.wefin.domain.game.news.crawler;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class NaverNewsCrawler {

    private static final String SEARCH_URL = "https://finance.naver.com/news/news_search.naver";
    private static final int ARTICLES_PER_SECTOR = 3;
    private static final long CRAWL_DELAY_MS = 400;
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    /**
     * 섹터별 키워드 검색으로 해당 날짜의 뉴스를 크롤링한다.
     * DB 의존 없이 순수 크롤링만 수행.
     */
    public List<CrawledArticle> crawlBySectors(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<CrawledArticle> articles = new ArrayList<>();

        for (NewsSector sector : NewsSector.values()) {
            try {
                List<CrawledArticle> sectorArticles = crawlSector(date, dateStr, sector);
                articles.addAll(sectorArticles);
                Thread.sleep(CRAWL_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[크롤링 중단] 인터럽트 발생");
                break;
            } catch (Exception e) {
                log.error("[크롤링 실패] sector={}, date={}, error={}",
                        sector.getDisplayName(), date, e.getMessage());
            }
        }

        log.info("[크롤링 완료] date={}, 총 {}건", date, articles.size());
        return articles;
    }

    private List<CrawledArticle> crawlSector(LocalDate date, String dateStr,
                                              NewsSector sector) throws Exception {
        String encodedKeyword = URLEncoder.encode(sector.getSearchKeyword(), EUC_KR);
        String searchUrl = SEARCH_URL + "?q=" + encodedKeyword
                + "&pd=4&stDateStart=" + dateStr + "&stDateEnd=" + dateStr;

        Document doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .get();

        Elements links = doc.select("dd.articleSubject a");
        log.debug("[크롤링] sector={}, date={}, {}건 발견",
                sector.getDisplayName(), date, links.size());

        List<CrawledArticle> results = new ArrayList<>();
        int count = 0;

        for (Element link : links) {
            if (count >= ARTICLES_PER_SECTOR) break;

            String title = link.text().trim();
            if (title.isBlank()) continue;

            String href = link.absUrl("href");
            if (href.isBlank()) href = "https://finance.naver.com" + link.attr("href");
            if (href.isBlank()) continue;

            Thread.sleep(CRAWL_DELAY_MS);
            String summary = fetchArticleBody(href, title);

            OffsetDateTime publishedAt = date.atTime(9, 0).atOffset(KST);

            results.add(new CrawledArticle(
                    title, summary, "naver_finance", href,
                    publishedAt, sector.getDisplayName(), sector.getSearchKeyword()));
            count++;
        }

        return results;
    }

    private String fetchArticleBody(String articleUrl, String fallbackTitle) {
        try {
            String naverNewsUrl = convertToNaverNewsUrl(articleUrl);

            Document doc = Jsoup.connect(naverNewsUrl)
                    .userAgent(USER_AGENT)
                    .timeout(8_000)
                    .get();

            Element body = doc.selectFirst("#dic_area");
            if (body == null) body = doc.selectFirst(".newsct_article");
            if (body == null) return fallbackTitle;

            return Jsoup.clean(body.text().trim(), org.jsoup.safety.Safelist.none());
        } catch (Exception e) {
            log.warn("[크롤링] 본문 조회 실패: url={}, error={}", articleUrl, e.getMessage());
            return fallbackTitle;
        }
    }

    private String convertToNaverNewsUrl(String url) {
        try {
            String articleId = extractParam(url, "article_id");
            String officeId = extractParam(url, "office_id");
            if (!articleId.isBlank() && !officeId.isBlank()) {
                return "https://n.news.naver.com/mnews/article/" + officeId + "/" + articleId;
            }
        } catch (Exception e) {
            log.debug("[URL 변환 실패] url={}, error={}", url, e.getMessage());
        }
        return url;
    }

    private String extractParam(String url, String param) {
        try {
            // URL에 인코딩 안 된 한글/공백이 포함될 수 있어서 URI.create() 대신 직접 파싱
            int queryStart = url.indexOf('?');
            if (queryStart < 0) return "";
            String query = url.substring(queryStart + 1);
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals(param)) {
                    return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("[URL 파라미터 파싱 실패] url={}, param={}, error={}", url, param, e.getMessage());
        }
        return "";
    }
}
