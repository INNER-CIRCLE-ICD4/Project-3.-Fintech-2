package fintech2.easypay.transfer;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.entity.AccountStatus;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.member.entity.Member;
import fintech2.easypay.member.repository.MemberRepository;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.repository.TransferRepository;
import fintech2.easypay.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 송금 동시성 테스트
 * 
 * 이 테스트는 동시에 발생하는 송금 요청에 대한 데이터 정합성을 검증합니다:
 * - 동시 송금 시 잔액 정합성
 * - 데드락 발생 시나리오
 * - 경합 조건(Race Condition) 처리
 * - 트랜잭션 격리 수준 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("송금 동시성 테스트")
class TransferConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    private Member memberA;
    private Member memberB;
    private Account accountA;
    private Account accountB;

    @BeforeEach
    @Transactional
    void setUp() {
        // 기존 데이터 정리
        transferRepository.deleteAll();
        accountRepository.deleteAll();
        memberRepository.deleteAll();
        
        // 회원 A 생성
        memberA = Member.builder()
                .phoneNumber("01012345678")
                .password("password123")
                .name("회원A")
                .build();
        memberA = memberRepository.saveAndFlush(memberA);

        // 회원 B 생성
        memberB = Member.builder()
                .phoneNumber("01087654321")
                .password("password123")
                .name("회원B")
                .build();
        memberB = memberRepository.saveAndFlush(memberB);

        // 계좌 A 생성 (초기 잔액 100,000원)
        accountA = Account.builder()
                .accountNumber("1111111111")
                .member(memberA)
                .balance(BigDecimal.valueOf(100000))
                .status(AccountStatus.ACTIVE)
                .build();
        accountA = accountRepository.saveAndFlush(accountA);

        // 계좌 B 생성 (초기 잔액 100,000원)
        accountB = Account.builder()
                .accountNumber("2222222222")
                .member(memberB)
                .balance(BigDecimal.valueOf(100000))
                .status(AccountStatus.ACTIVE)
                .build();
        accountB = accountRepository.saveAndFlush(accountB);
    }

    @Test
    @DisplayName("동시 송금 시 잔액 정합성 테스트")
    void concurrentTransferBalanceConsistencyTest() throws InterruptedException {
        // given: 10개의 동시 송금 요청 (각각 1,000원씩)
        int threadCount = 10;
        int transferAmount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 동시에 A → B로 송금 요청
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    TransferRequest request = new TransferRequest();
                    request.setReceiverAccountNumber(accountB.getAccountNumber());
                    request.setAmount(BigDecimal.valueOf(transferAmount));
                    request.setMemo("동시성 테스트 " + Thread.currentThread().getName());

                    transferService.transfer(memberA.getPhoneNumber(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("Transfer failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 잔액 정합성 확인
        Account updatedAccountA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account updatedAccountB = accountRepository.findById(accountB.getId()).orElseThrow();

        // 성공한 송금 건수에 따른 잔액 검증
        BigDecimal expectedAccountABalance = BigDecimal.valueOf(100000 - (successCount.get() * transferAmount));
        BigDecimal expectedAccountBBalance = BigDecimal.valueOf(100000 + (successCount.get() * transferAmount));

        assertThat(updatedAccountA.getBalance().compareTo(expectedAccountABalance)).isEqualTo(0);
        assertThat(updatedAccountB.getBalance().compareTo(expectedAccountBBalance)).isEqualTo(0);
        
        // 전체 금액 보존 확인
        BigDecimal totalBalance = updatedAccountA.getBalance().add(updatedAccountB.getBalance());
        assertThat(totalBalance.compareTo(BigDecimal.valueOf(200000))).isEqualTo(0);

        System.out.println("성공한 송금: " + successCount.get() + "건");
        System.out.println("실패한 송금: " + failCount.get() + "건");
        System.out.println("계좌 A 잔액: " + updatedAccountA.getBalance());
        System.out.println("계좌 B 잔액: " + updatedAccountB.getBalance());
    }

    @Test
    @DisplayName("데드락 시나리오 테스트 - 상호 송금")
    void deadlockScenarioTest() throws InterruptedException {
        // given: 두 사용자가 동시에 서로에게 송금
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: A → B, B → A 동시 송금
        // Thread 1: A → B로 50,000원 송금
        executor.submit(() -> {
            try {
                TransferRequest request = new TransferRequest();
                request.setReceiverAccountNumber(accountB.getAccountNumber());
                request.setAmount(BigDecimal.valueOf(50000));
                request.setMemo("A to B 송금");

                transferService.transfer(memberA.getPhoneNumber(), request);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                System.err.println("A→B 송금 실패: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: B → A로 30,000원 송금
        executor.submit(() -> {
            try {
                TransferRequest request = new TransferRequest();
                request.setReceiverAccountNumber(accountA.getAccountNumber());
                request.setAmount(BigDecimal.valueOf(30000));
                request.setMemo("B to A 송금");

                transferService.transfer(memberB.getPhoneNumber(), request);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                System.err.println("B→A 송금 실패: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // 모든 스레드 완료 대기 (타임아웃 설정으로 데드락 감지)
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 데드락 없이 완료되었는지 확인
        assertThat(completed).isTrue(); // 타임아웃 없이 완료되어야 함
        
        // 잔액 정합성 확인
        Account updatedAccountA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account updatedAccountB = accountRepository.findById(accountB.getId()).orElseThrow();

        // 전체 금액 보존 확인
        BigDecimal totalBalance = updatedAccountA.getBalance().add(updatedAccountB.getBalance());
        assertThat(totalBalance.compareTo(BigDecimal.valueOf(200000))).isEqualTo(0);

        System.out.println("데드락 테스트 완료");
        System.out.println("성공한 송금: " + successCount.get() + "건");
        System.out.println("실패한 송금: " + failCount.get() + "건");
        System.out.println("계좌 A 잔액: " + updatedAccountA.getBalance());
        System.out.println("계좌 B 잔액: " + updatedAccountB.getBalance());
    }

    @Test
    @DisplayName("잔액 부족 시 동시 송금 처리 테스트")
    void insufficientBalanceConcurrentTransferTest() throws InterruptedException {
        // given: 계좌 A의 잔액을 5,000원으로 설정
        accountA.withdraw(BigDecimal.valueOf(95000));
        accountRepository.save(accountA);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 10개의 동시 송금 요청 (각각 1,000원씩, 총 10,000원 > 잔액 5,000원)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    TransferRequest request = new TransferRequest();
                    request.setReceiverAccountNumber(accountB.getAccountNumber());
                    request.setAmount(BigDecimal.valueOf(1000));
                    request.setMemo("잔액 부족 테스트");

                    transferService.transfer(memberA.getPhoneNumber(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 잔액 부족으로 인한 적절한 실패 처리 확인
        Account updatedAccountA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account updatedAccountB = accountRepository.findById(accountB.getId()).orElseThrow();

        // 성공한 송금은 최대 5건까지만 가능 (5,000원 잔액)
        assertThat(successCount.get()).isLessThanOrEqualTo(5);
        
        // 잔액이 음수가 되지 않았는지 확인
        assertThat(updatedAccountA.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        
        // 전체 금액 보존 확인
        BigDecimal totalBalance = updatedAccountA.getBalance().add(updatedAccountB.getBalance());
        assertThat(totalBalance.compareTo(BigDecimal.valueOf(105000))).isEqualTo(0); // 초기 총 잔액

        System.out.println("잔액 부족 테스트 완료");
        System.out.println("성공한 송금: " + successCount.get() + "건");
        System.out.println("실패한 송금: " + failCount.get() + "건");
        System.out.println("계좌 A 잔액: " + updatedAccountA.getBalance());
        System.out.println("계좌 B 잔액: " + updatedAccountB.getBalance());
    }

    @Test
    @DisplayName("대량 동시 송금 성능 테스트")
    void highVolumeConcurrentTransferTest() throws InterruptedException {
        // given: 대량의 동시 송금 요청
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // when: 100개의 동시 송금 요청 (각각 100원씩)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    TransferRequest request = new TransferRequest();
                    request.setReceiverAccountNumber(accountB.getAccountNumber());
                    request.setAmount(BigDecimal.valueOf(100));
                    request.setMemo("대량 송금 테스트");

                    transferService.transfer(memberA.getPhoneNumber(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // then: 성능 및 정합성 검증
        Account updatedAccountA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account updatedAccountB = accountRepository.findById(accountB.getId()).orElseThrow();

        // 전체 금액 보존 확인
        BigDecimal totalBalance = updatedAccountA.getBalance().add(updatedAccountB.getBalance());
        assertThat(totalBalance.compareTo(BigDecimal.valueOf(200000))).isEqualTo(0);

        System.out.println("대량 동시 송금 테스트 완료");
        System.out.println("처리 시간: " + duration + "ms");
        System.out.println("성공한 송금: " + successCount.get() + "건");
        System.out.println("실패한 송금: " + failCount.get() + "건");
        System.out.println("초당 처리량: " + (successCount.get() * 1000.0 / duration) + " TPS");
        System.out.println("계좌 A 잔액: " + updatedAccountA.getBalance());
        System.out.println("계좌 B 잔액: " + updatedAccountB.getBalance());
    }
}