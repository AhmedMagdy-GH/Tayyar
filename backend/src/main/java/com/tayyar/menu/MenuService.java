package com.tayyar.menu;

import static com.tayyar.menu.MenuDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.branch.BranchService;
import com.tayyar.restaurant.RestaurantService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
public class MenuService {
    private final MenuRepository menus;
    private final MenuJournal journal;
    private final RestaurantService restaurants;
    private final BranchService branches;
    private final Clock clock;

    public MenuService(
            MenuRepository menus,
            MenuJournal journal,
            RestaurantService restaurants,
            BranchService branches,
            Clock clock) {
        this.menus = menus;
        this.journal = journal;
        this.restaurants = restaurants;
        this.branches = branches;
        this.clock = clock;
    }

    @Transactional
    public MenuView create(UUID restaurant, SessionPrincipal actor, CreateMenu input) {
        restaurants.details(restaurant, actor);
        journal.createMenu(UUID.randomUUID(), restaurant, input.name(), clock.instant());
        return menus.findByRestaurantId(restaurant).orElseThrow(MenuException::missing).view();
    }

    public MenuView details(UUID restaurant, SessionPrincipal actor) {
        return read(restaurant, actor).view();
    }

    @Transactional
    public Mutation edit(UUID restaurant, SessionPrincipal actor, EditMenu input) {
        var menu = lock(restaurant, actor, input.version());
        menu.edit(input);
        return done(menu, menu.id());
    }

    public Page<CategoryView> categories(UUID restaurant, SessionPrincipal actor, Window window) {
        return journal.categories(read(restaurant, actor), window);
    }

    public Snapshot<CategoryView> category(UUID restaurant, UUID category, SessionPrincipal actor) {
        var menu = read(restaurant, actor);
        return new Snapshot<>(journal.category(restaurant, category), menu.version());
    }

    @Transactional
    public Mutation createCategory(UUID restaurant, SessionPrincipal actor, CreateCategory input) {
        var menu = lock(restaurant, actor, input.version());
        return done(menu, journal.createCategory(menu, input, clock.instant()));
    }

    @Transactional
    public Mutation editCategory(
            UUID restaurant, UUID category, SessionPrincipal actor, EditCategory input) {
        var menu = lock(restaurant, actor, input.version());
        journal.editCategory(restaurant, category, input, clock.instant());
        return done(menu, category);
    }

    @Transactional
    public Mutation reorderCategories(UUID restaurant, SessionPrincipal actor, Reorder input) {
        var menu = lock(restaurant, actor, input.version());
        journal.reorderCategories(menu.id(), input.ids(), clock.instant());
        return done(menu, menu.id());
    }

    public Page<ItemView> items(
            UUID restaurant, UUID category, SessionPrincipal actor, Window window) {
        return journal.items(read(restaurant, actor), category, window);
    }

    public Snapshot<ItemView> item(UUID restaurant, UUID item, SessionPrincipal actor) {
        var menu = read(restaurant, actor);
        return new Snapshot<>(journal.item(restaurant, item), menu.version());
    }

    @Transactional
    public Mutation createItem(
            UUID restaurant, UUID category, SessionPrincipal actor, CreateItem input) {
        var menu = lock(restaurant, actor, input.version());
        return done(menu, journal.createItem(menu, category, input, clock.instant()));
    }

    @Transactional
    public Mutation editItem(UUID restaurant, UUID item, SessionPrincipal actor, EditItem input) {
        var menu = lock(restaurant, actor, input.version());
        journal.editItem(restaurant, item, input, clock.instant());
        return done(menu, item);
    }

    @Transactional
    public Mutation reorderItems(
            UUID restaurant, UUID category, SessionPrincipal actor, Reorder input) {
        var menu = lock(restaurant, actor, input.version());
        journal.category(restaurant, category);
        journal.reorderItems(category, input.ids(), clock.instant());
        return done(menu, category);
    }

    public Page<EffectiveItem> effective(
            UUID restaurant, UUID branch, SessionPrincipal actor, Window window) {
        branches.details(restaurant, branch, actor);
        return journal.effective(read(restaurant, actor), branch, window);
    }

    public Snapshot<EffectiveItem> effectiveItem(
            UUID restaurant, UUID branch, UUID item, SessionPrincipal actor) {
        branches.details(restaurant, branch, actor);
        var menu = read(restaurant, actor);
        return new Snapshot<>(journal.effectiveItem(menu, branch, item), menu.version());
    }

    @Transactional
    public Mutation override(
            UUID restaurant, UUID branch, UUID item, SessionPrincipal actor, OverrideInput input) {
        branches.details(restaurant, branch, actor);
        var menu = lock(restaurant, actor, input.version());
        journal.override(restaurant, branch, item, input, clock.instant());
        return done(menu, item);
    }

    @Transactional
    public Mutation removeOverride(
            UUID restaurant, UUID branch, UUID item, SessionPrincipal actor, VersionInput input) {
        branches.details(restaurant, branch, actor);
        var menu = lock(restaurant, actor, input.version());
        journal.removeOverride(restaurant, branch, item);
        return done(menu, item);
    }

    private Menu read(UUID restaurant, SessionPrincipal actor) {
        restaurants.details(restaurant, actor);
        return menus.findByRestaurantId(restaurant).orElseThrow(MenuException::missing);
    }

    private Menu lock(UUID restaurant, SessionPrincipal actor, long version) {
        restaurants.details(restaurant, actor);
        var menu = menus.lock(restaurant).orElseThrow(MenuException::missing);
        menu.touch(version, clock.instant());
        return menu;
    }

    private Mutation done(Menu menu, UUID resource) {
        menus.flush();
        return new Mutation(resource, menu.version());
    }
}
