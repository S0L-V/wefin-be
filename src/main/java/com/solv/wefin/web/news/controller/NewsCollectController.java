package com.solv.wefin.web.news.controller;

import com.solv.wefin.domain.news.crawl.ArticleCrawlService;
import com.solv.wefin.domain.news.service.NewsCollectService;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/admin/news")
@RequiredArgsConstructor
public class NewsCollectController {

    private final NewsCollectService newsCollectService;
    private final ArticleCrawlService articleCrawlService;

    @PostMapping("/collect")
    public ApiResponse<String> collectNow() {
        newsCollectService.collectAll();
        return ApiResponse.success("뉴스 수집 완료");
    }

    @PostMapping("/crawl")
    public ApiResponse<String> crawlNow() {
        articleCrawlService.crawlPendingArticles();
        return ApiResponse.success("뉴스 크롤링 완료");
    }
}
