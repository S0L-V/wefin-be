package com.solv.wefin.domain.news.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.news.dto.CollectedNewsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class NaverNewsCollector implements NewsCollector {

    private static final String SOURCE_NAME = "NaverNews";
    private static final DateTimeFormatter RFC_1123 =
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH);

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "DEFAULT", List.of("경제", "주식", "금리", "환율"),
            "ECONOMY", List.of("경제", "금리", "환율"),
            "POLITICS", List.of("정치 경제", "경제 정책"),
            "SOCIETY", List.of("사회 경제", "부동산"),
            "IT_SCIENCE", List.of("IT 경제", "핀테크"),
            "GLOBAL", List.of("해외 경제", "글로벌 금융")
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NaverNewsCollector(@Qualifier("newsRestTemplate") RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${news.naver.client-id:}")
    private String clientId;

    @Value("${news.naver.client-secret:}")
    private String clientSecret;

    @Value("${news.naver.display:100}")
    private int display;

    @Value("${news.naver.base-url:https://openapi.naver.com/v1/search/news.json}")
    private String baseUrl;

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<CollectedNewsDto> collect(String category) {
        Map<String, CollectedNewsDto> resultMap = new LinkedHashMap<>();
        List<String> keywords = resolveKeywords(category);

        for (String keyword : keywords) {
            try {
                List<CollectedNewsDto> articles = fetchByKeyword(keyword);
                for (CollectedNewsDto dto : articles) {
                    resultMap.putIfAbsent(dto.getOriginalUrl(), dto);
                }
            } catch (Exception e) {
                log.warn("네이버 뉴스 수집 실패 - keyword: {}, error: {}", keyword, e.getMessage());
            }
        }

        log.info("NaverNews 수집 완료 - category: {}, keywords: {}, count: {}",
                category, keywords.size(), resultMap.size());

        return new ArrayList<>(resultMap.values());
    }

    private List<CollectedNewsDto> fetchByKeyword(String keyword) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("query", keyword)
                .queryParam("display", display)
                .queryParam("start", 1)
                .queryParam("sort", "date")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        List<CollectedNewsDto> results = new ArrayList<>();
        JsonNode root = parseJson(response.getBody());
        JsonNode items = root.path("items");

        for (JsonNode item : items) {
            try {
                String originalLink = item.path("originallink").asText("");
                String naverLink = item.path("link").asText("");
                String articleUrl = originalLink.isBlank() ? naverLink : originalLink;

                if (articleUrl.isBlank()) continue;

                CollectedNewsDto dto = CollectedNewsDto.builder()
                        .externalArticleId("naver:" + Math.abs(articleUrl.hashCode()))
                        .originalUrl(articleUrl)
                        .originalTitle(stripHtml(item.path("title").asText("")))
                        .originalContent(stripHtml(item.path("description").asText("")))
                        .originalThumbnailUrl(null)
                        .originalPublishedAt(parseNaverDate(item.path("pubDate").asText(null)))
                        .publisherName(extractPublisher(articleUrl))
                        .rawPayload(objectMapper.writeValueAsString(item))
                        .build();

                results.add(dto);
            } catch (Exception e) {
                log.warn("네이버 뉴스 기사 파싱 실패: {}", e.getMessage());
            }
        }

        return results;
    }

    private List<String> resolveKeywords(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_KEYWORDS.get("DEFAULT");
        }
        return CATEGORY_KEYWORDS.getOrDefault(category.toUpperCase(), CATEGORY_KEYWORDS.get("DEFAULT"));
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("네이버 응답 파싱 실패", e);
        }
    }

    private String stripHtml(String text) {
        if (text == null || text.isBlank()) return text;
        return text
                .replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&#39;", "'")
                .trim();
    }

    private LocalDateTime parseNaverDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return null;
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(pubDate, RFC_1123);
            return zdt.toLocalDateTime();
        } catch (Exception e) {
            log.debug("날짜 파싱 실패: {}", pubDate);
            return null;
        }
    }

    private String extractPublisher(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return "Unknown";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
