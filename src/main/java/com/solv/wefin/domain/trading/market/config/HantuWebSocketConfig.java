package com.solv.wefin.domain.trading.market.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class HantuWebSocketConfig {

    @Value("${hantu.ws.url}")
    private String baseUrl;

    @Bean
    public WebSocketClient hantuWsClient() {
        return new StandardWebSocketClient();
    }
}
