package com.solv.wefin.domain.game.kis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kis")
public class KisProperties {

    private String appKey;
    private String appSecret;
    private String baseUrl;
}
