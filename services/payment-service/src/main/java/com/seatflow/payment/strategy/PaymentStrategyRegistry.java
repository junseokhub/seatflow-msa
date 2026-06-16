package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PaymentStrategyRegistry {

    private final Map<PaymentMethod, PaymentStrategy> registry;

    public PaymentStrategyRegistry(List<PaymentStrategy> strategies) {
        this.registry = strategies.stream()
                .collect(Collectors.toMap(PaymentStrategy::supportedMethod, s -> s));
    }

    public PaymentStrategy get(PaymentMethod method) {
        PaymentStrategy strategy = registry.get(method);
        if (strategy == null) {
            throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + method);
        }
        return strategy;
    }
}