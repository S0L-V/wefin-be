package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.dto.DartCorpCodeItem;
import com.solv.wefin.domain.trading.dart.entity.DartCorpCode;
import com.solv.wefin.domain.trading.dart.repository.DartCorpCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DartCorpCodeTxServiceTest {

    @Mock
    private DartCorpCodeRepository dartCorpCodeRepository;

    @InjectMocks
    private DartCorpCodeTxService dartCorpCodeTxService;

    @Test
    void 빈_리스트_upsert시_0건_반환_및_empty_saveAll() {
        // given
        given(dartCorpCodeRepository.findAll()).willReturn(List.of());

        // when
        int result = dartCorpCodeTxService.upsertAll(List.of());

        // then
        assertThat(result).isEqualTo(0);
        ArgumentCaptor<List<DartCorpCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(dartCorpCodeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void 신규_아이템만_있으면_모두_insert_대상() {
        // given
        given(dartCorpCodeRepository.findAll()).willReturn(List.of());
        List<DartCorpCodeItem> items = List.of(
                new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201"),
                new DartCorpCodeItem("035720", "00258801", "카카오", "20240329")
        );

        // when
        int result = dartCorpCodeTxService.upsertAll(items);

        // then
        assertThat(result).isEqualTo(2);
        ArgumentCaptor<List<DartCorpCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(dartCorpCodeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
                .extracting(DartCorpCode::getStockCode)
                .containsExactlyInAnyOrder("005930", "035720");
    }

    @Test
    void 기존_아이템은_엔티티_update_호출_saveAll에_포함되지않음() {
        // given
        DartCorpCode existing = new DartCorpCode("005930", "OLD_CODE", "OLD_NAME", "19000101");
        given(dartCorpCodeRepository.findAll()).willReturn(List.of(existing));

        List<DartCorpCodeItem> items = List.of(
                new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201")
        );

        // when
        int result = dartCorpCodeTxService.upsertAll(items);

        // then
        assertThat(result).isEqualTo(1);
        assertThat(existing.getCorpCode()).isEqualTo("00126380");
        assertThat(existing.getCorpName()).isEqualTo("삼성전자");
        assertThat(existing.getModifyDate()).isEqualTo("20251201");

        ArgumentCaptor<List<DartCorpCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(dartCorpCodeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void 혼합_케이스_기존은_update_신규는_saveAll() {
        // given
        DartCorpCode existing = new DartCorpCode("005930", "OLD_CODE", "OLD_NAME", "19000101");
        given(dartCorpCodeRepository.findAll()).willReturn(List.of(existing));

        List<DartCorpCodeItem> items = List.of(
                new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201"),
                new DartCorpCodeItem("035720", "00258801", "카카오", "20240329")
        );

        // when
        int result = dartCorpCodeTxService.upsertAll(items);

        // then
        assertThat(result).isEqualTo(2);
        assertThat(existing.getCorpCode()).isEqualTo("00126380");

        ArgumentCaptor<List<DartCorpCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(dartCorpCodeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getStockCode()).isEqualTo("035720");
    }

    @Test
    void findAll_1회만_호출되어_N플러스1_없음() {
        // given
        given(dartCorpCodeRepository.findAll()).willReturn(List.of());
        List<DartCorpCodeItem> items = List.of(
                new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201"),
                new DartCorpCodeItem("035720", "00258801", "카카오", "20240329"),
                new DartCorpCodeItem("000660", "00164742", "SK하이닉스", "20240110")
        );

        // when
        dartCorpCodeTxService.upsertAll(items);

        // then
        verify(dartCorpCodeRepository).findAll();
        verify(dartCorpCodeRepository, never()).findByStockCode(org.mockito.ArgumentMatchers.anyString());
    }
}
