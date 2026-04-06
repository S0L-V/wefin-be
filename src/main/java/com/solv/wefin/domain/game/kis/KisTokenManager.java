package com.solv.wefin.domain.game.kis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class KisTokenManager {

    private final RestClient kisRestClient;
    private final KisProperties kisProperties;
    private volatile String accessToken;
    private volatile OffsetDateTime tokenExpiresAt;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public KisTokenManager(@Qualifier("kisRestClient") RestClient kisRestClient,
                           KisProperties kisProperties) {
        this.kisRestClient = kisRestClient;
        this.kisProperties = kisProperties;
    }

    public synchronized String getAccessToken() {
        if (accessToken == null || tokenExpiresAt == null
                || tokenExpiresAt.isBefore(OffsetDateTime.now(KST).plusMinutes(5))) {
            fetchToken();
        }
        return accessToken;
    }

    private void fetchToken() {
        int maxRetries = 3;
        for (int tries = 1; tries <= maxRetries; tries++) {
            try {
                KisTokenResponse response = kisRestClient.post()
                        .uri("/oauth2/tokenP")
                        .body(java.util.Map.of(
                                "grant_type", "client_credentials",
                                "appkey", kisProperties.getAppKey(),
                                "appsecret", kisProperties.getAppSecret()
                        ))
                        .retrieve()
                        .body(KisTokenResponse.class);

                if (response == null || response.access_token() == null) {
                    throw new RuntimeException("KIS 토큰 응답이 비어있습니다");
                }

                this.accessToken = response.access_token();
                // KIS API는 만료 시각을 "yyyy-MM-dd HH:mm:ss" (KST) 형식으로 반환
                this.tokenExpiresAt = java.time.LocalDateTime.parse(
                        response.access_token_token_expired(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                ).atZone(KST).toOffsetDateTime();

                log.info("KIS 토큰 발급 완료 (만료: {})", tokenExpiresAt);
                return;
            } catch (Exception e) {
                log.error("KIS 토큰 발급 실패 ({}/{})", tries, maxRetries, e);
                if (tries == maxRetries) throw new RuntimeException("KIS 토큰 발급 " + maxRetries + "회 실패", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Scheduled(fixedRate = 1000 * 60 * 60 * 6)
    public synchronized void refreshToken() {
        try {
            fetchToken();
        } catch (Exception e) {
            log.error("KIS 토큰 정기 갱신 실패", e);
        }
    }
}
