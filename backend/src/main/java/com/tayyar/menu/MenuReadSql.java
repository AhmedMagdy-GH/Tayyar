package com.tayyar.menu;

/** Shared effective-value expressions. Aliases: menu m, category c, item i, override o. */
public final class MenuReadSql {
    private MenuReadSql() {}

    public static final String PRICE = "COALESCE(o.price,i.base_price)";
    public static final String AVAILABLE =
            "(m.active AND c.active AND i.active AND COALESCE(o.available,i.available))";
}
