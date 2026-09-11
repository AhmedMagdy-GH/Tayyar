package com.tayyar.operations;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class CheckoutMetrics {
    private final Counter successes;
    private final Counter conflicts;

    public CheckoutMetrics(MeterRegistry registry) {
        successes = Counter.builder("tayyar.checkout.outcomes")
                .description("Checkout outcomes")
                .tag("outcome", "success")
                .register(registry);
        conflicts = Counter.builder("tayyar.checkout.outcomes")
                .description("Checkout outcomes")
                .tag("outcome", "conflict")
                .register(registry);
    }

    public void success() { successes.increment(); }
    public void conflict() { conflicts.increment(); }
}
