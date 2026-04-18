package com.solv.wefin.domain.trading.dart.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dart.api")
public class DartProperties {

    private String baseUrl;
    private String key;
}
