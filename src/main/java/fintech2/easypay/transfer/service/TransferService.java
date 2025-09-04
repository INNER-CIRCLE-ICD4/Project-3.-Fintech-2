package fintech2.easypay.transfer.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationContext;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.transfer.action.ActionResult;
import fintech2.easypay.transfer.action.TransferActionProcessor;
import fintech2.easypay.transfer.command.InternalTransferCommand;
import fintech2.easypay.transfer.dto.RecentTransferResponse;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.repository.TransferRepository;

/**
 * 송금 서비스 (리팩토링됨)
 * Action Pattern을 사용한 송금 처리 및 거래 내역 관리
 * TransferActionProcessor에 송금 로직을 위임하여 관심사 분리
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransferService {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransferActionProcessor actionProcessor;
    private final ApplicationContext applicationContext;
    
    /**
     * 사용자 간 송금 처리 (리팩토링됨)
     * Action Pattern을 사용하여 송금 로직을 분리
     * @param senderPhoneNumber 송금자 휴대폰 번호
     * @param request 송금 요청 정보
     * @return 송금 처리 결과
     * @throws BusinessException 송금 처리 중 오류 발생 시
     */
    @Transactional
    public TransferResponse transfer(String senderPhoneNumber, TransferRequest request) {
        log.info("송금 요청 시작: sender={}, receiver={}, amount={}", 
                senderPhoneNumber, request.getReceiverAccountNumber(), request.getAmount());
        
        // 송금자 조회
        User sender = userRepository.findByPhoneNumber(senderPhoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 수신자 계좌 조회
        Account receiverAccount = accountRepository
            .findByAccountNumber(request.getReceiverAccountNumber())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
        
        User receiver = userRepository.findById(receiverAccount.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 송금자 계좌 결정
        String senderAccountNumber = determineSenderAccount(sender, request.getSenderAccountNumber());
        
        // 거래 ID 생성
        String transactionId = generateTransactionId();
        
        // 내부 송금 커맨드 생성
        InternalTransferCommand command = new InternalTransferCommand(
                sender.getId(),
                receiver.getId(),
                senderAccountNumber,
                request.getReceiverAccountNumber(),
                request.getAmount(),
                request.getMemo(),
                transactionId
        );
        
        // Action Processor를 통한 송금 처리
        ActionResult result = actionProcessor.process(command);
        
        // 결과에 따른 응답 생성
        if (result.isSuccess() || result.isPending()) {
            Transfer transfer = transferRepository.findByTransactionId(transactionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            
            log.info("송금 요청 완료: transactionId={}, status={}", transactionId, result.getStatus());
            return TransferResponse.from(transfer);
        } else {
            log.error("송금 요청 실패: transactionId={}, message={}", transactionId, result.getMessage());
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, result.getMessage());
        }
    }
    
    /**
     * 송금자 계좌번호 결정
     */
    private String determineSenderAccount(User sender, String requestedAccountNumber) {
        if (requestedAccountNumber != null && !requestedAccountNumber.trim().isEmpty()) {
            // 특정 계좌 지정된 경우 소유권 확인
            Account account = accountRepository.findByAccountNumber(requestedAccountNumber)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
            
            if (!account.getUserId().equals(sender.getId())) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "본인 계좌가 아닙니다.");
            }
            
            return requestedAccountNumber;
        } else {
            // 기본 계좌 사용
            fintech2.easypay.account.service.UserAccountService userAccountService = 
                applicationContext.getBean(fintech2.easypay.account.service.UserAccountService.class);
            fintech2.easypay.account.entity.UserAccount primaryUserAccount = userAccountService.getPrimaryAccount(sender.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "기본 계좌를 찾을 수 없습니다."));
            
            return primaryUserAccount.getAccountNumber();
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
