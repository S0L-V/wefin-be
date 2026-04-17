package com.solv.wefin.domain.trading.market.client;

import com.solv.wefin.domain.trading.market.dto.HantuTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class HantuTokenManager {

    @Value("${hantu.api.appkey}")
    private String appKey;

    @Value("${hantu.api.appsecret}")
    private String appSecret;

    private final RestClient hantuRestClient;
    private volatile String accessToken;
    private volatile LocalDateTime tokenExpiresAt;

    /**
     * 토큰을 반환합니다. (만료됐거나 만료 5분 이내이면 자동 갱신)
     * @return 액세스 토큰 문자열
     */
    public synchronized String getAccessToken() {
        if (accessToken == null || tokenExpiresAt == null || tokenExpiresAt.isBefore(LocalDateTime.now().plusMinutes(5))) {
            fetchToken();
        }

        return accessToken;
    }

    /**
     * 한투 API에 POST 요청으로 토큰을 발급합니다.
     * 실패 시 최대 3회 재시도.
     */
    private void fetchToken() {
        HantuTokenResponse response;

        for (int tries = 1; tries <= 3; tries++) {
            try {
                response = hantuRestClient.post()
                        .uri("/oauth2/tokenP")
                        .body(Map.of("grant_type", "client_credentials", "appkey", appKey, "appsecret",
                                appSecret))
                        .retrieve()
                        .body(HantuTokenResponse.class);

                this.accessToken = response.access_token();
                this.tokenExpiresAt = LocalDateTime.parse(response.access_token_token_expired(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return;
            } catch (Exception e) {
                log.error("한투 토큰 발급 실패 ({}/3)", tries, e);
                if (tries == 3) throw e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 6시간 주기로 토큰을 갱신합니다.
     */
    @Scheduled(fixedRate = 1000 * 60 * 60 * 6)
    public synchronized void refreshToken() {
        try {
            fetchToken();
        } catch (Exception e) {
            log.error("한투 토큰 정기 갱신 실패", e);
        }
    }
}
