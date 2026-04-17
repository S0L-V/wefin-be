package com.solv.wefin.web.interest;

import com.solv.wefin.domain.interest.service.InterestService;
import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.interest.dto.InterestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 관심사 API (SECTOR / TOPIC)
 *
 * STOCK은 {@code /api/watchlist}에서 이미 제공되며, 본 컨트롤러는 동일 저장소를 공유하되
 * 경로를 분리해 리소스 구조를 Watchlist와 일관되게 유지한다
 */
@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;

    @GetMapping("/sectors")
    public ApiResponse<List<InterestResponse>> getSectors(@AuthenticationPrincipal UUID userId) {
        List<InterestResponse> body = interestService.list(userId, InterestType.SECTOR).stream()
                .map(InterestResponse::from)
                .toList();
        return ApiResponse.success(body);
    }

    @PostMapping("/sectors/{code}")
    public ApiResponse<Void> addSector(@AuthenticationPrincipal UUID userId, @PathVariable String code) {
        interestService.add(userId, InterestType.SECTOR, code);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/sectors/{code}")
    public ApiResponse<Void> deleteSector(@AuthenticationPrincipal UUID userId, @PathVariable String code) {
        interestService.delete(userId, InterestType.SECTOR, code);
        return ApiResponse.success(null);
    }

    @GetMapping("/topics")
    public ApiResponse<List<InterestResponse>> getTopics(@AuthenticationPrincipal UUID userId) {
        List<InterestResponse> body = interestService.list(userId, InterestType.TOPIC).stream()
                .map(InterestResponse::from)
                .toList();
        return ApiResponse.success(body);
    }

    @PostMapping("/topics/{code}")
    public ApiResponse<Void> addTopic(@AuthenticationPrincipal UUID userId, @PathVariable String code) {
        interestService.add(userId, InterestType.TOPIC, code);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/topics/{code}")
    public ApiResponse<Void> deleteTopic(@AuthenticationPrincipal UUID userId, @PathVariable String code) {
        interestService.delete(userId, InterestType.TOPIC, code);
        return ApiResponse.success(null);
    }
}
