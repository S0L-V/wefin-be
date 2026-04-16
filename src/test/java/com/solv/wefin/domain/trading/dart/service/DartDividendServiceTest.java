package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartDividendClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartDividendApiResponse;
import com.solv.wefin.domain.trading.dart.client.dto.DartDividendItem;
import com.solv.wefin.domain.trading.dart.dto.DartDividendInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DartDividendServiceTest {

    @Mock
    private DartCorpCodeService dartCorpCodeService;

    @Mock
    private DartDividendClient dartDividendClient;

    @InjectMocks
    private DartDividendService dartDividendService;

    private DartDividendItem item(String category, String stockKind, String current) {
        return new DartDividendItem(category, stockKind, current, null, null);
    }

    private DartDividendApiResponse successResponse() {
        return new DartDividendApiResponse("000", "정상", List.of(
                item("주당 현금배당금(원)", "보통주", "1,444"),
                item("주당 현금배당금(원)", "우선주", "1,445"),
                item("현금배당수익률(%)", "보통주", "1.8"),
                item("현금배당성향(%)", "보통주", "17.5")
        ));
    }

    @Test
    void 배당_정상조회_보통주_당기값_추출() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartDividendClient.fetch(eq("00126380"), anyString(), anyString()))
                .willReturn(successResponse());

        // when
        DartDividendInfo result = dartDividendService.getDividend("005930");

        // then
        assertThat(result.dividendPerShare()).isEqualByComparingTo(new BigDecimal("1444"));
        assertThat(result.dividendYieldRate()).isEqualByComparingTo(new BigDecimal("1.8"));
        assertThat(result.payoutRatio()).isEqualByComparingTo(new BigDecimal("17.5"));
    }

    @Test
    void status_013이면_이전연도로_fallback() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        int currentYear = LocalDate.now().getYear();
        String firstYear = String.valueOf(currentYear - 1);
        String secondYear = String.valueOf(currentYear - 2);

        DartDividendApiResponse noData = new DartDividendApiResponse("013", "조회된 데이타가 없습니다.", null);
        given(dartDividendClient.fetch("00126380", firstYear, "11011")).willReturn(noData);
        given(dartDividendClient.fetch("00126380", secondYear, "11011")).willReturn(successResponse());

        // when
        DartDividendInfo result = dartDividendService.getDividend("005930");

        // then
        assertThat(result.businessYear()).isEqualTo(secondYear);
    }

    @Test
    void 연도_2회_fallback_모두_실패시_NOT_FOUND() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartDividendApiResponse noData = new DartDividendApiResponse("013", "조회된 데이타가 없습니다.", null);
        given(dartDividendClient.fetch(eq("00126380"), anyString(), anyString()))
                .willReturn(noData);

        // when & then
        assertThatThrownBy(() -> dartDividendService.getDividend("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_DIVIDEND_NOT_FOUND);
    }

    @Test
    void status가_000도_013도_아니면_FETCH_FAILED() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartDividendApiResponse error = new DartDividendApiResponse("020", "요청 제한 초과", null);
        given(dartDividendClient.fetch(eq("00126380"), anyString(), anyString()))
                .willReturn(error);

        // when & then
        assertThatThrownBy(() -> dartDividendService.getDividend("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_DIVIDEND_FETCH_FAILED);
    }

    @Test
    void list_비어있으면_NOT_FOUND() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartDividendApiResponse empty = new DartDividendApiResponse("000", "정상", List.of());
        given(dartDividendClient.fetch(eq("00126380"), anyString(), anyString()))
                .willReturn(empty);

        // when & then
        assertThatThrownBy(() -> dartDividendService.getDividend("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_DIVIDEND_NOT_FOUND);
    }

    @Test
    void 보통주_항목이_없으면_NOT_FOUND() {
        // given — 우선주만 있음
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartDividendApiResponse onlyPreferred = new DartDividendApiResponse("000", "정상", List.of(
                item("주당 현금배당금(원)", "우선주", "1,445"),
                item("현금배당수익률(%)", "우선주", "2.0")
        ));
        given(dartDividendClient.fetch(eq("00126380"), anyString(), anyString()))
                .willReturn(onlyPreferred);

        // when & then
        assertThatThrownBy(() -> dartDividendService.getDividend("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_DIVIDEND_NOT_FOUND);
    }

    @Test
    void 금액_대시나_null은_null로_파싱_하지만_다른_항목이_있으면_반환() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartDividendApiResponse response = new DartDividendApiResponse("000", "정상", List.of(
                item("주당 현금배당금(원)", "보통주", "1,444"),
                item("현금배당수익률(%)", "보통주", "-"),
                item("현금배당성향(%)", "보통주", null)
        ));
        given(dartDividendClient.fetch(eq("00126380"), anyString(), anyString()))
                .willReturn(response);

        // when
        DartDividendInfo result = dartDividendService.getDividend("005930");

        // then
        assertThat(result.dividendPerShare()).isEqualByComparingTo(new BigDecimal("1444"));
        assertThat(result.dividendYieldRate()).isNull();
        assertThat(result.payoutRatio()).isNull();
    }

    @Test
    void corpCode_미존재시_예외_전파() {
        // given
        given(dartCorpCodeService.getCorpCode("999999"))
                .willThrow(new BusinessException(ErrorCode.DART_CORP_CODE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> dartDividendService.getDividend("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_CORP_CODE_NOT_FOUND);
    }
}
