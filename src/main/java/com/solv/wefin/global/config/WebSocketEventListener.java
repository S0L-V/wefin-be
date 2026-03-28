package com.solv.wefin.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
public class WebSocketEventListener {

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Object user = accessor.getUser();

        if (user instanceof Authentication auth) {
            log.info("WebSocket connected user={}", auth.getName());
        } else {
            log.info("WebSocket connected");
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Object user = accessor.getUser();

        if (user instanceof Authentication auth) {
            log.info("WebSocket disconnected user={}", auth.getName());
        } else {
            log.info("WebSocket disconnected");
        }
    }
}
