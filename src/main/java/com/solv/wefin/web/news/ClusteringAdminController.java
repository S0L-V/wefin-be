package com.solv.wefin.web.news;

import com.solv.wefin.domain.news.cluster.batch.ClusteringScheduler;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/admin/news/clustering")
@RequiredArgsConstructor
public class ClusteringAdminController {

    private final ClusteringScheduler clusteringScheduler;

    /**
     * 클러스터링을 수동으로 트리거한다.
     */
    @PostMapping("/trigger")
    public ApiResponse<String> triggerClustering() {
        boolean executed = clusteringScheduler.execute();
        if (!executed) {
            throw new BusinessException(ErrorCode.CLUSTERING_ALREADY_RUNNING);
        }
        return ApiResponse.success("클러스터링 배치 실행 완료");
    }
}
