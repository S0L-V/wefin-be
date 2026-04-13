package com.solv.wefin.web.trading.ranking;

import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solv.wefin.domain.trading.ranking.dto.DailyRankingInfo;
import com.solv.wefin.domain.trading.ranking.service.RankingService;
import com.solv.wefin.global.config.security.JwtProvider;

@WebMvcTest(RankingController.class)
class RankingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RankingService rankingService;
	@MockitoBean
	private JwtProvider jwtProvider;

	@Test
	void 인증_없이_조회() throws Exception {
		// given
		DailyRankingInfo info = new DailyRankingInfo(
			List.of(new DailyRankingInfo.RankingItemInfo(1, "user1", new BigDecimal("5000"), 3)),
			null
		);
		given(rankingService.getDailyRanking(null)).willReturn(info);

		// when & then
		mockMvc.perform(get("/api/ranking/daily")
				.with(authentication(
					new UsernamePasswordAuthenticationToken(null, null, List.of())
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.rankings").isArray())
			.andExpect(jsonPath("$.data.rankings[0].rank").value(1))
			.andExpect(jsonPath("$.data.rankings[0].nickname").value("user1"))
			.andExpect(jsonPath("$.data.myRank").value(nullValue()));
	}

	@Test
	void 인증_포함_조회() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		DailyRankingInfo info = new DailyRankingInfo(
			List.of(new DailyRankingInfo.RankingItemInfo(1, "user1", new BigDecimal("5000"), 3)),
			new DailyRankingInfo.MyRankInfo(3, new BigDecimal("2000"))
		);
		given(rankingService.getDailyRanking(userId)).willReturn(info);

		// when & then
		mockMvc.perform(get("/api/ranking/daily")
				.with(authentication(
					new UsernamePasswordAuthenticationToken(userId, null, List.of())
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.rankings[0].rank").value(1))
			.andExpect(jsonPath("$.data.myRank.rank").value(3))
			.andExpect(jsonPath("$.data.myRank.realizedProfit").value(2000));
	}
}