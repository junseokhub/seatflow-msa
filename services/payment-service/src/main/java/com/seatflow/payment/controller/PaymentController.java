package com.seatflow.payment.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.payment.dto.PaymentRequest;
import com.seatflow.payment.dto.PaymentResponse;
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

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @RequestBody @Valid PaymentRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                PaymentResponse.from(paymentService.processPayment(
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