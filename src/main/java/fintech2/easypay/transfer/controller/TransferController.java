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

import fintech2.easypay.auth.CustomUserDetails;
import fintech2.easypay.common.ApiResponse;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import fintech2.easypay.transfer.service.TransferService;

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
    
    /**
     * 송금 처리 API
     * 인증된 사용자가 다른 사용자에게 송금
     * @param userDetails 인증된 사용자 정보
     * @param request 송금 요청 정보
     * @return 송금 처리 결과
     */
    @PostMapping
    public ApiResponse<TransferResponse> transfer(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.transfer(userDetails.getUsername(), request);
        return ApiResponse.success("송금이 완료되었습니다.", response);
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
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Pageable pageable) {
        Page<TransferResponse> response = 
            transferService.getTransferHistory(userDetails.getUsername(), pageable);
        return ApiResponse.success(response);
    }
    
    @GetMapping("/sent")
    @Operation(summary = "송금 내역 조회", description = "내가 송금한 내역 조회")
    public ApiResponse<Page<TransferResponse>> getSentTransfers(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Pageable pageable) {
        Page<TransferResponse> response = 
            transferService.getSentTransfers(userDetails.getUsername(), pageable);
        return ApiResponse.success(response);
    }
    
    @GetMapping("/received")
    @Operation(summary = "입금 내역 조회", description = "내가 받은 입금 내역 조회")
    public ApiResponse<Page<TransferResponse>> getReceivedTransfers(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Pageable pageable) {
        Page<TransferResponse> response = 
            transferService.getReceivedTransfers(userDetails.getUsername(), pageable);
        return ApiResponse.success(response);
    }
}
