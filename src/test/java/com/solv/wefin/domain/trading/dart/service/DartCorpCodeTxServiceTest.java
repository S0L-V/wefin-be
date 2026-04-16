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
    void payload_내부에_stockCode가_중복되면_첫번째는_insert_이후는_update_분기() {
        // given — DB는 비어있고, payload에 같은 005930이 2번 + 다른 값
        given(dartCorpCodeRepository.findAll()).willReturn(List.of());
        List<DartCorpCodeItem> items = List.of(
                new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201"),
                new DartCorpCodeItem("005930", "99999999", "DUPLICATE", "29991231")
        );

        // when
        int result = dartCorpCodeTxService.upsertAll(items);

        // then — saveAll에는 1건만 (UNIQUE 충돌 방지)
        assertThat(result).isEqualTo(2);
        ArgumentCaptor<List<DartCorpCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(dartCorpCodeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        // 두 번째 등장한 item의 값으로 update됨 (insert된 엔티티에 반영)
        assertThat(captor.getValue().get(0).getCorpCode()).isEqualTo("99999999");
        assertThat(captor.getValue().get(0).getCorpName()).isEqualTo("DUPLICATE");
    }

    @Test
    void 빈_stockCode_아이템은_무시() {
        // given
        given(dartCorpCodeRepository.findAll()).willReturn(List.of());
        List<DartCorpCodeItem> items = new java.util.ArrayList<>();
        items.add(new DartCorpCodeItem(null, "X", "X", "X"));
        items.add(new DartCorpCodeItem("", "X", "X", "X"));
        items.add(new DartCorpCodeItem("  ", "X", "X", "X"));
        items.add(new DartCorpCodeItem("005930", "00126380", "삼성전자", "20251201"));

        // when
        dartCorpCodeTxService.upsertAll(items);

        // then
        ArgumentCaptor<List<DartCorpCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(dartCorpCodeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getStockCode()).isEqualTo("005930");
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
