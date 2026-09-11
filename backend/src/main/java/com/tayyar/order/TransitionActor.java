package com.tayyar.order;

import com.tayyar.auth.SessionPrincipal;

import java.util.UUID;

public record TransitionActor(Kind kind, UUID userId) {
    public enum Kind {
        USER,
        SYSTEM,
        PROVIDER
    }

    public TransitionActor {
        if ((kind == Kind.USER) != (userId != null))
            throw new IllegalArgumentException("User actors require a session identity");
    }

    public static TransitionActor user(SessionPrincipal principal) {
        return new TransitionActor(Kind.USER, principal.id());
    }

    public static TransitionActor system() {
        return new TransitionActor(Kind.SYSTEM, null);
    }

    public static TransitionActor provider() {
        return new TransitionActor(Kind.PROVIDER, null);
    }
}
