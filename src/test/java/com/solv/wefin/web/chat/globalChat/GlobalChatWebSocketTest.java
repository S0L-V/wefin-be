package com.solv.wefin.web.chat.globalChat;

import com.solv.wefin.common.WebSocketIntegrationTestBase;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalChatWebSocketTest extends WebSocketIntegrationTestBase {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("SockJS 기반 전체 채팅 송수신 테스트")
    void globalChat_sockJs_sendAndReceive() throws Exception {

        // SockJS + WebSocket 기반 STOMP 클라이언트 생성
        List<Transport> transports = List.of(
                new WebSocketTransport(new StandardWebSocketClient())
        );
        SockJsClient sockJsClient = new SockJsClient(transports);

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // 서버에서 오는 메시지를 담을 큐
        BlockingQueue<GlobalChatMessageResponse> queue = new LinkedBlockingQueue<>();

        // CONNECT 시 nickname 해더 전달 (서버에서 Principal로 사용됨)
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("nickname", "testUser");

        // WebSocket 연결
        StompSession session = stompClient
                .connectAsync(
                        "http://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {

                            // STOMP 처리 중 예외 발생 시 로그 출력
                            @Override
                            public void handleException(StompSession session, StompCommand command,
                                                        StompHeaders headers, byte[] payload, Throwable exception) {
                                exception.printStackTrace();
                            }

                            // 연결 자체 문제 발생 시 로그 출력
                            @Override
                            public void handleTransportError(StompSession session, Throwable exception) {
                                exception.printStackTrace();
                            }
                        }
                )
                .get(5, TimeUnit.SECONDS);

        // 채팅 메시지 구독
        session.subscribe("/topic/chat/global", new StompFrameHandler() {

            // 서버에서 받은 메시지를 어떤 타입으로 변환할지 지정
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GlobalChatMessageResponse.class;
            }

            // 메시지 수신 시 실행되는 콜백
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("받은 payload = " + payload);

                // 큐에 메시지 저장 (offer 반환값은 항상 true라 무시 가능)
                queue.offer((GlobalChatMessageResponse) payload);
            }
        });

        // 구독 완료 대기
        Thread.sleep(300);

        // 메시지 전송 설정
        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.setDestination("/app/chat/global/send");
        sendHeaders.setContentType(MediaType.APPLICATION_JSON);

        // JSON payload 생성
        Map<String, String> payload = Map.of("content", "테스트 메시지");

        // 메시지 전송
        session.send(sendHeaders, payload);

        // 메시지 수신 대기
        GlobalChatMessageResponse response = queue.poll(5, TimeUnit.SECONDS);

        // 결과 검증
        assertNotNull(response, "구독 메시지를 받지 못했습니다.");
        assertEquals("testUser", response.getSender());
        assertEquals("테스트 메시지", response.getContent());
    }
}