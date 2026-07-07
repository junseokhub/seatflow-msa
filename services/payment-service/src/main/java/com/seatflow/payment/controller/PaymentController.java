package com.seatflow.payment.controller;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.response.ApiResponse;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.dto.PaymentRequest;
import com.seatflow.payment.dto.PaymentResponse;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.service.PaymentFacade;
import com.seatflow.payment.service.PaymentService;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;
    private final PaymentService paymentService;

    /**
     * 결제 요청. Idempotency-Key 헤더로 광클·재시도를 멱등 처리한다(1층).
     * userId는 더 이상 클라이언트가 보내는 값이 아니라, 검증된 토큰의 주체다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @RequestBody @Valid PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.ok(
                PaymentResponse.from(paymentFacade.pay(
                        idempotencyKey,
                        new ProcessPaymentCommand(
                                request.reservationId(),
                                userId,
                                request.amount(),
                                request.paymentMethod()
                        )
                ))
        ));
    }

    /**
     * 결제 조회. 본인 결제만 볼 수 있다 — id만 알면 남의 결제 내역(금액, 결제수단)을
     * 볼 수 있는 걸 막는다.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable Long id,
            Authentication authentication) {
        Payment payment = paymentService.getPayment(id);
        if (!payment.getUserId().equals(authentication.getName())) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_NOT_OWNED.getStatus().value(),
                    PaymentErrorCode.PAYMENT_NOT_OWNED.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok(PaymentResponse.from(payment)));
    }
}