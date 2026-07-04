package com.seatflow.payment.service;

import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.service.command.ProcessPaymentCommand;

import java.math.BigDecimal;

public interface PaymentService {

    /** 결제 본체(트랜잭션). 멱등 차단은 PaymentFacade가 앞단에서 수행한다. */
    Payment executePayment(ProcessPaymentCommand command);

    Payment getPayment(Long id);

    void executeRefund(Long sagaId, Long reservationId, BigDecimal refundAmount);
}