package com.tayyar.address;

import static com.tayyar.address.AddressDtos.*;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('CUSTOMER')")
public class AddressService {
    private final AddressRepository addresses;
    private final AddressJournal journal;
    private final Clock clock;
    private final com.tayyar.delivery.ServiceabilityQuery serviceability;

    public AddressService(
            AddressRepository addresses,
            AddressJournal journal,
            Clock clock,
            com.tayyar.delivery.ServiceabilityQuery serviceability) {
        this.addresses = addresses;
        this.journal = journal;
        this.clock = clock;
        this.serviceability = serviceability;
    }

    public Page list(SessionPrincipal actor, Window window) {
        var rows =
                addresses.findByUserId(
                        actor.id(),
                        PageRequest.of(
                                window.page(),
                                window.size(),
                                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new Page(
                rows.map(CustomerAddress::view).getContent(),
                window.page(),
                window.size(),
                rows.getTotalElements());
    }

    public View details(SessionPrincipal actor, UUID id) {
        return owned(actor, id).view();
    }

    @Transactional
    public View create(SessionPrincipal actor, Profile input) {
        journal.lockUser(actor.id());
        long count = addresses.countByUserId(actor.id());
        if (count >= 100) throw AddressException.invalid("Saved address limit reached");
        return addresses
                .saveAndFlush(new CustomerAddress(actor.id(), input, count == 0, clock.instant()))
                .view();
    }

    @Transactional
    public View edit(SessionPrincipal actor, UUID id, Edit input) {
        journal.lockUser(actor.id());
        var address = owned(actor, id);
        address.touch(input.version(), clock.instant());
        address.profile(input.profile());
        addresses.flush();
        return address.view();
    }

    @Transactional
    public void delete(SessionPrincipal actor, UUID id, VersionInput input) {
        journal.lockUser(actor.id());
        var address = owned(actor, id);
        address.checkVersion(input.version());
        addresses.delete(address);
        addresses.flush();
    }

    @Transactional
    public View selectDefault(SessionPrincipal actor, UUID id, VersionInput input) {
        journal.lockUser(actor.id());
        var address = owned(actor, id);
        var now = clock.instant();
        address.touch(input.version(), now);
        journal.clearDefault(actor.id(), id, now);
        address.selectDefault();
        addresses.flush();
        return address.view();
    }

    private CustomerAddress owned(SessionPrincipal actor, UUID id) {
        return addresses.findByIdAndUserId(id, actor.id()).orElseThrow(AddressException::missing);
    }

    @Transactional
    public View selectZone(SessionPrincipal actor, UUID id, ZoneSelection input) {
        journal.lockUser(actor.id());
        var address = owned(actor, id);
        address.touch(input.version(), clock.instant());
        if (input.deliveryZoneId() != null)
            serviceability.requireActiveZone(input.deliveryZoneId());
        address.selectZone(input.deliveryZoneId());
        addresses.flush();
        return address.view();
    }
}
