package com.tayyar.cart;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Shared configuration locks. Menu writers lock the menu parent (including absent override
 * insertion); schedule writers lock the branch parent.
 */
@Repository
public class CartValidationLocks {
    private final JdbcTemplate jdbc;

    public CartValidationLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(CartStore.LockedCart cart) {
        rows("SELECT id FROM restaurants WHERE id=? FOR SHARE", cart.restaurant());
        rows("SELECT id FROM branches WHERE id=? FOR SHARE", cart.branch());
        rows(
                "SELECT id FROM restaurant_menus WHERE restaurant_id=? ORDER BY id FOR SHARE",
                cart.restaurant());
        rows(
                "SELECT c.id FROM menu_categories c WHERE c.id IN (SELECT i.category_id FROM"
                    + " menu_items i JOIN cart_items ci ON ci.menu_item_id=i.id WHERE ci.cart_id=?)"
                    + " ORDER BY c.id FOR SHARE",
                cart.id());
        rows(
                "SELECT i.id FROM menu_items i JOIN cart_items ci ON ci.menu_item_id=i.id WHERE"
                    + " ci.cart_id=? ORDER BY i.id FOR SHARE OF i",
                cart.id());
        rows(
                "SELECT o.item_id FROM branch_menu_item_overrides o JOIN cart_items ci ON"
                    + " ci.menu_item_id=o.item_id WHERE ci.cart_id=? AND o.branch_id=? ORDER BY"
                    + " o.item_id FOR SHARE OF o",
                cart.id(),
                cart.branch());
        rows("SELECT id FROM cart_items WHERE cart_id=? ORDER BY id FOR UPDATE", cart.id());
    }

    private void rows(String sql, Object... values) {
        jdbc.query(sql, (row, n) -> row.getObject(1, UUID.class), values);
    }
}
