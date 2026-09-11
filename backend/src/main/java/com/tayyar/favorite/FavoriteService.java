package com.tayyar.favorite;

import static com.tayyar.favorite.FavoriteDtos.Page;
import com.tayyar.auth.SessionPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('CUSTOMER')")
@Transactional(readOnly = true)
class FavoriteService {
    private final FavoriteStore store;
    private final Clock clock;
    FavoriteService(FavoriteStore store, Clock clock) { this.store = store; this.clock = clock; }

    Page list(SessionPrincipal actor, FavoriteParameters p) { return store.list(actor.id(), p); }
    @Transactional void add(SessionPrincipal actor, UUID restaurant) { store.add(actor.id(), restaurant, clock.instant()); }
    @Transactional void remove(SessionPrincipal actor, UUID restaurant) { store.remove(actor.id(), restaurant); }
}
