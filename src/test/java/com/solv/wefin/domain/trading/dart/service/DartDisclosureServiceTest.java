package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartDisclosureClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartDisclosureApiResponse;
import com.solv.wefin.domain.trading.dart.dto.DartDisclosureInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DartDisclosureServiceTest {

    @Mock
    private DartCorpCodeService dartCorpCodeService;

    @Mock
    private DartDisclosureClient dartDisclosureClient;

    @InjectMocks
    private DartDisclosureService dartDisclosureService;

    private DartDisclosureApiResponse successResponse() {
        return new DartDisclosureApiResponse("000", "정상", 1, 20, 2, 1, List.of(
                new DartDisclosureApiResponse.Item(
                        "20260413800802",
                        "최대주주등소유주식변동신고서              ",
                        "20260413",
                        "삼성전자",
                        "Y",
                        "유"
                ),
                new DartDisclosureApiResponse.Item(
                        "20260413002934",
                        "주식등의대량보유상황보고서(일반)",
                        "20260413",
                        "삼성물산",
                        "Y",
                        null
                )
        ));
    }

    @Test
    void 공시_정상조회_items_매핑_및_viewer_url_생성() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartDisclosureClient.fetch(eq("00126380"), anyString(), anyString(), anyInt()))
                .willReturn(successResponse());

        // when
        DartDisclosureInfo result = dartDisclosureService.getDisclosures("005930");

        // then
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).receiptNo()).isEqualTo("20260413800802");
        // trim 확인
        assertThat(result.items().get(0).reportName()).isEqualTo("최대주주등소유주식변동신고서");
        assertThat(result.items().get(0).viewerUrl())
                .isEqualTo("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260413800802");
        assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    void status_013이면_빈_리스트_반환() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartDisclosureClient.fetch(eq("00126380"), anyString(), anyString(), anyInt()))
                .willReturn(new DartDisclosureApiResponse("013", "조회된 데이타가 없습니다.",
                        null, null, 0, 0, null));

        // when
        DartDisclosureInfo result = dartDisclosureService.getDisclosures("005930");

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(0);
    }

    @Test
    void status가_000도_013도_아니면_FETCH_FAILED() {
        // given
        given(dartCorpCodeService.getCorpCode("005930")).willReturn("00126380");
        given(dartDisclosureClient.fetch(eq("00126380"), anyString(), anyString(), anyInt()))
                .willReturn(new DartDisclosureApiResponse("020", "요청 제한 초과",
                        null, null, 0, 0, null));

        // when & then
        assertThatThrownBy(() -> dartDisclosureService.getDisclosures("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_DISCLOSURE_FETCH_FAILED);
    }

    @Test
    void corpCode_미존재시_예외_전파() {
        // given
        given(dartCorpCodeService.getCorpCode("999999"))
                .willThrow(new BusinessException(ErrorCode.DART_CORP_CODE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> dartDisclosureService.getDisclosures("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DART_CORP_CODE_NOT_FOUND);
    }
}
