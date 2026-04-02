package com.solv.wefin.domain.trading.account.service;

import static com.solv.wefin.domain.trading.common.TradingConstants.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.repository.VirtualAccountRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VirtualAccountService {

	private final VirtualAccountRepository accountRepository;

	/**
	 * 계좌 생성
	 */
	@Transactional
	public VirtualAccount createAccount(UUID userId) {
		Optional<VirtualAccount> account = accountRepository.findByUserId(userId);
		if (account.isPresent()) {
			throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
		}

		try {
			return accountRepository.save(new VirtualAccount(userId));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
		}
	}

	/**
	 * userId로 계좌 조회
	 */
	public VirtualAccount getAccountByUserId(UUID userId) {
		return accountRepository.findByUserId(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
	}

	/**
	 * 주문 가능 수량 조회
	 */
	public Integer calculateBuyingPower(Long virtualAccountId, BigDecimal price) {
		VirtualAccount account = accountRepository.findById(virtualAccountId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
		BigDecimal priceWithFee = price.multiply(BigDecimal.ONE.add(FEE_RATE));
		return account.getBalance().divide(priceWithFee, 0, RoundingMode.DOWN).intValue();
	}

	/**
	 * 잔고 차감
	 */
	@Transactional
	public VirtualAccount deductBalance(Long virtualAccountId, BigDecimal amount) {
		VirtualAccount account = getAccountForUpdate(virtualAccountId);

		account.deduct(amount);
		return account;
	}

	/**
	 * 잔고 입금
	 */
	@Transactional
	public VirtualAccount depositBalance(Long virtualAccountId, BigDecimal amount) {
		VirtualAccount account = getAccountForUpdate(virtualAccountId);

		account.deposit(amount);
		return account;
	}

	/**
	 * 실현손익 누적
	 */
	@Transactional
	public VirtualAccount addRealizedProfit(Long virtualAccountId, BigDecimal realizedProfit) {
		VirtualAccount account = getAccountForUpdate(virtualAccountId);

		account.addProfit(realizedProfit);
		return account;
	}

	/**
	 * 비관적 락으로 계좌 조회
	 */
	private VirtualAccount getAccountForUpdate(Long virtualAccountId) {
		return accountRepository.findByIdForUpdate(virtualAccountId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
	}
}
