package com.seatflow.payment.service;

import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.service.command.ProcessPaymentCommand;

public interface PaymentService {
    Payment processPayment(ProcessPaymentCommand command);
    Payment getPayment(Long id);
}