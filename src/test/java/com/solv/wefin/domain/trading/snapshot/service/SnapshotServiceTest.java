package com.solv.wefin.domain.trading.snapshot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.snapshot.entity.DailySnapshot;
import com.solv.wefin.domain.trading.snapshot.repository.DailySnapshotRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

	@Mock
	DailySnapshotRepository dailySnapshotRepository;
	@Mock
	VirtualAccountService accountService;
	@Mock
	SnapshotPersistenceService snapshotPersistenceService;

	@InjectMocks
	SnapshotService snapshotService;

	private static final LocalDate TEST_DATE = LocalDate.of(2026, 4, 9);

	@Test
	void 전체_스냅샷_생성_정상() {
		// given
		VirtualAccount mockAccount1 = mock(VirtualAccount.class);
		VirtualAccount mockAccount2 = mock(VirtualAccount.class);
		given(accountService.getAllAccounts()).willReturn(List.of(mockAccount1, mockAccount2));
		given(snapshotPersistenceService.createSnapshot(eq(mockAccount1), eq(TEST_DATE)))
			.willReturn(true);

		// when
		snapshotService.createAllSnapshots(TEST_DATE);

		// then
		verify(snapshotPersistenceService, times(2)).createSnapshot(any(), eq(TEST_DATE));
	}

	@Test
	void 한_계좌_실패해도_계속_진행() {
		// given
		VirtualAccount mockAccount1 = mock(VirtualAccount.class);
		VirtualAccount mockAccount2 = mock(VirtualAccount.class);
		given(accountService.getAllAccounts()).willReturn(List.of(mockAccount1, mockAccount2));
		given(snapshotPersistenceService.createSnapshot(eq(mockAccount1), eq(TEST_DATE)))
			.willThrow(new RuntimeException("실패"));
		given(snapshotPersistenceService.createSnapshot(eq(mockAccount2), eq(TEST_DATE)))
			.willReturn(true);

		// when
		snapshotService.createAllSnapshots(TEST_DATE);

		// then
		verify(snapshotPersistenceService, times(2)).createSnapshot(any(), eq(TEST_DATE));
	}

	@Test
	void 자산추이_조회_정상() {
		// given
		LocalDate from = LocalDate.of(2026, 4, 1);
		LocalDate to = TEST_DATE;
		given(dailySnapshotRepository.findByVirtualAccountIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
			1L, from, to)).willReturn(List.of());

		// when
		List<DailySnapshot> result = snapshotService.getAssetHistory(1L, from, to);

		// then
		assertThat(result).isEmpty();
		verify(dailySnapshotRepository).findByVirtualAccountIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
			1L, from, to);
	}

	@Test
	void 자산추이_조회_날짜_역전() {
		// given
		LocalDate from = TEST_DATE;
		LocalDate to = LocalDate.of(2026, 4, 1);

		// when & then
		assertThatThrownBy(() -> snapshotService.getAssetHistory(1L, from, to))
			.isInstanceOf(BusinessException.class)
			.hasMessage(ErrorCode.MARKET_INVALID_DATE.getMessage());
	}

	@Test
	void from_null이면_30일_전_기본값() {
		// given
		given(dailySnapshotRepository.findByVirtualAccountIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
			eq(1L), any(LocalDate.class), eq(TEST_DATE)
		)).willReturn(List.of());

		// when
		snapshotService.getAssetHistory(1L, null, TEST_DATE);

		// then
		verify(dailySnapshotRepository).findByVirtualAccountIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
			eq(1L), eq(TEST_DATE.minusDays(29)), eq(TEST_DATE));
	}
}