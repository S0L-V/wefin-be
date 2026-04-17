package com.solv.wefin.domain.trading.stock.news.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "wefin-news.api")
public class WefinNewsProperties {

    private String baseUrl;
}
