package fintech2.easypay.payment.mock;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.payment.service.AccountService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 결제 테스트용 계좌 서비스 Mock 구현체
 * 실제 데이터베이스 없이 메모리에서 계좌 정보를 관리
 */
public class MockAccountService implements AccountService {
    
    private final Map<Long, Account> accountsByMemberId = new HashMap<>();
    private final Map<String, Account> accountsByAccountNumber = new HashMap<>();
    
    /**
     * 테스트용 계좌 데이터 추가
     */
    public void addTestAccount(Account account) {
        accountsByMemberId.put(account.getMember().getId(), account);
        accountsByAccountNumber.put(account.getAccountNumber(), account);
    }
    
    /**
     * 테스트용 데이터 초기화
     */
    public void clearTestData() {
        accountsByMemberId.clear();
        accountsByAccountNumber.clear();
    }
    
    @Override
    public Optional<Account> findByMemberId(Long memberId) {
        return Optional.ofNullable(accountsByMemberId.get(memberId));
    }
    
    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return Optional.ofNullable(accountsByAccountNumber.get(accountNumber));
    }
    
    @Override
    public void withdraw(Account account, BigDecimal amount) {
        account.withdraw(amount);
    }
    
    @Override
    public void deposit(Account account, BigDecimal amount) {
        account.deposit(amount);
    }
    
    @Override
    public Account save(Account account) {
        // Mock 환경에서는 실제 저장하지 않고 그대로 반환
        return account;
    }
}