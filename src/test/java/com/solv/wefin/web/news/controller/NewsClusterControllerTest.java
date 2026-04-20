package com.solv.wefin.web.news.controller;

import com.solv.wefin.domain.news.cluster.service.ClusterInteractionService;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import com.solv.wefin.domain.news.config.NewsHotProperties;
import com.solv.wefin.global.config.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsClusterController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NewsClusterControllerTest.TestConfig.class)
@TestPropertySource(properties = {
        "news.hot.window-hours=3",
        "news.hot.aggregation-interval-seconds=300",
        "news.hot.initial-delay-seconds=30",
        "news.hot.max-size=20",
        "news.hot.mark-read-throttle-seconds=60"
})
class NewsClusterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsClusterQueryService newsClusterQueryService;

    @MockitoBean
    private ClusterInteractionService clusterInteractionService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @org.springframework.boot.context.properties.EnableConfigurationProperties(NewsHotProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("sort=view — size 가 max-size 초과면 INVALID_INPUT")
    void sortView_sizeExceedsMax_returns400() throws Exception {
        mockMvc.perform(get("/api/news/clusters").param("sort", "view").param("size", "21"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sort=view — size 미지정 시 maxSize 이하 기본값으로 조회")
    void sortView_noSize_usesDefaultClamped() throws Exception {
        given(newsClusterQueryService.getFeed(any(), any(), anyInt(), any(), any(), eq("view"), any(), any()))
                .willReturn(new ClusterFeedResult(List.of(), false, null, null, OffsetDateTime.now()));

        mockMvc.perform(get("/api/news/clusters").param("sort", "view"))
                .andExpect(status().isOk());

        // DEFAULT_PAGE_SIZE=10, maxSize=20 → 10이 전달되어야 함
        verify(newsClusterQueryService).getFeed(any(), any(), eq(10), any(), any(), eq("view"), any(), any());
    }

    @Test
    @DisplayName("sort=view — cursor 파라미터 수신해도 서비스엔 null 전달 (cursor 무시)")
    void sortView_ignoresCursorParam() throws Exception {
        given(newsClusterQueryService.getFeed(any(), any(), anyInt(), any(), any(), eq("view"), any(), any()))
                .willReturn(new ClusterFeedResult(List.of(), false, null, null, null));

        mockMvc.perform(get("/api/news/clusters")
                        .param("sort", "view")
                        .param("cursor", "1700000000000_123")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(newsClusterQueryService).getFeed(eq(null), eq(null), eq(5), any(), any(), eq("view"), any(), any());
    }

    @Test
    @DisplayName("sort 파라미터가 허용값 아니면 에러")
    void sort_unsupported_returns400() throws Exception {
        mockMvc.perform(get("/api/news/clusters").param("sort", "unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sort=view 응답 — lastAggregatedAt 포함 (null 아닐 때)")
    void sortView_responseIncludesLastAggregatedAt() throws Exception {
        OffsetDateTime aggTime = OffsetDateTime.parse("2026-04-20T10:00:00Z");
        given(newsClusterQueryService.getFeed(any(), any(), anyInt(), any(), any(), eq("view"), any(), any()))
                .willReturn(new ClusterFeedResult(List.of(), false, null, null, aggTime));

        mockMvc.perform(get("/api/news/clusters").param("sort", "view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastAggregatedAt").exists())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("sort=publishedAt 응답 — lastAggregatedAt 은 null 이므로 응답에서 생략")
    void sortPublished_omitsLastAggregatedAt() throws Exception {
        given(newsClusterQueryService.getFeed(any(), any(), anyInt(), any(), any(), eq("publishedAt"), any(), any()))
                .willReturn(new ClusterFeedResult(List.of(), false, null, null, null));

        mockMvc.perform(get("/api/news/clusters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastAggregatedAt").doesNotExist());
    }
}
