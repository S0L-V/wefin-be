package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.crawler.CrawledArticle;
import com.solv.wefin.domain.game.news.crawler.NaverNewsCrawler;
import com.solv.wefin.domain.game.news.entity.GameNewsArchive;
import com.solv.wefin.domain.game.news.repository.GameNewsArchiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCrawlService {

    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    private final NaverNewsCrawler naverNewsCrawler;
    private final GameNewsArchiveRepository newsArchiveRepository;

    /**
     * 특정 날짜의 뉴스를 크롤링하고 DB에 저장한다.
     * 이미 저장된 뉴스가 있으면 DB에서 조회하여 반환한다.
     *
     * @return 해당 날짜의 전체 뉴스 목록 (기존 + 신규)
     */
    public List<GameNewsArchive> crawlAndSave(LocalDate date) {
        // 1. 해당 날짜에 이미 저장된 뉴스 조회
        OffsetDateTime dayStart = date.atStartOfDay().atOffset(KST);
        OffsetDateTime dayEnd = date.atTime(23, 59, 59).atOffset(KST);
        List<GameNewsArchive> existing = newsArchiveRepository
                .findByPublishedAtBetweenOrderByPublishedAtDesc(dayStart, dayEnd);

        if (!existing.isEmpty()) {
            log.info("[뉴스 크롤링] 기존 데이터 사용: date={}, {}건", date, existing.size());
            return existing;
        }

        // 2. 크롤링 수행
        List<CrawledArticle> crawled = naverNewsCrawler.crawlBySectors(date);
        if (crawled.isEmpty()) {
            log.warn("[뉴스 크롤링] 수집된 뉴스 없음: date={}", date);
            return List.of();
        }

        // 3. 중복 URL 필터링
        List<String> urls = crawled.stream()
                .map(CrawledArticle::originalUrl)
                .toList();
        Set<String> existingUrls = newsArchiveRepository.findExistingUrls(urls);

        List<GameNewsArchive> newArticles = crawled.stream()
                .filter(article -> !existingUrls.contains(article.originalUrl()))
                .map(this::toEntity)
                .toList();

        // 4. 일괄 저장 (동시 요청 시 UNIQUE 위반 → 기존 데이터 반환)
        if (!newArticles.isEmpty()) {
            try {
                newsArchiveRepository.saveAll(newArticles);
                log.info("[뉴스 크롤링] 저장 완료: date={}, 신규 {}건 (중복 제외 {}건)",
                        date, newArticles.size(), crawled.size() - newArticles.size());
            } catch (DataIntegrityViolationException e) {
                log.info("[뉴스 크롤링] 동시 저장 감지, 기존 데이터 반환: date={}", date);
            }
        }

        // 저장 후 전체 조회하여 일관된 결과 반환
        return newsArchiveRepository
                .findByPublishedAtBetweenOrderByPublishedAtDesc(dayStart, dayEnd);
    }

    private GameNewsArchive toEntity(CrawledArticle article) {
        return GameNewsArchive.create(
                article.title(),
                article.summary(),
                article.source(),
                article.originalUrl(),
                article.publishedAt(),
                article.category(),
                article.keyword()
        );
    }
}
