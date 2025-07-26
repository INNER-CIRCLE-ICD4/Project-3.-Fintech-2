package fintech2.easypay.payment.service.impl;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.payment.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 결제 모듈용 계좌 서비스 구현체
 * 실제 AccountRepository를 사용하여 계좌 관련 기능 제공
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {
    
    private final AccountRepository accountRepository;
    
    @Override
    public Optional<Account> findByMemberId(Long memberId) {
        return accountRepository.findByMemberId(memberId);
    }
    
    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }
    
    @Override
    @Transactional
    public void withdraw(Account account, BigDecimal amount) {
        account.withdraw(amount);
        accountRepository.save(account);
    }
    
    @Override
    @Transactional
    public void deposit(Account account, BigDecimal amount) {
        account.deposit(amount);
        accountRepository.save(account);
    }
    
    @Override
    @Transactional
    public Account save(Account account) {
        return accountRepository.save(account);
    }
}