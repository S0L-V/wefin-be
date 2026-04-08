package com.solv.wefin.domain.game.openai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiRestClientConfig {

    @Bean("openAiRestClient")
    public RestClient openAiRestClient(OpenAiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
