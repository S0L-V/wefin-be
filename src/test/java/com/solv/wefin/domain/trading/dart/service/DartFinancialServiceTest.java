package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartFinancialClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartFinancialApiResponse;
import com.solv.wefin.domain.trading.dart.client.dto.DartFinancialItem;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialSummary;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DartFinancialServiceTest {

    @Mock
    private DartCorpCodeService dartCorpCodeService;

    @Mock
    private DartFinancialClient dartFinancialClient;

    @InjectMocks
    private DartFinancialService dartFinancialService;

    private DartFinancialItem item(String accountId, String thstrm, String frmtrm, String bfe) {
        return new DartFinancialItem(
                accountIdToSjDiv(accountId), accountId, accountId, // sj/account_id/account_nm
                "제 55 기", thstrm,
                "제 54 기", frmtrm,
                "제 53 기", bfe,
                "KRW"
        );
    }

    private String accountIdToSjDiv(String accountId) {
        if (accountId.contains("Assets") || accountId.contains("Liabilities") || accountId.contains("Equity")) return "BS";
        return "IS";
    }

    private DartFinancialApiResponse successResponse() {
        return new DartFinancialApiResponse("000", "정상", List.of(
                item("ifrs-full_Assets", "448234000000000", "447000000000000", "415000000000000"),
                item("ifrs-full_Liabilities", "92000000000000", "91000000000000", "85000000000000"),
                item("ifrs-full_Equity", "356000000000000", "356000000000000", "330000000000000"),
                item("ifrs-full_Revenue", "258000000000000", "302000000000000", "279000000000000"),
                item("dart_OperatingIncomeLoss", "6566000000000", "43300000000000", "51600000000000"),
                item("ifrs-full_ProfitLoss", "15500000000000", "55600000000000", "39200000000000")
        ));
    }

    @Test
    void 재무제표_정상_추출_당기_전기_전전기() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartFinancialClient.fetch(eq("00126380"), anyString(), anyString(), anyString()))
                .willReturn(successResponse());

        // when
        DartFinancialSummary result = dartFinancialService.getFinancialSummary("005930");

        // then
        assertThat(result.currency()).isEqualTo("KRW");
        assertThat(result.currentPeriod().totalAssets())
                .isEqualByComparingTo(new BigDecimal("448234000000000"));
        assertThat(result.currentPeriod().revenue())
                .isEqualByComparingTo(new BigDecimal("258000000000000"));
        assertThat(result.currentPeriod().operatingIncome())
                .isEqualByComparingTo(new BigDecimal("6566000000000"));
        assertThat(result.previousPeriod().netIncome())
                .isEqualByComparingTo(new BigDecimal("55600000000000"));
        assertThat(result.prePreviousPeriod().totalEquity())
                .isEqualByComparingTo(new BigDecimal("330000000000000"));
    }

    @Test
    void status_013이면_이전연도로_fallback() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        int currentYear = LocalDate.now().getYear();
        String firstYear = String.valueOf(currentYear - 1);
        String secondYear = String.valueOf(currentYear - 2);

        DartFinancialApiResponse noData = new DartFinancialApiResponse("013", "조회된 데이타가 없습니다.", null);
        given(dartFinancialClient.fetch("00126380", firstYear, "11011", "CFS")).willReturn(noData);
        given(dartFinancialClient.fetch("00126380", secondYear, "11011", "CFS")).willReturn(successResponse());

        // when
        DartFinancialSummary result = dartFinancialService.getFinancialSummary("005930");

        // then
        assertThat(result.businessYear()).isEqualTo(secondYear);
        verify(dartFinancialClient).fetch("00126380", firstYear, "11011", "CFS");
        verify(dartFinancialClient).fetch("00126380", secondYear, "11011", "CFS");
    }

    @Test
    void 연도_2회_fallback_모두_실패시_NOT_FOUND() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartFinancialApiResponse noData = new DartFinancialApiResponse("013", "조회된 데이타가 없습니다.", null);
        given(dartFinancialClient.fetch(eq("00126380"), anyString(), anyString(), anyString()))
                .willReturn(noData);

        // when & then
        assertThatThrownBy(() -> dartFinancialService.getFinancialSummary("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_FINANCIAL_NOT_FOUND);
    }

    @Test
    void status가_000도_013도_아니면_FETCH_FAILED() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartFinancialApiResponse error = new DartFinancialApiResponse("020", "요청 제한 초과", null);
        given(dartFinancialClient.fetch(eq("00126380"), anyString(), anyString(), anyString()))
                .willReturn(error);

        // when & then
        assertThatThrownBy(() -> dartFinancialService.getFinancialSummary("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_FINANCIAL_FETCH_FAILED);
    }

    @Test
    void 응답이_null이면_FETCH_FAILED() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartFinancialClient.fetch(eq("00126380"), anyString(), anyString(), anyString()))
                .willReturn(null);

        // when & then
        assertThatThrownBy(() -> dartFinancialService.getFinancialSummary("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_FINANCIAL_FETCH_FAILED);
    }

    @Test
    void 금액이_대시_또는_null인_계정은_null로_파싱() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartFinancialApiResponse response = new DartFinancialApiResponse("000", "정상", List.of(
                item("ifrs-full_Assets", "448234000000000", "447000000000000", "415000000000000"),
                item("ifrs-full_Revenue", "-", null, ""),
                item("dart_OperatingIncomeLoss", "NOT_A_NUMBER", "43300000000000", "51600000000000")
        ));
        given(dartFinancialClient.fetch(eq("00126380"), anyString(), anyString(), anyString()))
                .willReturn(response);

        // when
        DartFinancialSummary result = dartFinancialService.getFinancialSummary("005930");

        // then
        assertThat(result.currentPeriod().totalAssets()).isNotNull();
        assertThat(result.currentPeriod().revenue()).isNull();      // "-"
        assertThat(result.previousPeriod().revenue()).isNull();     // null
        assertThat(result.prePreviousPeriod().revenue()).isNull();  // ""
        assertThat(result.currentPeriod().operatingIncome()).isNull(); // NumberFormatException
        assertThat(result.previousPeriod().operatingIncome())
                .isEqualByComparingTo(new BigDecimal("43300000000000"));
    }

    @Test
    void 영업이익은_금융지주의_ifrs_표준_account_id도_매칭() {
        // given — 신한지주 패턴: dart_OperatingIncomeLoss 없고 ifrs-full_ProfitLossFromOperatingActivities 사용
        given(dartCorpCodeService.getCorpCode("055550")).willReturn("00382199");
        DartFinancialApiResponse response = new DartFinancialApiResponse("000", "정상", List.of(
                item("ifrs-full_Assets", "500000000000000", "480000000000000", "450000000000000"),
                item("ifrs-full_Liabilities", "450000000000000", "430000000000000", "400000000000000"),
                item("ifrs-full_Equity", "50000000000000", "50000000000000", "50000000000000"),
                // dart_OperatingIncomeLoss 없음 — IFRS 표준 영업이익만
                new DartFinancialItem("IS", "ifrs-full_ProfitLossFromOperatingActivities",
                        "영업이익", "제 24 기", "5000000000000",
                        "제 23 기", "4500000000000",
                        "제 22 기", "4000000000000",
                        "KRW"),
                item("ifrs-full_ProfitLoss", "4000000000000", "3500000000000", "3000000000000")
        ));
        given(dartFinancialClient.fetch(eq("00382199"), anyString(), anyString(), anyString()))
                .willReturn(response);

        // when
        DartFinancialSummary result = dartFinancialService.getFinancialSummary("055550");

        // then — IFRS 영업이익이 매핑됨
        assertThat(result.currentPeriod().operatingIncome())
                .isEqualByComparingTo(new BigDecimal("5000000000000"));
        assertThat(result.currentPeriod().netIncome())
                .isEqualByComparingTo(new BigDecimal("4000000000000"));
    }

    @Test
    void 핵심_6개_계정이_하나도_매칭안되면_NOT_FOUND() {
        // given — list는 비어있지 않지만 6개 account_id 중 하나도 매칭 안 됨
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartFinancialApiResponse response = new DartFinancialApiResponse("000", "정상", List.of(
                item("ifrs-full_UnknownAccount", "100", "100", "100"),
                item("dart_SomethingElse", "200", "200", "200")
        ));
        given(dartFinancialClient.fetch(eq("00126380"), anyString(), anyString(), anyString()))
                .willReturn(response);

        // when & then
        assertThatThrownBy(() -> dartFinancialService.getFinancialSummary("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_FINANCIAL_NOT_FOUND);
    }

    @Test
    void list이_비어있으면_NOT_FOUND() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        DartFinancialApiResponse empty = new DartFinancialApiResponse("000", "정상", List.of());
        given(dartFinancialClient.fetch(eq("00126380"), anyString(), anyString(), anyString()))
                .willReturn(empty);

        // when & then
        assertThatThrownBy(() -> dartFinancialService.getFinancialSummary("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_FINANCIAL_NOT_FOUND);
    }

    @Test
    void corpCode_미존재시_예외_전파() {
        // given
        given(dartCorpCodeService.getCorpCode("999999"))
                .willThrow(new BusinessException(ErrorCode.DART_CORP_CODE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> dartFinancialService.getFinancialSummary("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_CORP_CODE_NOT_FOUND);
    }
}
