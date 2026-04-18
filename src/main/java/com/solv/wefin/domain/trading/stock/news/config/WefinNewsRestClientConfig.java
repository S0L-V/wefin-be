package com.solv.wefin.domain.trading.stock.news.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(WefinNewsProperties.class)
@RequiredArgsConstructor
public class WefinNewsRestClientConfig {

    private final WefinNewsProperties wefinNewsProperties;

    @Bean("wefinNewsRestClient")
    public RestClient wefinNewsRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(wefinNewsProperties.getBaseUrl())
                .build();
    }
}
