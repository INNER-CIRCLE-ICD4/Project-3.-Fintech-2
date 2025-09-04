package fintech2.easypay.transfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.auth.dto.UserPrincipal;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.auth.service.PinService;
import fintech2.easypay.common.ApiResponse;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.transfer.action.ActionResult;
import fintech2.easypay.transfer.action.TransferActionProcessor;
import fintech2.easypay.transfer.command.InternalTransferCommand;
import fintech2.easypay.transfer.command.SecureTransferCommand;
import fintech2.easypay.transfer.dto.RecentTransferResponse;
import fintech2.easypay.transfer.dto.SecureTransferRequest;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.repository.TransferRepository;
import fintech2.easypay.transfer.service.TransferService;

import java.util.UUID;
import org.springframework.context.ApplicationContext;

/**
 * 송금 관리 컴트롤러
 * 사용자 간 송금 및 거래 내역 조회 기능을 제공
 * JWT 토큰 기반 인증을 통한 보안 및 사용자 식별
 */
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "송금 관리", description = "송금 및 거래 내역 관리 API")
public class TransferController {
    
    private final TransferService transferService;
    private final TransferActionProcessor actionProcessor;
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PinService pinService;
    private final ApplicationContext applicationContext;
    
    /**
     * 송금 처리 API (기존 - PIN 검증 없음)
     * 인증된 사용자가 다른 사용자에게 송금
     * @param userDetails 인증된 사용자 정보
     * @param request 송금 요청 정보
     * @return 송금 처리 결과
     */
    @PostMapping
    public ApiResponse<TransferResponse> transfer(
        @AuthenticationPrincipal UserPrincipal userDetails,
        @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.transfer(userDetails.getUsername(), request);
        return ApiResponse.success("송금이 완료되었습니다.", response);
    }

    /**
     * 보안 송금 처리 API (PIN 검증 포함) - 리팩토링됨
     * PIN 인증이 완료된 후 송금 처리
     * @param userDetails 인증된 사용자 정보
     * @param request PIN 세션 토큰이 포함된 보안 송금 요청
     * @return 송금 처리 결과
     */
    @PostMapping("/secure")
    @Operation(summary = "보안 송금", description = "PIN 인증을 통한 보안 송금 처리")
    public ApiResponse<TransferResponse> secureTransfer(
        @AuthenticationPrincipal UserPrincipal userDetails,
        @Valid @RequestBody SecureTransferRequest request) {
        
        // 송금자 조회
        User sender = userRepository.findByPhoneNumber(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 수신자 계좌 조회
        Account receiverAccount = accountRepository.findByAccountNumber(request.getReceiverAccountNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
        
        User receiver = userRepository.findById(receiverAccount.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 송금자 계좌 결정
        String senderAccountNumber = determineSenderAccount(sender, request.getSenderAccountNumber());
        
        // 거래 ID 생성
        String transactionId = generateTransactionId();
        
        // 내부 송금인지 외부 송금인지 판단 (현재는 내부 송금만 지원)
        boolean isExternal = false; // 향후 확장 시 로직 추가
        
        // 보안 송금 커맨드 생성
        SecureTransferCommand command = new SecureTransferCommand(
                sender.getId(),
                receiver.getId(),
                senderAccountNumber,
                request.getReceiverAccountNumber(),
                request.getAmount(),
                request.getMemo(),
                transactionId,
                request.getPinSessionToken(),
                isExternal,
                null // 내부 송금이므로 null
        );
        
        // Action Processor를 통한 보안 송금 처리
        ActionResult result = actionProcessor.process(command);
        
        // 결과에 따른 응답 생성
        if (result.isSuccess() || result.isPending()) {
            Transfer transfer = transferRepository.findByTransactionId(transactionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            
            TransferResponse response = TransferResponse.from(transfer);
            return ApiResponse.success("PIN 인증을 통한 송금이 완료되었습니다.", response);
        } else {
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, result.getMessage());
        }
    }
    
    /**
     * 송금자 계좌번호 결정 (헬퍼 메서드)
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
    
    /**
     * 고유한 거래 ID 생성
     */
    private String generateTransactionId() {
        String transactionId;
        do {
            transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (transferRepository.existsByTransactionId(transactionId));
        
        return transactionId;
    }
    
    /**
     * 거래 조회 API
     * 거래 ID로 특정 거래의 상세 정보를 조회
     * @param transactionId 조회할 거래 ID
     * @return 거래 상세 정보
     */
    @GetMapping("/{transactionId}")
    @Operation(summary = "거래 조회", description = "거래 ID로 특정 거래 조회")
    public ApiResponse<TransferResponse> getTransfer(@PathVariable String transactionId) {
        TransferResponse response = transferService.getTransfer(transactionId);
        return ApiResponse.success(response);
    }
    
    @GetMapping("/history")
    public ApiResponse<Page<TransferResponse>> getTransferHistory(
        @AuthenticationPrincipal UserPrincipal userDetails,
        Pageable pageable) {
        Page<TransferResponse> response = 
            transferService.getTransferHistory(userDetails.getUsername(), pageable);
        return ApiResponse.success(response);
    }
    
    @GetMapping("/sent")
    @Operation(summary = "송금 내역 조회", description = "내가 송금한 내역 조회")
    public ApiResponse<Page<TransferResponse>> getSentTransfers(
        @AuthenticationPrincipal UserPrincipal userDetails,
        Pageable pageable) {
        Page<TransferResponse> response = 
            transferService.getSentTransfers(userDetails.getUsername(), pageable);
        return ApiResponse.success(response);
    }
    
    @GetMapping("/received")
    @Operation(summary = "입금 내역 조회", description = "내가 받은 입금 내역 조회")
    public ApiResponse<Page<TransferResponse>> getReceivedTransfers(
        @AuthenticationPrincipal UserPrincipal userDetails,
        Pageable pageable) {
        Page<TransferResponse> response = 
            transferService.getReceivedTransfers(userDetails.getUsername(), pageable);
        return ApiResponse.success(response);
    }
    
    @GetMapping("/recent")
    @Operation(summary = "최근 송금 대상 조회", description = "최근 송금한 사람들의 목록 조회 (중복 제거)")
    public ApiResponse<Page<RecentTransferResponse>> getRecentTransfers(
        @AuthenticationPrincipal UserPrincipal userDetails,
        Pageable pageable) {
        Page<RecentTransferResponse> response = 
            transferService.getRecentTransfers(userDetails.getUsername(), pageable);
        return ApiResponse.success(response);
    }
}
