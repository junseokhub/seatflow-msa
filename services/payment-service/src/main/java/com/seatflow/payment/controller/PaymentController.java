package com.seatflow.payment.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.payment.dto.PaymentRequest;
import com.seatflow.payment.dto.PaymentResponse;
import com.seatflow.payment.service.PaymentFacade;
import com.seatflow.payment.service.PaymentService;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;
    private final PaymentService paymentService;

    /**
     * 결제 요청. Idempotency-Key 헤더로 광클·재시도를 멱등 처리한다(1층).
     * 클라이언트는 결제 시도마다 고유 키를 보내며, 같은 키의 재요청은 차단된다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @RequestBody @Valid PaymentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                PaymentResponse.from(paymentService.getPayment(id))
        ));
    }
}