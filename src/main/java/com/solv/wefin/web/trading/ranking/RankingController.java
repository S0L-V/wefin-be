package com.solv.wefin.web.trading.ranking;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.ranking.dto.DailyRankingInfo;
import com.solv.wefin.domain.trading.ranking.service.RankingService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.ranking.dto.DailyRankingResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankingController {

	private final RankingService rankingService;

	@GetMapping("/daily")
	public ApiResponse<DailyRankingResponse> getDailyRanking(@AuthenticationPrincipal UUID userId) {
		DailyRankingInfo info = rankingService.getDailyRanking(userId);
		return ApiResponse.success(DailyRankingResponse.from(info));
	}
}
