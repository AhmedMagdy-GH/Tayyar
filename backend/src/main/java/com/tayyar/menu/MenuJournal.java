package com.tayyar.menu;

import static com.tayyar.menu.MenuDtos.*;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class MenuJournal {
    private final JdbcTemplate jdbc;

    public MenuJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createMenu(UUID id, UUID restaurant, String name, Instant now) {
        int rows =
                jdbc.update(
                        "INSERT INTO restaurant_menus(id,restaurant_id,name,created_at,updated_at)"
                            + " VALUES (?,?,?,?,?) ON CONFLICT (restaurant_id) DO NOTHING",
                        id,
                        restaurant,
                        name.strip(),
                        Timestamp.from(now),
                        Timestamp.from(now));
        if (rows == 0) throw MenuException.conflict();
    }

    public List<UUID> categoryIds(UUID menu) {
        return jdbc.queryForList(
                "SELECT id FROM menu_categories WHERE menu_id=? ORDER BY position LIMIT 101",
                UUID.class,
                menu);
    }

    public List<UUID> itemIds(UUID category) {
        return jdbc.queryForList(
                "SELECT id FROM menu_items WHERE category_id=? ORDER BY position LIMIT 501",
                UUID.class,
                category);
    }

    public UUID createCategory(Menu menu, CreateCategory input, Instant now) {
        var ids = categoryIds(menu.id());
        if (ids.size() >= 100) throw MenuException.invalid("Menu category limit reached");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " menu_categories(id,menu_id,restaurant_id,name,description,position,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?)",
                id,
                menu.id(),
                menu.restaurantId(),
                input.name().strip(),
                input.description(),
                ids.size(),
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    public UUID createItem(Menu menu, UUID category, CreateItem input, Instant now) {
        category(menu.restaurantId(), category);
        var ids = itemIds(category);
        if (ids.size() >= 500) throw MenuException.invalid("Category item limit reached");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " menu_items(id,category_id,restaurant_id,name,description,base_price,available,position,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?)",
                id,
                category,
                menu.restaurantId(),
                input.name().strip(),
                input.description(),
                input.basePrice(),
                input.available(),
                ids.size(),
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    private final RowMapper<CategoryView> categoryMapper =
            (r, n) ->
                    new CategoryView(
                            r.getObject("id", UUID.class),
                            r.getString("name"),
                            r.getString("description"),
                            r.getBoolean("active"),
                            r.getInt("position"));
    private final RowMapper<ItemView> itemMapper =
            (r, n) ->
                    new ItemView(
                            r.getObject("id", UUID.class),
                            r.getObject("category_id", UUID.class),
                            r.getString("name"),
                            r.getString("description"),
                            r.getBigDecimal("base_price"),
                            r.getBoolean("active"),
                            r.getBoolean("available"),
                            r.getInt("position"));

    public CategoryView category(UUID restaurant, UUID id) {
        var rows =
                jdbc.query(
                        "SELECT id,name,description,active,position FROM menu_categories WHERE"
                            + " restaurant_id=? AND id=?",
                        categoryMapper,
                        restaurant,
                        id);
        if (rows.isEmpty()) throw MenuException.missing();
        return rows.getFirst();
    }

    public ItemView item(UUID restaurant, UUID id) {
        var rows =
                jdbc.query(
                        "SELECT"
                            + " id,category_id,name,description,base_price,active,available,position"
                            + " FROM menu_items WHERE restaurant_id=? AND id=?",
                        itemMapper,
                        restaurant,
                        id);
        if (rows.isEmpty()) throw MenuException.missing();
        return rows.getFirst();
    }

    public void editCategory(UUID restaurant, UUID id, EditCategory input, Instant now) {
        if (jdbc.update(
                        "UPDATE menu_categories SET name=?,description=?,active=?,updated_at=?"
                            + " WHERE restaurant_id=? AND id=?",
                        input.name().strip(),
                        input.description(),
                        input.active(),
                        Timestamp.from(now),
                        restaurant,
                        id)
                == 0) throw MenuException.missing();
    }

    public void editItem(UUID restaurant, UUID id, EditItem input, Instant now) {
        if (jdbc.update(
                        "UPDATE menu_items SET"
                            + " name=?,description=?,base_price=?,active=?,available=?,updated_at=?"
                            + " WHERE restaurant_id=? AND id=?",
                        input.name().strip(),
                        input.description(),
                        input.basePrice(),
                        input.active(),
                        input.available(),
                        Timestamp.from(now),
                        restaurant,
                        id)
                == 0) throw MenuException.missing();
    }

    public Page<CategoryView> categories(Menu menu, Window window) {
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM menu_categories WHERE menu_id=?",
                        Long.class,
                        menu.id());
        var rows =
                jdbc.query(
                        "SELECT id,name,description,active,position FROM menu_categories WHERE"
                            + " menu_id=? ORDER BY position,id LIMIT ? OFFSET ?",
                        categoryMapper,
                        menu.id(),
                        window.size(),
                        window.offset());
        return new Page<>(rows, window.page(), window.size(), count, menu.version());
    }

    public Page<ItemView> items(Menu menu, UUID category, Window window) {
        category(menu.restaurantId(), category);
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM menu_items WHERE category_id=?",
                        Long.class,
                        category);
        var rows =
                jdbc.query(
                        "SELECT"
                            + " id,category_id,name,description,base_price,active,available,position"
                            + " FROM menu_items WHERE category_id=? ORDER BY position,id LIMIT ?"
                            + " OFFSET ?",
                        itemMapper,
                        category,
                        window.size(),
                        window.offset());
        return new Page<>(rows, window.page(), window.size(), count, menu.version());
    }

    public void reorderCategories(UUID menu, List<UUID> ids, Instant now) {
        MenuRules.reorder(categoryIds(menu), ids);
        reorder("menu_categories", ids, now);
    }

    public void reorderItems(UUID category, List<UUID> ids, Instant now) {
        MenuRules.reorder(itemIds(category), ids);
        reorder("menu_items", ids, now);
    }

    private void reorder(String table, List<UUID> ids, Instant now) {
        // Table is selected only by the two internal methods above, never request input.
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++)
            batch.add(new Object[] {i, Timestamp.from(now), ids.get(i)});
        if (!batch.isEmpty())
            jdbc.batchUpdate("UPDATE " + table + " SET position=?,updated_at=? WHERE id=?", batch);
    }

    public void override(
            UUID restaurant, UUID branch, UUID item, OverrideInput input, Instant now) {
        item(restaurant, item);
        if (input.available() == null && input.price() == null)
            throw MenuException.invalid("Supply an override value or remove the override");
        jdbc.update(
                "INSERT INTO"
                    + " branch_menu_item_overrides(branch_id,item_id,restaurant_id,available,price,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?) ON CONFLICT (branch_id,item_id) DO UPDATE SET"
                    + " available=EXCLUDED.available,price=EXCLUDED.price,updated_at=EXCLUDED.updated_at",
                branch,
                item,
                restaurant,
                input.available(),
                input.price(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public void removeOverride(UUID restaurant, UUID branch, UUID item) {
        item(restaurant, item);
        if (jdbc.update(
                        "DELETE FROM branch_menu_item_overrides WHERE branch_id=? AND item_id=? AND"
                            + " restaurant_id=?",
                        branch,
                        item,
                        restaurant)
                == 0) throw MenuException.missing();
    }

    private static final String EFFECTIVE_FROM =
            " FROM menu_items i JOIN menu_categories c ON c.id=i.category_id JOIN restaurant_menus"
                + " m ON m.id=c.menu_id LEFT JOIN branch_menu_item_overrides o ON o.item_id=i.id"
                + " AND o.branch_id=? WHERE i.restaurant_id=?";
    private static final String EFFECTIVE_SELECT =
            "SELECT i.id,i.category_id,i.name,i.description,i.base_price,o.price AS"
                + " price_override,o.available AS"
                + " availability_override,COALESCE(o.price,i.base_price) AS"
                + " effective_price,(m.active AND c.active AND i.active AND"
                + " COALESCE(o.available,i.available)) AS effective_available,m.currency";
    private final RowMapper<EffectiveItem> effectiveMapper =
            (r, n) ->
                    new EffectiveItem(
                            r.getObject("id", UUID.class),
                            r.getObject("category_id", UUID.class),
                            r.getString("name"),
                            r.getString("description"),
                            r.getBigDecimal("base_price"),
                            r.getBigDecimal("price_override"),
                            r.getObject("availability_override", Boolean.class),
                            r.getBigDecimal("effective_price"),
                            r.getBoolean("effective_available"),
                            r.getString("currency"));

    public Page<EffectiveItem> effective(Menu menu, UUID branch, Window window) {
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM menu_items WHERE restaurant_id=?",
                        Long.class,
                        menu.restaurantId());
        var rows =
                jdbc.query(
                        EFFECTIVE_SELECT
                                + EFFECTIVE_FROM
                                + " ORDER BY c.position,i.position,i.id LIMIT ? OFFSET ?",
                        effectiveMapper,
                        branch,
                        menu.restaurantId(),
                        window.size(),
                        window.offset());
        return new Page<>(rows, window.page(), window.size(), count, menu.version());
    }

    public EffectiveItem effectiveItem(Menu menu, UUID branch, UUID item) {
        var rows =
                jdbc.query(
                        EFFECTIVE_SELECT + EFFECTIVE_FROM + " AND i.id=?",
                        effectiveMapper,
                        branch,
                        menu.restaurantId(),
                        item);
        if (rows.isEmpty()) throw MenuException.missing();
        return rows.getFirst();
    }
}
