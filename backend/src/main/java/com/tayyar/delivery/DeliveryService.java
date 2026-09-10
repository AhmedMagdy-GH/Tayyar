package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.branch.BranchService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
public class DeliveryService {
    private final CityRepository cities;
    private final DeliveryZoneRepository zones;
    private final BranchDeliveryZoneRepository rules;
    private final BranchService branches;
    private final Clock clock;

    public DeliveryService(
            CityRepository cities,
            DeliveryZoneRepository zones,
            BranchDeliveryZoneRepository rules,
            BranchService branches,
            Clock clock) {
        this.cities = cities;
        this.zones = zones;
        this.rules = rules;
        this.branches = branches;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','RESTAURANT_OWNER','ADMIN')")
    public Page<GeographyView> cities(Window window) {
        var rows = cities.findAll(page(window));
        return new Page<>(
                rows.map(City::view).getContent(),
                window.page(),
                window.size(),
                rows.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','RESTAURANT_OWNER','ADMIN')")
    public Page<GeographyView> zones(UUID city, Window window) {
        if (!cities.existsById(city)) throw DeliveryException.missing();
        var rows = zones.findByCityId(city, page(window));
        return new Page<>(
                rows.map(DeliveryZone::view).getContent(),
                window.page(),
                window.size(),
                rows.getTotalElements());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GeographyView createCity(GeographyInput input) {
        try {
            return cities.saveAndFlush(new City(input, clock.instant())).view();
        } catch (DataIntegrityViolationException ex) {
            throw DeliveryException.conflict();
        }
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','RESTAURANT_OWNER','ADMIN')")
    public GeographyView city(UUID id) {
        return cities.findById(id).orElseThrow(DeliveryException::missing).view();
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','RESTAURANT_OWNER','ADMIN')")
    public GeographyView zone(UUID id) {
        return zones.findById(id).orElseThrow(DeliveryException::missing).view();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GeographyView editCity(UUID id, GeographyEdit input) {
        var row = cities.lock(id).orElseThrow(DeliveryException::missing);
        row.touch(input.version(), clock.instant());
        row.edit(input.profile());
        try {
            cities.flush();
        } catch (DataIntegrityViolationException ex) {
            throw DeliveryException.conflict();
        }
        return row.view();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GeographyView createZone(ZoneInput input) {
        if (!cities.existsById(input.cityId())) throw DeliveryException.missing();
        try {
            return zones.saveAndFlush(new DeliveryZone(input, clock.instant())).view();
        } catch (DataIntegrityViolationException ex) {
            throw DeliveryException.conflict();
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GeographyView editZone(UUID id, GeographyEdit input) {
        var row = zones.lock(id).orElseThrow(DeliveryException::missing);
        row.touch(input.version(), clock.instant());
        row.edit(input.profile());
        try {
            zones.flush();
        } catch (DataIntegrityViolationException ex) {
            throw DeliveryException.conflict();
        }
        return row.view();
    }

    public Page<RuleView> rules(
            UUID restaurant, UUID branch, SessionPrincipal actor, Window window) {
        branches.details(restaurant, branch, actor);
        var rows = rules.findByBranchId(branch, page(window));
        return new Page<>(
                rows.map(BranchDeliveryZone::view).getContent(),
                window.page(),
                window.size(),
                rows.getTotalElements());
    }

    @Transactional
    public RuleView createRule(
            UUID restaurant, UUID branch, UUID zone, SessionPrincipal actor, RuleInput input) {
        branches.details(restaurant, branch, actor);
        if (!zones.existsById(zone)) throw DeliveryException.missing();
        try {
            return rules.saveAndFlush(new BranchDeliveryZone(branch, zone, input, clock.instant()))
                    .view();
        } catch (DataIntegrityViolationException ex) {
            throw DeliveryException.conflict();
        }
    }

    public RuleView rule(UUID restaurant, UUID branch, UUID zone, SessionPrincipal actor) {
        branches.details(restaurant, branch, actor);
        return rules.findByBranchIdAndDeliveryZoneId(branch, zone)
                .orElseThrow(DeliveryException::missing)
                .view();
    }

    @Transactional
    public RuleView editRule(
            UUID restaurant, UUID branch, UUID zone, SessionPrincipal actor, RuleEdit input) {
        branches.details(restaurant, branch, actor);
        var row = rules.lock(branch, zone).orElseThrow(DeliveryException::missing);
        row.touch(input.version(), clock.instant());
        row.edit(input.rule());
        rules.flush();
        return row.view();
    }

    private PageRequest page(Window window) {
        return PageRequest.of(window.page(), window.size(), Sort.by("id"));
    }
}
