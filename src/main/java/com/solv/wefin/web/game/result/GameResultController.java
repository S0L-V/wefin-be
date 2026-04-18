package com.solv.wefin.web.game.result;

import com.solv.wefin.domain.game.order.dto.OrderHistoryInfo;
import com.solv.wefin.domain.game.result.dto.AnalysisReportInfo;
import com.solv.wefin.domain.game.result.dto.GameEndInfo;
import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import com.solv.wefin.domain.game.result.service.GameAnalysisReportService;
import com.solv.wefin.domain.game.result.service.GameEndService;
import com.solv.wefin.domain.game.result.service.GameResultService;
import com.solv.wefin.domain.game.snapshot.dto.SnapshotInfo;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.result.dto.response.AnalysisReportResponse;
import com.solv.wefin.web.game.result.dto.response.GameEndResponse;
import com.solv.wefin.web.game.result.dto.response.GameResultResponse;
import com.solv.wefin.web.game.result.dto.response.OrderHistoryResponse;
import com.solv.wefin.web.game.result.dto.response.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class GameResultController {

    private final GameEndService gameEndService;
    private final GameResultService gameResultService;
    private final GameAnalysisReportService analysisReportService;

    @PostMapping("/end")
    public ResponseEntity<ApiResponse<GameEndResponse>> endGame(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        GameEndInfo info = gameEndService.endGame(roomId, userId);
        GameEndResponse response = GameEndResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/result")
    public ResponseEntity<ApiResponse<GameResultResponse>> getGameResult(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        GameResultInfo info = gameResultService.getGameResult(roomId, userId);
        GameResultResponse response = GameResultResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<ApiResponse<List<SnapshotResponse>>> getSnapshots(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        List<SnapshotInfo> infos = gameResultService.getSnapshots(roomId, userId);
        List<SnapshotResponse> response = infos.stream()
                .map(SnapshotResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderHistoryResponse>>> getOrderHistory(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        List<OrderHistoryInfo> infos = gameResultService.getOrderHistory(roomId, userId);
        List<OrderHistoryResponse> response = infos.stream()
                .map(OrderHistoryResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<AnalysisReportResponse>> getAnalysisReport(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        AnalysisReportInfo info = analysisReportService.getOrGenerateReport(roomId, userId);
        AnalysisReportResponse response = AnalysisReportResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
