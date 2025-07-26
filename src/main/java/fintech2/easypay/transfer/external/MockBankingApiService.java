package fintech2.easypay.transfer.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Mock 뱅킹 API 서비스 구현체
 * 실제 외부 API 대신 테스트용 Mock 응답을 생성
 */
@Service
@Slf4j
public class MockBankingApiService implements BankingApiService {
    
    // 거래 상태를 메모리에 저장 (실제로는 DB나 캐시 사용)
    private final ConcurrentHashMap<String, BankingApiResponse> transactionStore = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @Override
    public BankingApiResponse processTransfer(BankingApiRequest request) {
        log.info("Mock 뱅킹 API 호출 - 송금 처리: {}", request);
        
        try {
            // 타임아웃 시뮬레이션 (3% 확률로 3초 이상 지연)
            if (random.nextDouble() < 0.03) {
                simulateTimeout();
                return createTimeoutResponse(request.getTransactionId());
            }
            
            // 실제 API 호출 시뮬레이션 (50-200ms 지연)
            simulateApiDelay();
            
            // 랜덤하게 성공/실패 시나리오 생성
            BankingApiResponse response;
            double randomValue = random.nextDouble();
            
            if (randomValue < 0.80) {
                // 80% 성공 케이스
                response = BankingApiResponse.builder()
                        .transactionId(request.getTransactionId())
                        .bankTransactionId("BANK-" + UUID.randomUUID().toString())
                        .status(BankingApiStatus.SUCCESS)
                        .message("송금이 정상적으로 처리되었습니다.")
                        .processedAt(LocalDateTime.now())
                        .build();
            } else if (randomValue < 0.85) {
                // 5% 잔액 부족
                response = BankingApiResponse.builder()
                        .transactionId(request.getTransactionId())
                        .status(BankingApiStatus.INSUFFICIENT_BALANCE)
                        .errorCode("E001")
                        .errorMessage("송금 계좌의 잔액이 부족합니다.")
                        .processedAt(LocalDateTime.now())
                        .build();
            } else if (randomValue < 0.90) {
                // 5% 계좌 오류
                response = BankingApiResponse.builder()
                        .transactionId(request.getTransactionId())
                        .status(BankingApiStatus.INVALID_ACCOUNT)
                        .errorCode("E002")
                        .errorMessage("수신 계좌 정보가 올바르지 않습니다.")
                        .processedAt(LocalDateTime.now())
                        .build();
            } else if (randomValue < 0.95) {
                // 5% 시스템 오류
                response = BankingApiResponse.builder()
                        .transactionId(request.getTransactionId())
                        .status(BankingApiStatus.SYSTEM_ERROR)
                        .errorCode("E999")
                        .errorMessage("은행 시스템 오류가 발생했습니다.")
                        .processedAt(LocalDateTime.now())
                        .build();
            } else {
                // 5% 처리중 상태 (나중에 성공으로 변경됨)
                response = BankingApiResponse.builder()
                        .transactionId(request.getTransactionId())
                        .status(BankingApiStatus.PENDING)
                        .message("거래가 처리 중입니다.")
                        .processedAt(LocalDateTime.now())
                        .build();
                
                // 백그라운드에서 5초 후 성공으로 변경
                scheduleStatusUpdate(request.getTransactionId());
            }
            
            // 거래 정보 저장
            transactionStore.put(request.getTransactionId(), response);
            
            log.info("Mock 뱅킹 API 응답: {}", response);
            return response;
            
        } catch (TimeoutException e) {
            return createTimeoutResponse(request.getTransactionId());
        }
    }
    
    @Override
    public BankingApiResponse getTransferStatus(String transactionId) {
        log.info("Mock 뱅킹 API 호출 - 송금 상태 조회: {}", transactionId);
        
        // 저장된 거래 정보 반환
        BankingApiResponse response = transactionStore.get(transactionId);
        if (response == null) {
            return BankingApiResponse.builder()
                    .transactionId(transactionId)
                    .status(BankingApiStatus.FAILED)
                    .errorCode("E404")
                    .errorMessage("거래를 찾을 수 없습니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        
        return response;
    }
    
    /**
     * API 호출 지연 시뮬레이션
     */
    private void simulateApiDelay() {
        try {
            Thread.sleep(50 + random.nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 타임아웃 시뮬레이션 (3초 대기)
     */
    private void simulateTimeout() throws TimeoutException {
        try {
            Thread.sleep(3000);
            throw new TimeoutException("API 호출 타임아웃");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TimeoutException("API 호출 중단됨");
        }
    }
    
    /**
     * 타임아웃 응답 생성
     */
    private BankingApiResponse createTimeoutResponse(String transactionId) {
        BankingApiResponse response = BankingApiResponse.builder()
                .transactionId(transactionId)
                .status(BankingApiStatus.TIMEOUT)
                .errorCode("E_TIMEOUT")
                .errorMessage("API 호출 시간이 초과되었습니다. 거래 상태를 확인해주세요.")
                .processedAt(LocalDateTime.now())
                .build();
        
        // 타임아웃된 거래를 UNKNOWN 상태로 저장 (실제 처리 상태 알 수 없음)
        BankingApiResponse unknownResponse = BankingApiResponse.builder()
                .transactionId(transactionId)
                .status(BankingApiStatus.UNKNOWN)
                .message("거래 처리 상태를 확인할 수 없습니다.")
                .processedAt(LocalDateTime.now())
                .build();
        
        transactionStore.put(transactionId, unknownResponse);
        
        // 5초 후 실제 결과로 업데이트 (백그라운드 처리 시뮬레이션)
        scheduleActualResult(transactionId);
        
        return response;
    }
    
    /**
     * PENDING 상태 거래를 5초 후 성공으로 변경
     */
    private void scheduleStatusUpdate(String transactionId) {
        scheduler.schedule(() -> {
            BankingApiResponse successResponse = BankingApiResponse.builder()
                    .transactionId(transactionId)
                    .bankTransactionId("BANK-" + UUID.randomUUID().toString())
                    .status(BankingApiStatus.SUCCESS)
                    .message("송금이 완료되었습니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
            
            transactionStore.put(transactionId, successResponse);
            log.info("거래 상태 업데이트 완료: {} -> SUCCESS", transactionId);
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 타임아웃된 거래의 실제 결과를 5초 후 업데이트 (80% 성공, 20% 실패)
     */
    private void scheduleActualResult(String transactionId) {
        scheduler.schedule(() -> {
            BankingApiResponse actualResponse;
            if (random.nextDouble() < 0.8) {
                // 80% 성공
                actualResponse = BankingApiResponse.builder()
                        .transactionId(transactionId)
                        .bankTransactionId("BANK-" + UUID.randomUUID().toString())
                        .status(BankingApiStatus.SUCCESS)
                        .message("송금이 완료되었습니다. (지연 처리)")
                        .processedAt(LocalDateTime.now())
                        .build();
            } else {
                // 20% 실패
                actualResponse = BankingApiResponse.builder()
                        .transactionId(transactionId)
                        .status(BankingApiStatus.SYSTEM_ERROR)
                        .errorCode("E999")
                        .errorMessage("시스템 오류로 인한 거래 실패")
                        .processedAt(LocalDateTime.now())
                        .build();
            }
            
            transactionStore.put(transactionId, actualResponse);
            log.info("타임아웃 거래 실제 결과 업데이트: {} -> {}", transactionId, actualResponse.getStatus());
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 특정 금액 이상에서만 실패하도록 설정 (테스트용)
     */
    public BankingApiResponse processTransferWithThreshold(BankingApiRequest request, BigDecimal threshold) {
        if (request.getAmount().compareTo(threshold) > 0) {
            return BankingApiResponse.builder()
                    .transactionId(request.getTransactionId())
                    .status(BankingApiStatus.INSUFFICIENT_BALANCE)
                    .errorCode("E001")
                    .errorMessage("일일 송금 한도를 초과했습니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        return processTransfer(request);
    }
}