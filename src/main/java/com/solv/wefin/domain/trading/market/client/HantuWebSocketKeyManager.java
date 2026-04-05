package com.solv.wefin.domain.trading.market.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class HantuWebSocketKeyManager {

    @Value("${hantu.api.appkey}")
    private String appKey;

    @Value("${hantu.api.appsecret}")
    private String appSecret;

    private final RestClient hantuRestClient;
    private String approvalKey;

    public String getApprovalKey() {
        if (approvalKey == null) {
            fetchApprovalKey();
        }

        return approvalKey;
    }

    private void fetchApprovalKey() {
        for (int tries = 1; tries <= 3; tries++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = hantuRestClient.post()
                        .uri("/oauth2/Approval")
                        .body(Map.of("grant_type", "client_credentials",
                                "appkey", appKey, "secretkey", appSecret))
                        .retrieve()
                        .body(Map.class);
                if (response == null || response.get("approval_key") == null) {
                    throw new RuntimeException("한투 웹소켓 접속키 응답이 비어있습니다.");
                }
                this.approvalKey = (String) response.get("approval_key");

                return;
            } catch (Exception e) {
                log.error("한투 웹소켓 접속키 발급 실패 ({}/3)", tries, e);
                if (tries == 3) throw e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
