package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.entity.DartCorpCode;
import com.solv.wefin.domain.trading.dart.repository.DartCorpCodeRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DartCorpCodeServiceTest {

    @Mock
    private DartCorpCodeRepository dartCorpCodeRepository;

    @InjectMocks
    private DartCorpCodeService dartCorpCodeService;

    @Test
    void stockCode로_corpCode_정상조회() {
        // given
        DartCorpCode entity = new DartCorpCode("005930", "00126380", "삼성전자", "20251201");
        given(dartCorpCodeRepository.findByStockCode("005930"))
                .willReturn(Optional.of(entity));

        // when
        String corpCode = dartCorpCodeService.getCorpCode("005930");

        // then
        assertThat(corpCode).isEqualTo("00126380");
    }

    @Test
    void 존재하지_않는_stockCode_조회시_DART_CORP_CODE_NOT_FOUND_예외() {
        // given
        given(dartCorpCodeRepository.findByStockCode("999999"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dartCorpCodeService.getCorpCode("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_CORP_CODE_NOT_FOUND);
    }
}
