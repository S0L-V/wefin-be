package com.solv.wefin.domain.trading.ranking.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.ranking.dto.DailyRankingInfo;
import com.solv.wefin.domain.trading.ranking.dto.DailyRankingRow;
import com.solv.wefin.domain.trading.ranking.repository.RankingQueryRepository;
import com.solv.wefin.domain.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

	@Mock
	private RankingQueryRepository rankingQueryRepository;
	@Mock
	private VirtualAccountService virtualAccountService;
	@Mock
	private UserService userService;


	@InjectMocks
	private RankingService rankingService;

	@Test
	void 일일_랭킹_TOP10_정상() {
		// given
		List<DailyRankingRow> mockRows = new ArrayList<>();
		List<VirtualAccount> mockAccounts = new ArrayList<>();
		List<User> mockUsers = new ArrayList<>();

		for (int i = 0; i < 12; i++) {
			Long accountId = 100L + i;
			UUID userId = UUID.randomUUID();
			BigDecimal profit = new BigDecimal((12 - i) * 1000);

			mockRows.add(new DailyRankingRow(accountId, profit, 5L));

			if (i < 10) {
				mockAccounts.add(mockAccount(accountId, userId));
				mockUsers.add(mockUser(userId, "user" + i));
			}
		}

		given(rankingQueryRepository.findDailySellAggregates(any(), any()))
			.willReturn(mockRows);
		given(virtualAccountService.findAllByIdIn(anyList()))
			.willReturn(mockAccounts);
		given(userService.findAllByIdIn(anyList()))
			.willReturn(mockUsers);

		// when
		DailyRankingInfo result = rankingService.getDailyRanking(null);

		// then
		assertThat(result.rankings()).hasSize(10);
		assertThat(result.rankings().get(0).rank()).isEqualTo(1);
		assertThat(result.rankings().get(0).realizedProfit()).isEqualByComparingTo("12000");
		assertThat(result.rankings().get(9).rank()).isEqualTo(10);
		assertThat(result.rankings().get(9).realizedProfit()).isEqualByComparingTo("3000");
		assertThat(result.myRank()).isNull();

		ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
		ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(rankingQueryRepository).findDailySellAggregates(
			startCaptor.capture(), endCaptor.capture());

		ZoneId kst = ZoneId.of("Asia/Seoul");
		LocalDate today = LocalDate.now(kst);
		assertThat(startCaptor.getValue()).isEqualTo(today.atStartOfDay(kst).toOffsetDateTime());
		assertThat(endCaptor.getValue()).isEqualTo(today.plusDays(1).atStartOfDay(kst).toOffsetDateTime());
	}

	@Test
	void 거래_없으면_빈_리스트() {
		// given
		given(rankingQueryRepository.findDailySellAggregates(any(), any()))
			.willReturn(List.of());
		given(virtualAccountService.findAllByIdIn(anyList()))
			.willReturn(List.of());
		given(userService.findAllByIdIn(anyList())).willReturn(List.of());

		// when
		DailyRankingInfo result = rankingService.getDailyRanking(null);

		// then
		assertThat(result.rankings()).isEmpty();
		assertThat(result.myRank()).isNull();
	}

	@Test
	void myRank_TOP10_안() {
		// given
		UUID callerUserId = UUID.randomUUID();

		List<DailyRankingRow> mockRows = List.of(
			new DailyRankingRow(100L, new BigDecimal("5000"), 5L),
			new DailyRankingRow(101L, new BigDecimal("4000"), 4L),
			new DailyRankingRow(102L, new BigDecimal("3000"), 3L),
			new DailyRankingRow(103L, new BigDecimal("2000"), 2L),
			new DailyRankingRow(104L, new BigDecimal("1000"), 1L)
		);

		VirtualAccount account1 = mockAccount(100L, UUID.randomUUID());
		VirtualAccount account2 = mockAccount(101L, UUID.randomUUID());
		VirtualAccount account3 = mockAccount(102L, callerUserId);
		VirtualAccount account4 = mockAccount(103L, UUID.randomUUID());
		VirtualAccount account5 = mockAccount(104L, UUID.randomUUID());

		User user1 = mockUser(account1.getUserId(), "user1");
		User user2 = mockUser(account2.getUserId(), "user2");
		User user3 = mockUser(account3.getUserId(), "user3");
		User user4 = mockUser(account4.getUserId(), "user4");
		User user5 = mockUser(account5.getUserId(), "user5");

		given(rankingQueryRepository.findDailySellAggregates(any(), any()))
			.willReturn(mockRows);
		given(virtualAccountService.findAllByIdIn(anyList()))
			.willReturn(List.of(account1, account2, account3, account4, account5));
		given(userService.findAllByIdIn(anyList()))
			.willReturn(List.of(user1, user2, user3, user4, user5));

		given(virtualAccountService.findByUserId(callerUserId)).willReturn(Optional.of(account3));

		// when
		DailyRankingInfo result = rankingService.getDailyRanking(callerUserId);

		// then
		assertThat(result.myRank()).isNotNull();
		assertThat(result.myRank().rank()).isEqualTo(3);
		assertThat(result.myRank().realizedProfit()).isEqualByComparingTo("3000");
	}

	@Test
	void myRank_TOP10_밖() {
		// given
		UUID callerUserId = UUID.randomUUID();
		Long callerAccountId = 110L;

		List<DailyRankingRow> mockRows = new ArrayList<>();
		List<VirtualAccount> top10Accounts = new ArrayList<>();
		List<User> top10Users = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			Long accountId = 100L + i;
			UUID userId = UUID.randomUUID();
			BigDecimal profit = new BigDecimal((12 - i) * 1000);
			mockRows.add(new DailyRankingRow(accountId, profit, 1L));

			if (i < 10) {
				top10Accounts.add(mockAccount(accountId, userId));
				top10Users.add(mockUser(userId, "user" + i));
			}
		}

		VirtualAccount myAccount = mockMyAccount(callerAccountId);

		given(rankingQueryRepository.findDailySellAggregates(any(), any()))
			.willReturn(mockRows);
		given(virtualAccountService.findAllByIdIn(anyList()))
			.willReturn(top10Accounts);
		given(userService.findAllByIdIn(anyList()))
			.willReturn(top10Users);
		given(virtualAccountService.findByUserId(callerUserId))
			.willReturn(Optional.of(myAccount));

		// when
		DailyRankingInfo result = rankingService.getDailyRanking(callerUserId);

		// then
		assertThat(result.rankings()).hasSize(10);
		assertThat(result.myRank().rank()).isEqualTo(11);
		assertThat(result.myRank().realizedProfit()).isEqualByComparingTo("2000");
	}

	@Test
	void myRank_거래_없는_사용자() {
		// given
		UUID callerUserId = UUID.randomUUID();
		Long callerAccountId = 999L;

		List<DailyRankingRow> mockRows = List.of(
			new DailyRankingRow(100L, new BigDecimal("5000"), 5L),
			new DailyRankingRow(101L, new BigDecimal("4000"), 4L)
		);

		VirtualAccount account1 = mockAccount(100L, UUID.randomUUID());
		VirtualAccount account2 = mockAccount(101L, UUID.randomUUID());
		User user1 = mockUser(account1.getUserId(), "user1");
		User user2 = mockUser(account2.getUserId(), "user2");

		VirtualAccount myAccount = mockMyAccount(callerAccountId);

		given(rankingQueryRepository.findDailySellAggregates(any(), any()))
			.willReturn(mockRows);
		given(virtualAccountService.findAllByIdIn(anyList()))
			.willReturn(List.of(account1, account2));
		given(userService.findAllByIdIn(anyList()))
			.willReturn(List.of(user1, user2));
		given(virtualAccountService.findByUserId(callerUserId))
			.willReturn(Optional.of(myAccount));

		// when
		DailyRankingInfo result = rankingService.getDailyRanking(callerUserId);

		// then
		assertThat(result.rankings()).hasSize(2);
		assertThat(result.myRank()).isNull();
	}

	@Test
	void myRank_계좌_없음() {
		// given
		UUID callerUserId = UUID.randomUUID();

		given(rankingQueryRepository.findDailySellAggregates(any(), any()))
			.willReturn(List.of());
		given(virtualAccountService.findAllByIdIn(anyList()))
			.willReturn(List.of());
		given(userService.findAllByIdIn(anyList()))
			.willReturn(List.of());

		given(virtualAccountService.findByUserId(callerUserId))
			.willReturn(Optional.empty());

		// when
		DailyRankingInfo result = rankingService.getDailyRanking(callerUserId);

		// then
		assertThat(result.rankings()).isEmpty();
		assertThat(result.myRank()).isNull();
	}

	@Test
	void 닉네임_매핑_누락_행_제외() {
		// given
		List<DailyRankingRow> mockRows = List.of(
			new DailyRankingRow(100L, new BigDecimal("5000"), 5L),
			new DailyRankingRow(101L, new BigDecimal("4000"), 4L)
		);

		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();

		VirtualAccount account1 = mockAccount(100L, userId1);
		VirtualAccount account2 = mockAccount(101L, userId2);
		User user1 = mockUser(userId1, "user1");

		given(rankingQueryRepository.findDailySellAggregates(any(), any()))
			.willReturn(mockRows);
		given(virtualAccountService.findAllByIdIn(anyList()))
			.willReturn(List.of(account1, account2));
		given(userService.findAllByIdIn(anyList()))
			.willReturn(List.of(user1));

		// when
		DailyRankingInfo result = rankingService.getDailyRanking(null);

		// then
		assertThat(result.rankings()).hasSize(1);
		assertThat(result.rankings().get(0).nickname()).isEqualTo("user1");
		assertThat(result.rankings().get(0).rank()).isEqualTo(1);
	}

	private VirtualAccount mockMyAccount(Long virtualAccountId) {
		VirtualAccount mockAccount = mock(VirtualAccount.class);
		given(mockAccount.getVirtualAccountId()).willReturn(virtualAccountId);
		return mockAccount;
	}

	private VirtualAccount mockAccount(Long virtualAccountId, UUID userId) {
		VirtualAccount mockAccount = mock(VirtualAccount.class);
		given(mockAccount.getVirtualAccountId()).willReturn(virtualAccountId);
		given(mockAccount.getUserId()).willReturn(userId);
		return mockAccount;
	}

	private User mockUser(UUID userId, String nickname) {
		User mockUser = mock(User.class);
		given(mockUser.getUserId()).willReturn(userId);
		given(mockUser.getNickname()).willReturn(nickname);
		return mockUser;
	}
}