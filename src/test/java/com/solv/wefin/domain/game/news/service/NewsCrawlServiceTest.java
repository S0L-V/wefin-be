package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.crawler.CrawledArticle;
import com.solv.wefin.domain.game.news.crawler.NaverNewsCrawler;
import com.solv.wefin.domain.game.news.entity.GameNewsArchive;
import com.solv.wefin.domain.game.news.repository.GameNewsArchiveRepository;
import com.solv.wefin.domain.game.news.service.NewsCrawlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NewsCrawlServiceTest {

    @InjectMocks
    private NewsCrawlService newsCrawlService;

    @Mock
    private NaverNewsCrawler naverNewsCrawler;

    @Mock
    private GameNewsArchiveRepository newsArchiveRepository;

    private static final LocalDate TEST_DATE = LocalDate.of(2020, 3, 15);
    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    // === 크롤링 + 저장 테스트 ===

    @Test
    @DisplayName("크롤링 + 저장 성공 — 신규 뉴스를 크롤링하고 DB에 저장한다")
    void crawlAndSave_success_newArticles() {
        // Given — 해당 날짜에 기존 뉴스 없음, 크롤링 결과 2건, 중복 URL 없음
        OffsetDateTime dayStart = TEST_DATE.atStartOfDay().atOffset(KST);
        OffsetDateTime dayEnd = TEST_DATE.atTime(23, 59, 59).atOffset(KST);

        given(newsArchiveRepository.findByPublishedAtBetweenOrderByPublishedAtDesc(dayStart, dayEnd))
                .willReturn(Collections.emptyList())   // 첫 조회: 기존 없음
                .willReturn(List.of(createNewsArchive("뉴스1"), createNewsArchive("뉴스2")));  // 저장 후 재조회

        List<CrawledArticle> crawled = List.of(
                createCrawledArticle("뉴스1", "https://news.com/1"),
                createCrawledArticle("뉴스2", "https://news.com/2")
        );
        given(naverNewsCrawler.crawlBySectors(TEST_DATE)).willReturn(crawled);
        given(newsArchiveRepository.findExistingUrls(anyList())).willReturn(Set.of());

        // When
        List<GameNewsArchive> result = newsCrawlService.crawlAndSave(TEST_DATE);

        // Then — 크롤링 수행, 저장 호출, 결과 2건
        assertThat(result).hasSize(2);
        verify(naverNewsCrawler).crawlBySectors(TEST_DATE);
        verify(newsArchiveRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("기존 뉴스 존재 시 크롤링 스킵 — DB에서 기존 데이터를 반환한다")
    void crawlAndSave_existingData_skipsCrawling() {
        // Given — 해당 날짜에 이미 뉴스 3건 존재
        OffsetDateTime dayStart = TEST_DATE.atStartOfDay().atOffset(KST);
        OffsetDateTime dayEnd = TEST_DATE.atTime(23, 59, 59).atOffset(KST);

        List<GameNewsArchive> existingNews = List.of(
                createNewsArchive("기존뉴스1"),
                createNewsArchive("기존뉴스2"),
                createNewsArchive("기존뉴스3")
        );
        given(newsArchiveRepository.findByPublishedAtBetweenOrderByPublishedAtDesc(dayStart, dayEnd))
                .willReturn(existingNews);

        // When
        List<GameNewsArchive> result = newsCrawlService.crawlAndSave(TEST_DATE);

        // Then — 크롤링 호출 없이 기존 데이터 3건 반환
        assertThat(result).hasSize(3);
        verify(naverNewsCrawler, never()).crawlBySectors(any());
        verify(newsArchiveRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("중복 URL 필터링 — 이미 존재하는 URL은 제외하고 신규만 저장한다")
    void crawlAndSave_duplicateUrlFiltering() {
        // Given — 크롤링 3건 중 1건은 이미 DB에 존재
        OffsetDateTime dayStart = TEST_DATE.atStartOfDay().atOffset(KST);
        OffsetDateTime dayEnd = TEST_DATE.atTime(23, 59, 59).atOffset(KST);

        given(newsArchiveRepository.findByPublishedAtBetweenOrderByPublishedAtDesc(dayStart, dayEnd))
                .willReturn(Collections.emptyList())
                .willReturn(List.of(createNewsArchive("뉴스1"), createNewsArchive("뉴스2"), createNewsArchive("뉴스3")));

        List<CrawledArticle> crawled = List.of(
                createCrawledArticle("뉴스1", "https://news.com/1"),
                createCrawledArticle("뉴스2", "https://news.com/2"),
                createCrawledArticle("뉴스3", "https://news.com/3")
        );
        given(naverNewsCrawler.crawlBySectors(TEST_DATE)).willReturn(crawled);
        // URL 1건은 이미 존재
        given(newsArchiveRepository.findExistingUrls(anyList()))
                .willReturn(Set.of("https://news.com/1"));

        // When
        List<GameNewsArchive> result = newsCrawlService.crawlAndSave(TEST_DATE);

        // Then — 저장은 호출되고 (신규 2건만 필터링됨), 전체 재조회 결과 반환
        assertThat(result).hasSize(3);
        verify(newsArchiveRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("크롤링 결과 빈 리스트 — 수집된 뉴스가 없으면 빈 리스트 반환")
    void crawlAndSave_emptyCrawlResult() {
        // Given — 기존 뉴스 없음, 크롤링 결과도 없음
        OffsetDateTime dayStart = TEST_DATE.atStartOfDay().atOffset(KST);
        OffsetDateTime dayEnd = TEST_DATE.atTime(23, 59, 59).atOffset(KST);

        given(newsArchiveRepository.findByPublishedAtBetweenOrderByPublishedAtDesc(dayStart, dayEnd))
                .willReturn(Collections.emptyList());
        given(naverNewsCrawler.crawlBySectors(TEST_DATE)).willReturn(Collections.emptyList());

        // When
        List<GameNewsArchive> result = newsCrawlService.crawlAndSave(TEST_DATE);

        // Then — 빈 리스트 반환, saveAll 호출 안 됨
        assertThat(result).isEmpty();
        verify(newsArchiveRepository, never()).saveAll(anyList());
        verify(newsArchiveRepository, never()).findExistingUrls(anyList());
    }

    @Test
    @DisplayName("동시 저장 시 DataIntegrityViolationException — 기존 데이터 반환")
    void crawlAndSave_concurrentSave_returnsExistingData() {
        // Given — 크롤링 성공했지만 saveAll에서 UNIQUE 위반 발생
        OffsetDateTime dayStart = TEST_DATE.atStartOfDay().atOffset(KST);
        OffsetDateTime dayEnd = TEST_DATE.atTime(23, 59, 59).atOffset(KST);

        given(newsArchiveRepository.findByPublishedAtBetweenOrderByPublishedAtDesc(dayStart, dayEnd))
                .willReturn(Collections.emptyList())   // 첫 조회: 없음
                .willReturn(List.of(createNewsArchive("동시저장뉴스")));  // 예외 후 재조회

        List<CrawledArticle> crawled = List.of(
                createCrawledArticle("동시저장뉴스", "https://news.com/concurrent")
        );
        given(naverNewsCrawler.crawlBySectors(TEST_DATE)).willReturn(crawled);
        given(newsArchiveRepository.findExistingUrls(anyList())).willReturn(Set.of());
        given(newsArchiveRepository.saveAll(anyList()))
                .willThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // When — 동시 저장 예외가 발생해도 정상 흐름으로 복구
        List<GameNewsArchive> result = newsCrawlService.crawlAndSave(TEST_DATE);

        // Then — 예외 후 재조회하여 기존 데이터 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("동시저장뉴스");
    }

    // === 헬퍼 메서드 ===

    private CrawledArticle createCrawledArticle(String title, String url) {
        return new CrawledArticle(
                title,
                title + " 요약",
                "naver_finance",
                url,
                TEST_DATE.atTime(9, 0).atOffset(KST),
                "반도체",
                "반도체"
        );
    }

    private GameNewsArchive createNewsArchive(String title) {
        return GameNewsArchive.create(
                title,
                title + " 요약",
                "naver_finance",
                "https://news.com/" + title.hashCode(),
                TEST_DATE.atTime(9, 0).atOffset(KST),
                "반도체",
                "반도체"
        );
    }
}
