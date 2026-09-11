package com.tayyar.payment;

import static com.tayyar.payment.PaymentDtos.*;

import com.tayyar.order.TransitionActor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** Internal persistence/state foundation; it performs no provider or card operation. */
@Service
@Transactional(readOnly = true)
public class PaymentService {
    private final PaymentStore store;
    private final Clock clock;

    public PaymentService(PaymentStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public View create(Create input, TransitionActor actor) {
        if (input == null || input.orderId() == null || input.method() == null || actor == null)
            throw PaymentException.invalid("Payment order, method and actor are required");
        String provider = text(input.provider(), 100);
        String reference = text(input.providerReference(), 200);
        if ((provider == null) != (reference == null))
            throw PaymentException.invalid(
                    "Provider and provider reference must be supplied together");
        if (input.method() == PaymentMethod.CASH && provider != null)
            throw PaymentException.invalid("Cash payments cannot have card-provider metadata");
        Create normalized = new Create(input.orderId(), input.method(), provider, reference);
        var terms = store.terms(input.orderId());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            store.create(id, normalized, terms, now);
            store.history(id, null, PaymentStatus.PENDING, actor, null, now);
        } catch (DataIntegrityViolationException exception) {
            throw PaymentException.invalid("Payment relationships or values are invalid");
        }
        return store.details(id);
    }

    @Transactional
    public View transition(
            UUID id,
            long expectedVersion,
            PaymentStatus target,
            TransitionActor actor,
            String reason) {
        if (id == null || target == null || actor == null)
            throw PaymentException.invalid("Transition identity, status and actor are required");
        var state = store.state(id);
        if (state.version() != expectedVersion) throw PaymentException.conflict();
        PaymentRules.transition(state.method(), state.status(), target, reason);
        String normalizedReason = PaymentRules.optionalReason(reason);
        Instant now = clock.instant();
        store.transition(state, target, now);
        store.history(id, state.status(), target, actor, normalizedReason, now);
        return store.details(id);
    }

    public View details(UUID id) {
        return store.details(id);
    }

    public List<History> history(UUID id) {
        store.state(id);
        return store.history(id);
    }

    private String text(String value, int maximum) {
        if (value == null) return null;
        String result = value.strip();
        if (result.isEmpty() || result.length() > maximum)
            throw PaymentException.invalid("Provider metadata is invalid");
        return result;
    }
}
