package com.solv.wefin.domain.game.openai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private String apiKey;
    private String model = "gpt-4o-mini";
    private int maxTokens = 1500;
    private double temperature = 0.7;
}
