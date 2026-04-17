package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartCorpCodeClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartCorpCodeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DartCorpCodeLoaderTest {

    @Mock
    private DartCorpCodeClient dartCorpCodeClient;

    @Mock
    private DartCorpCodeTxService dartCorpCodeTxService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache dartCorpCodeCache;

    @InjectMocks
    private DartCorpCodeLoader dartCorpCodeLoader;

    @Test
    void refresh_정상흐름_fetch_upsert_cacheClear_순서() {
        // given
        List<DartCorpCodeItem> items = List.of(
                new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201"),
                new DartCorpCodeItem("035720", "00258801", "카카오", "20240329")
        );
        given(dartCorpCodeClient.fetchAll()).willReturn(items);
        given(dartCorpCodeTxService.upsertAll(items)).willReturn(2);
        given(cacheManager.getCache("dartCorpCode")).willReturn(dartCorpCodeCache);

        // when
        int result = dartCorpCodeLoader.refresh();

        // then
        assertThat(result).isEqualTo(2);
        InOrder inOrder = inOrder(dartCorpCodeClient, dartCorpCodeTxService, dartCorpCodeCache);
        inOrder.verify(dartCorpCodeClient).fetchAll();
        inOrder.verify(dartCorpCodeTxService).upsertAll(items);
        inOrder.verify(dartCorpCodeCache).clear();
    }

    @Test
    void refresh_캐시가_존재하지_않으면_clear_호출_안함() {
        // given
        List<DartCorpCodeItem> items = List.of(
                new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201")
        );
        given(dartCorpCodeClient.fetchAll()).willReturn(items);
        given(dartCorpCodeTxService.upsertAll(items)).willReturn(1);
        given(cacheManager.getCache("dartCorpCode")).willReturn(null);

        // when
        int result = dartCorpCodeLoader.refresh();

        // then
        assertThat(result).isEqualTo(1);
        verify(dartCorpCodeClient).fetchAll();
        verify(dartCorpCodeTxService).upsertAll(items);
    }
}
