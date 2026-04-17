package com.solv.wefin.web.vote;

import com.solv.wefin.domain.vote.dto.info.VoteDetailInfo;
import com.solv.wefin.domain.vote.dto.info.VoteInfo;
import com.solv.wefin.domain.vote.dto.info.VoteOptionInfo;
import com.solv.wefin.domain.vote.dto.info.VoteOptionResultInfo;
import com.solv.wefin.domain.vote.dto.info.VoteResultInfo;
import com.solv.wefin.domain.vote.entity.VoteStatus;
import com.solv.wefin.domain.vote.service.VoteService;
import com.solv.wefin.global.config.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoteController.class)
class VoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VoteService voteService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void createVote_success() throws Exception {
        UUID userId = UUID.randomUUID();
        VoteInfo info = new VoteInfo(
                1L,
                "Snack vote",
                VoteStatus.OPEN,
                1,
                OffsetDateTime.parse("2026-04-17T20:00:00+09:00")
        );

        given(voteService.createVote(eq(userId), any())).willReturn(info);

        String requestBody = """
                {
                  \"groupId\": 1,
                  \"title\": \"Snack vote\",
                  \"options\": [\"Chicken\", \"Pizza\"],
                  \"maxSelectCount\": 1,
                  \"durationHours\": 1
                }
                """;

        mockMvc.perform(post("/api/votes")
                        .with(csrf())
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        ))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.voteId").value(1))
                .andExpect(jsonPath("$.data.title").value("Snack vote"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void getVoteDetail_success() throws Exception {
        UUID userId = UUID.randomUUID();
        VoteDetailInfo info = new VoteDetailInfo(
                2L,
                "Lunch vote",
                VoteStatus.OPEN,
                2,
                OffsetDateTime.parse("2026-04-17T22:00:00+09:00"),
                false,
                List.of(
                        new VoteOptionInfo(11L, "Chicken"),
                        new VoteOptionInfo(12L, "Pizza")
                ),
                List.of(11L)
        );

        given(voteService.getVoteDetail(userId, 2L)).willReturn(info);

        mockMvc.perform(get("/api/votes/2")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voteId").value(2))
                .andExpect(jsonPath("$.data.options[0].optionId").value(11))
                .andExpect(jsonPath("$.data.myOptionIds[0]").value(11));
    }

    @Test
    void submitVote_success() throws Exception {
        UUID userId = UUID.randomUUID();
        VoteResultInfo info = new VoteResultInfo(
                3L,
                "Dessert vote",
                VoteStatus.OPEN,
                1,
                OffsetDateTime.parse("2026-04-17T23:00:00+09:00"),
                false,
                2L,
                List.of(
                        new VoteOptionResultInfo(21L, "Cake", 2L, 100.0, true)
                )
        );

        given(voteService.submitVote(eq(userId), eq(3L), any())).willReturn(info);

        String requestBody = """
                {
                  \"optionIds\": [21]
                }
                """;

        mockMvc.perform(post("/api/votes/3/answers")
                        .with(csrf())
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        ))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voteId").value(3))
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.options[0].optionId").value(21))
                .andExpect(jsonPath("$.data.options[0].selectedByMe").value(true));
    }

    @Test
    void getVoteResult_success() throws Exception {
        UUID userId = UUID.randomUUID();
        VoteResultInfo info = new VoteResultInfo(
                4L,
                "Weekend plan vote",
                VoteStatus.CLOSED,
                2,
                OffsetDateTime.parse("2026-04-17T18:00:00+09:00"),
                true,
                3L,
                List.of(
                        new VoteOptionResultInfo(31L, "Hiking", 2L, 66.6, true),
                        new VoteOptionResultInfo(32L, "Movie", 1L, 33.3, false)
                )
        );

        given(voteService.getVoteResult(userId, 4L)).willReturn(info);

        mockMvc.perform(get("/api/votes/4/result")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closed").value(true))
                .andExpect(jsonPath("$.data.options[0].optionText").value("Hiking"))
                .andExpect(jsonPath("$.data.options[1].voteCount").value(1));
    }
}
