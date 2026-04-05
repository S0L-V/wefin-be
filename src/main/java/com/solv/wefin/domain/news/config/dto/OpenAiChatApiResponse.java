package com.solv.wefin.domain.news.config.dto;

import lombok.Getter;

import java.util.List;

/**
 * OpenAI Chat Completions API 공통 응답 DTO
 */
@Getter
public class OpenAiChatApiResponse {
    private List<Choice> choices; //  생성된 응답 후보 목록 (현재는 하나의 응답만 사용)

    @Getter
    public static class Choice {
        private Message message; // 하나의 응답 단위
    }

    @Getter
    public static class Message {
        private String content; // 실제 생성된 텍스트
    }
}
