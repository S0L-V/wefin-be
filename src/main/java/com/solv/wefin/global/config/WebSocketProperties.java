package com.solv.wefin.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "websocket")
public class WebSocketProperties {

    private List<String> allowedOrigins = new ArrayList<>();
}
