package fintech2.easypay.transfer.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.transfer.action.ActionResult;
import fintech2.easypay.transfer.action.TransferActionProcessor;
import fintech2.easypay.transfer.action.command.InternalTransferCommand;
import fintech2.easypay.transfer.action.command.SecureTransferCommand;
import fintech2.easypay.transfer.dto.RecentTransferResponse;
import fintech2.easypay.transfer.dto.SecureTransferRequest;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.repository.TransferRepository;

/**
 * 송금 서비스 (Action Pattern Facade)
 * DTO를 Command로 변환하고 TransferActionProcessor에 위임
 * API 레이어와 Action Pattern 레이어 간의 어댑터 역할
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransferService {
    
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final TransferActionProcessor transferActionProcessor;
    
    /**
     * 일반 송금 처리 (내부 계좌 간 송금)
     * TransferRequest를 InternalTransferCommand로 변환하여 처리
     * @param senderPhoneNumber 송금자 휴대폰 번호
     * @param request 송금 요청 정보
     * @return 송금 처리 결과
     * @throws BusinessException 송금 처리 중 오류 발생 시
     */
    @Transactional
    public TransferResponse transfer(String senderPhoneNumber, TransferRequest request) {
        log.info("Processing transfer request: sender={}, amount={}", senderPhoneNumber, request.getAmount());
        
        try {
            // DTO를 Command로 변환
            InternalTransferCommand command = InternalTransferCommand.builder()
                    .senderPhoneNumber(senderPhoneNumber)
                    .senderAccountNumber(request.getSenderAccountNumber())
                    .receiverAccountNumber(request.getReceiverAccountNumber())
                    .amount(request.getAmount())
                    .memo(request.getMemo())
                    .transactionId(generateTransactionId())
                    .build();
            
            // Action Pattern으로 처리
            ActionResult result = transferActionProcessor.process(command);
            
            // ActionResult를 DTO로 변환
            return convertActionResultToTransferResponse(result, command.getTransactionId());
            
        } catch (Exception e) {
            log.error("Transfer processing failed: sender={}, amount={}", senderPhoneNumber, request.getAmount(), e);
            
            if (e instanceof BusinessException) {
                throw e;
            }
            
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, 
                    "송금 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 보안 송금 처리 (PIN 검증 포함)
     * SecureTransferRequest를 SecureTransferCommand로 변환하여 처리
     * @param senderPhoneNumber 송금자 휴대폰 번호
     * @param request 보안 송금 요청 정보
     * @return 송금 처리 결과
     */
    @Transactional
    public TransferResponse secureTransfer(String senderPhoneNumber, SecureTransferRequest request) {
        log.info("Processing secure transfer request: sender={}, amount={}", senderPhoneNumber, request.getAmount());
        
        try {
            // DTO를 Command로 변환
            SecureTransferCommand command = SecureTransferCommand.builder()
                    .senderPhoneNumber(senderPhoneNumber)
                    .senderAccountNumber(request.getSenderAccountNumber())
                    .receiverAccountNumber(request.getReceiverAccountNumber())
                    .receiverBankCode("EASYPAY") // 기본값: 내부 송금
                    .amount(request.getAmount())
                    .memo(request.getMemo())
                    .pinSessionToken(request.getPinSessionToken())
                    .currency("KRW")
                    .transactionId(generateTransactionId())
                    .build();
            
            // Action Pattern으로 처리
            ActionResult result = transferActionProcessor.process(command);
            
            // ActionResult를 DTO로 변환
            return convertActionResultToTransferResponse(result, command.getTransactionId());
            
        } catch (Exception e) {
            log.error("Secure transfer processing failed: sender={}, amount={}", senderPhoneNumber, request.getAmount(), e);
            
            if (e instanceof BusinessException) {
                throw e;
            }
            
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, 
                    "보안 송금 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    public TransferResponse getTransfer(String transactionId) {
        Transfer transfer = transferRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        
        return TransferResponse.from(transfer);
    }
    
    public Page<TransferResponse> getTransferHistory(String phoneNumber, Pageable pageable) {
        Page<Transfer> transfers = transferRepository.findByPhoneNumberOrderByCreatedAtDesc(phoneNumber, pageable);
        return transfers.map(TransferResponse::from);
    }
    
    public Page<TransferResponse> getSentTransfers(String phoneNumber, Pageable pageable) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Page<Transfer> transfers = transferRepository.findBySenderIdOrderByCreatedAtDesc(user.getId(), pageable);
        return transfers.map(TransferResponse::from);
    }
    
    public Page<TransferResponse> getReceivedTransfers(String phoneNumber, Pageable pageable) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Page<Transfer> transfers = transferRepository.findByReceiverIdOrderByCreatedAtDesc(user.getId(), pageable);
        return transfers.map(TransferResponse::from);
    }
    
    /**
     * 최근 송금한 사람들의 목록 조회 (중복 제거)
     * 가장 최근에 송금한 순서대로 정렬
     * @param phoneNumber 사용자 휴대폰 번호
     * @param pageable 페이지 정보
     * @return 최근 송금 대상 목록
     */
    public Page<RecentTransferResponse> getRecentTransfers(String phoneNumber, Pageable pageable) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 성공한 송금만 조회하고, 수신자별로 가장 최근 거래만 가져옴
        Page<Transfer> transfers = transferRepository.findRecentDistinctReceivers(user.getId(), pageable);
        return transfers.map(RecentTransferResponse::from);
    }
    
    /**
     * ActionResult를 TransferResponse로 변환
     * @param result Action 처리 결과
     * @param transactionId 거래 ID
     * @return 변환된 TransferResponse
     */
    private TransferResponse convertActionResultToTransferResponse(ActionResult result, String transactionId) {
        if (!result.isSuccess()) {
            // 실패한 경우에도 트랜잭션 정보를 조회하여 응답 생성
            Transfer transfer = transferRepository.findByTransactionId(transactionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            
            // 실패 상태에 따른 적절한 오류 처리
            if (result.getCode().equals("INSUFFICIENT_BALANCE")) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            } else if (result.getCode().equals("VALIDATION_FAILED")) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, result.getMessage());
            } else if (result.getCode().equals("INVALID_PIN_SESSION")) {
                throw new BusinessException(ErrorCode.INVALID_PIN_SESSION, result.getMessage());
            } else {
                throw new BusinessException(ErrorCode.TRANSACTION_FAILED, result.getMessage());
            }
        }
        
        // 성공한 경우 Transfer 엔티티 조회 후 응답 생성
        Transfer transfer = transferRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        
        return TransferResponse.from(transfer);
    }
    
    /**
     * 고유한 거래 ID 생성
     * TXN 접두어 + 12자리 랜덤 문자열
     * 중복 방지를 위한 검증 로직 포함
     * @return 생성된 거래 ID
     */
    private String generateTransactionId() {
        String transactionId;
        do {
            transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (transferRepository.existsByTransactionId(transactionId));
        
        return transactionId;
    }
}
