package com.solv.wefin.domain.game.batch;

import com.solv.wefin.domain.game.batch.entity.BatchStatus;
import com.solv.wefin.domain.game.batch.entity.BatchType;
import com.solv.wefin.domain.game.batch.repository.BatchProgressRepository;
import com.solv.wefin.domain.game.batch.service.StockCollectService;
import com.solv.wefin.domain.game.batch.service.StockInitService;
import com.solv.wefin.global.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/admin/batch")
@RequiredArgsConstructor
@Profile({"local", "dev"})
public class BatchAdminController {

    private final StockInitService stockInitService;
    private final StockCollectService stockCollectService;
    private final BatchProgressRepository batchProgressRepository;

    /**
     * POST /api/admin/batch/init
     * CSV에서 종목 목록을 읽어 stock_info + batch_progress를 초기화한다.
     */
    @PostMapping("/init")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> init() {
        Map<String, Integer> result = stockInitService.initFromCsv();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * POST /api/admin/batch/collect?size=320
     * 수동으로 수집을 트리거한다 (비동기). 즉시 응답 반환, 백그라운드에서 수집 진행.
     * 진행 상태는 GET /status로 확인.
     */
    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<Map<String, Object>>> collect(
            @RequestParam(defaultValue = "320") @Min(1) @Max(500) int size) {
        stockCollectService.collectBatchAsync(size);
        return ResponseEntity.accepted().body(ApiResponse.success(Map.of(
                "message", "수집이 시작되었습니다. GET /api/admin/batch/status로 진행 상태를 확인하세요.",
                "batchSize", size
        )));
    }

    /**
     * GET /api/admin/batch/status
     * 수집 진행 상태를 조회한다. GROUP BY로 한 번에 조회.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Long>>> status() {
        List<Object[]> rows = batchProgressRepository.countGroupByStatus(BatchType.DAILY);

        Map<String, Long> result = new HashMap<>();
        long total = 0;

        for (Object[] row : rows) {
            BatchStatus batchStatus = (BatchStatus) row[0];
            Long count = (Long) row[1];
            result.put(batchStatus.name().toLowerCase(), count);
            total += count;
        }

        // 누락된 상태는 0으로 채우기
        for (BatchStatus bs : BatchStatus.values()) {
            result.putIfAbsent(bs.name().toLowerCase(), 0L);
        }
        result.put("total", total);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
