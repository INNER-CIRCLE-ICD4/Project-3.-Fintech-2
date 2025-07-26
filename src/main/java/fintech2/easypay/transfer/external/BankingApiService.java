package fintech2.easypay.transfer.external;

import java.math.BigDecimal;

/**
 * 외부 뱅킹 API 서비스 인터페이스
 * 펌뱅킹/오픈뱅킹을 통한 실제 은행 시스템과의 통신을 담당
 */
public interface BankingApiService {
    
    /**
     * 외부 은행 API를 통한 송금 처리
     * @param request 송금 요청 정보
     * @return 송금 처리 결과
     */
    BankingApiResponse processTransfer(BankingApiRequest request);
    
    /**
     * 송금 상태 조회
     * @param transactionId 거래 ID
     * @return 송금 상태 정보
     */
    BankingApiResponse getTransferStatus(String transactionId);
}