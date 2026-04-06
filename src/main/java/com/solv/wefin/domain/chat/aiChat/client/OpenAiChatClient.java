package com.solv.wefin.domain.chat.aiChat.client;

import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage.AiChatRole;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiChatClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final String chatUrl;

    private static final String SYSTEM_PROMPT = """
            너는 친절한 투자 도우미다.
            사용자의 질문에 대해 이해하기 쉽게 설명하되, 확정적인 투자 권유는 피하고
            불확실성과 주의사항을 함께 알려줘.
            """;


    public OpenAiChatClient(
            @Qualifier("chatRestTemplate") RestTemplate restTemplate,
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.chat.model}") String model,
            @Value("${openai.chat.url}") String chatUrl
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.chatUrl = chatUrl;
    }

    public String ask(List<AiChatMessage> history) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", toOpenAiMessages(history)
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ChatResponse response = restTemplate.postForObject(
                    chatUrl,
                    request,
                    ChatResponse.class
            );

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new BusinessException(ErrorCode.AI_CHAT_REQUEST_FAILED);
            }

            Message message = response.getChoices().get(0).getMessage();
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                throw new BusinessException(ErrorCode.AI_CHAT_REQUEST_FAILED);
            }

            return message.getContent();
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AI_CHAT_TIMEOUT);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_CHAT_REQUEST_FAILED);
        }
    }

    private List<Map<String, String>> toOpenAiMessages(List<AiChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", SYSTEM_PROMPT
        ));

        history.stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreatedAt))
                .forEach(message -> messages.add(Map.of(
                        "role", toOpenAiRole(message.getRole()),
                        "content", message.getContent()
                )));

        return messages;
    }

    private String toOpenAiRole(AiChatMessage.AiChatRole role) {
        return role == AiChatRole.USER ? "user" : "assistant";
    }

    @Getter
    private static class ChatResponse {
        private List<Choice> choices;
    }

    @Getter
    private static class Choice {
        private Message message;
    }

    @Getter
    private static class Message {
        private String content;
    }
}
