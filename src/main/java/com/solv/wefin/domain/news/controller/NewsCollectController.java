package com.solv.wefin.domain.news.controller;

import com.solv.wefin.domain.news.service.NewsCollectService;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/admin/news")
@RequiredArgsConstructor
public class NewsCollectController {

    private final NewsCollectService newsCollectService;

    @PostMapping("/collect")
    public ApiResponse<String> collectNow() {
        newsCollectService.collectAll();
        return ApiResponse.success("뉴스 수집 완료");
    }
}
