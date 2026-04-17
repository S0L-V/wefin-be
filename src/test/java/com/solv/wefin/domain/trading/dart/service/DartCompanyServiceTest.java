package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartCompanyClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartCompanyApiResponse;
import com.solv.wefin.domain.trading.dart.dto.DartCompanyInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DartCompanyServiceTest {

    @Mock
    private DartCorpCodeService dartCorpCodeService;

    @Mock
    private DartCompanyClient dartCompanyClient;

    @InjectMocks
    private DartCompanyService dartCompanyService;

    private DartCompanyApiResponse okResponse(String status) {
        return new DartCompanyApiResponse(
                status, status.equals("000") ? "정상" : "에러",
                "삼성전자", "SAMSUNG ELECTRONICS CO,.LTD",
                "삼성전자", "005930", "한종희", "Y",
                "1301110006246", "1248100998",
                "경기도 수원시 영통구 삼성로 129", "www.samsung.com/sec", "",
                "02-2255-0114", "031-200-7538",
                "264", "19690113", "12"
        );
    }

    @Test
    void stockCode로_기업정보_정상조회() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartCompanyClient.fetch("00126380")).willReturn(okResponse("000"));

        // when
        DartCompanyInfo result = dartCompanyService.getCompany("005930");

        // then
        assertThat(result.corpName()).isEqualTo("삼성전자");
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.ceoName()).isEqualTo("한종희");
        assertThat(result.homepageUrl()).isEqualTo("www.samsung.com/sec");
    }

    @Test
    void corpCode_미존재시_DART_CORP_CODE_NOT_FOUND_전파() {
        // given
        given(dartCorpCodeService.getCorpCode("999999"))
                .willThrow(new BusinessException(ErrorCode.DART_CORP_CODE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> dartCompanyService.getCompany("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_CORP_CODE_NOT_FOUND);
    }

    @Test
    void DART_응답이_null이면_FETCH_FAILED() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartCompanyClient.fetch("00126380")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> dartCompanyService.getCompany("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_COMPANY_FETCH_FAILED);
    }

    @Test
    void DART_status_013이면_COMPANY_NOT_FOUND() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartCompanyClient.fetch("00126380")).willReturn(okResponse("013"));

        // when & then
        assertThatThrownBy(() -> dartCompanyService.getCompany("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_COMPANY_NOT_FOUND);
    }

    @Test
    void DART_status가_000도_013도_아니면_FETCH_FAILED() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartCompanyClient.fetch("00126380")).willReturn(okResponse("020"));

        // when & then
        assertThatThrownBy(() -> dartCompanyService.getCompany("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_COMPANY_FETCH_FAILED);
    }
}
