package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuWebSocketClient;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionManagerTest {

    @Mock
    private HantuWebSocketClient hantuWebSocketClient;

    @InjectMocks
    private SubscriptionManager subscriptionManager;

    @Test
    void 첫_구독시_한투에_구독_전송() {
        subscriptionManager.subscribe("005930");

        verify(hantuWebSocketClient).sendSubscribe("H0STCNT0", "005930");
        verify(hantuWebSocketClient).sendSubscribe("H0STASP0", "005930");
    }

    @Test
    void 같은_종목_중복_구독시_한투에_전송_안함() {
        subscriptionManager.subscribe("005930");
        subscriptionManager.subscribe("005930");

        verify(hantuWebSocketClient, times(1)).sendSubscribe("H0STCNT0", "005930");
        verify(hantuWebSocketClient, times(1)).sendSubscribe("H0STASP0", "005930");
    }

    @Test
    void 구독_제한_초과시_예외() {
        for (int i = 1; i <= 20; i++) {
            subscriptionManager.subscribe(String.format("%06d", i));
        }

        assertThatThrownBy(() -> subscriptionManager.subscribe("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_SUBSCRIPTION_LIMIT_EXCEEDED);
    }

    @Test
    void 이미_구독중인_종목은_제한에_안걸림() {
        for (int i = 1; i <= 20; i++) {
            subscriptionManager.subscribe(String.format("%06d", i));
        }

        assertThatCode(() -> subscriptionManager.subscribe("000001"))
                .doesNotThrowAnyException();
    }

    @Test
    void 마지막_구독자_해제시_한투에_해제_전송() {
        subscriptionManager.subscribe("005930");
        subscriptionManager.unsubscribe("005930");

        verify(hantuWebSocketClient).sendUnsubscribe("H0STCNT0", "005930");
        verify(hantuWebSocketClient).sendUnsubscribe("H0STASP0", "005930");
    }

    @Test
    void 구독자_남아있으면_한투에_해제_안함() {
        subscriptionManager.subscribe("005930");
        subscriptionManager.subscribe("005930");
        subscriptionManager.unsubscribe("005930");

        verify(hantuWebSocketClient, never()).sendUnsubscribe(any(), any());
    }

    @Test
    void 구독하지_않은_종목_해제시_무시() {
        subscriptionManager.unsubscribe("005930");

        verify(hantuWebSocketClient, never()).sendUnsubscribe(any(), any());
    }
}