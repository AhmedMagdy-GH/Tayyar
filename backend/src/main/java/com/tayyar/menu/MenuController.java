package com.tayyar.menu;

import static com.tayyar.menu.MenuDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}")
public class MenuController {
    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    @PostMapping("/menu")
    public ResponseEntity<MenuView> create(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody CreateMenu input) {
        return ResponseEntity.created(URI.create("/api/v1/restaurants/" + restaurantId + "/menu"))
                .body(service.create(restaurantId, actor, input));
    }

    @GetMapping("/menu")
    public MenuView details(
            @PathVariable UUID restaurantId, @AuthenticationPrincipal SessionPrincipal actor) {
        return service.details(restaurantId, actor);
    }

    @PutMapping("/menu")
    public Mutation edit(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody EditMenu input) {
        return service.edit(restaurantId, actor, input);
    }

    @GetMapping("/menu/categories")
    public Page<CategoryView> categories(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.categories(restaurantId, actor, new Window(page, size));
    }

    @GetMapping("/menu/categories/{categoryId}")
    public Snapshot<CategoryView> category(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.category(restaurantId, categoryId, actor);
    }

    @PostMapping("/menu/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Mutation createCategory(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody CreateCategory input) {
        return service.createCategory(restaurantId, actor, input);
    }

    @PutMapping("/menu/categories/{categoryId}")
    public Mutation editCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody EditCategory input) {
        return service.editCategory(restaurantId, categoryId, actor, input);
    }

    @PutMapping("/menu/categories/order")
    public Mutation orderCategories(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Reorder input) {
        return service.reorderCategories(restaurantId, actor, input);
    }

    @GetMapping("/menu/categories/{categoryId}/items")
    public Page<ItemView> items(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.items(restaurantId, categoryId, actor, new Window(page, size));
    }

    @PostMapping("/menu/categories/{categoryId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Mutation createItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody CreateItem input) {
        return service.createItem(restaurantId, categoryId, actor, input);
    }

    @PutMapping("/menu/categories/{categoryId}/items/order")
    public Mutation orderItems(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Reorder input) {
        return service.reorderItems(restaurantId, categoryId, actor, input);
    }

    @GetMapping("/menu/items/{itemId}")
    public Snapshot<ItemView> item(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.item(restaurantId, itemId, actor);
    }

    @PutMapping("/menu/items/{itemId}")
    public Mutation editItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody EditItem input) {
        return service.editItem(restaurantId, itemId, actor, input);
    }

    @GetMapping("/branches/{branchId}/menu/items")
    public Page<EffectiveItem> effective(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.effective(restaurantId, branchId, actor, new Window(page, size));
    }

    @GetMapping("/branches/{branchId}/menu/items/{itemId}")
    public Snapshot<EffectiveItem> effectiveItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.effectiveItem(restaurantId, branchId, itemId, actor);
    }

    @PutMapping("/branches/{branchId}/menu/items/{itemId}/override")
    public Mutation override(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody OverrideInput input) {
        return service.override(restaurantId, branchId, itemId, actor, input);
    }

    @DeleteMapping("/branches/{branchId}/menu/items/{itemId}/override")
    public Mutation removeOverride(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody VersionInput input) {
        return service.removeOverride(restaurantId, branchId, itemId, actor, input);
    }
}
