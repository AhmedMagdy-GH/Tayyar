package com.tayyar.promotion;

import static com.tayyar.promotion.PromotionDtos.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class PromotionStore {
    private final JdbcTemplate jdbc;

    public PromotionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(UUID id, UUID restaurant, String code, Create input,
            PromotionRules.Values values, Instant now) {
        try {
            jdbc.update("""
INSERT INTO promotions(id,restaurant_id,code,name,description,discount_type,percentage_value,
 fixed_amount,minimum_merchandise_subtotal,maximum_discount,starts_at,ends_at,active,
 total_usage_limit,per_customer_usage_limit,created_at,updated_at)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""", id, restaurant, code, input.name().strip(), optional(input.description()),
                    input.discountType().name(), values.percentage(), values.fixed(), values.minimum(),
                    values.maximum(), timestamp(input.startsAt()), timestamp(input.endsAt()), input.active(),
                    input.totalUsageLimit(), input.perCustomerUsageLimit(), Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException ex) {
            throw PromotionException.conflict("PROMOTION_CODE_EXISTS",
                    "A promotion with this normalized code already exists for the restaurant");
        }
    }

    public boolean update(UUID id, UUID restaurant, String code, Update input,
            PromotionRules.Values values, Instant now) {
        try {
            return jdbc.update("""
UPDATE promotions SET code=?,name=?,description=?,discount_type=?,percentage_value=?,fixed_amount=?,
 minimum_merchandise_subtotal=?,maximum_discount=?,starts_at=?,ends_at=?,active=?,total_usage_limit=?,
 per_customer_usage_limit=?,version=version+1,updated_at=?
WHERE id=? AND restaurant_id=? AND version=?
""", code, input.name().strip(), optional(input.description()), input.discountType().name(),
                    values.percentage(), values.fixed(), values.minimum(), values.maximum(),
                    timestamp(input.startsAt()), timestamp(input.endsAt()), input.active(),
                    input.totalUsageLimit(), input.perCustomerUsageLimit(), Timestamp.from(now), id,
                    restaurant, input.version()) == 1;
        } catch (DataIntegrityViolationException ex) {
            throw PromotionException.conflict("PROMOTION_CODE_EXISTS",
                    "A promotion with this normalized code already exists for the restaurant");
        }
    }

    public Optional<View> get(UUID restaurant, UUID id) {
        return jdbc.query("SELECT * FROM promotions WHERE restaurant_id=? AND id=?", this::view,
                restaurant, id).stream().findFirst();
    }

    public Page list(UUID restaurant, int page, int size) {
        long total = jdbc.queryForObject("SELECT count(*) FROM promotions WHERE restaurant_id=?",
                Long.class, restaurant);
        var items = jdbc.query("""
SELECT * FROM promotions WHERE restaurant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
""", this::view, restaurant, size, page * size);
        return new Page(items, page, size, total);
    }

    public Optional<Locked> lockByCode(UUID restaurant, String code) {
        return jdbc.query("""
SELECT * FROM promotions WHERE restaurant_id=? AND code=? FOR UPDATE
""", (r, n) -> new Locked(
                r.getObject("id", UUID.class), r.getObject("restaurant_id", UUID.class),
                r.getString("code"), r.getString("name"), DiscountType.valueOf(r.getString("discount_type")),
                r.getBigDecimal("percentage_value"), r.getBigDecimal("fixed_amount"),
                r.getBigDecimal("minimum_merchandise_subtotal"), r.getBigDecimal("maximum_discount"),
                instant(r.getTimestamp("starts_at")), instant(r.getTimestamp("ends_at")),
                r.getBoolean("active"), nullableLong(r, "total_usage_limit"),
                nullableLong(r, "per_customer_usage_limit")), restaurant, code).stream().findFirst();
    }

    public long totalUses(UUID promotion) {
        return jdbc.queryForObject("SELECT count(*) FROM promotion_redemptions WHERE promotion_id=?",
                Long.class, promotion);
    }

    public long customerUses(UUID promotion, UUID customer) {
        return jdbc.queryForObject("""
SELECT count(*) FROM promotion_redemptions WHERE promotion_id=? AND customer_id=?
""", Long.class, promotion, customer);
    }

    public void redeem(Locked promotion, UUID customer, UUID order, BigDecimal discount, Instant now) {
        jdbc.update("""
INSERT INTO order_promotion_snapshots(order_id,promotion_id,restaurant_id,code,name,discount_type,discount_total,created_at)
VALUES (?,?,?,?,?,?,?,?)
""", order, promotion.id(), promotion.restaurant(), promotion.code(), promotion.name(),
                promotion.type().name(), discount, Timestamp.from(now));
        jdbc.update("""
INSERT INTO promotion_redemptions(id,promotion_id,customer_id,order_id,redeemed_at) VALUES (?,?,?,?,?)
""", UUID.randomUUID(), promotion.id(), customer, order, Timestamp.from(now));
    }

    private View view(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new View(r.getObject("id", UUID.class), r.getObject("restaurant_id", UUID.class),
                r.getString("code"), r.getString("name"), r.getString("description"),
                DiscountType.valueOf(r.getString("discount_type")), r.getBigDecimal("percentage_value"),
                r.getBigDecimal("fixed_amount"), r.getBigDecimal("minimum_merchandise_subtotal"),
                r.getBigDecimal("maximum_discount"), instant(r.getTimestamp("starts_at")),
                instant(r.getTimestamp("ends_at")), r.getBoolean("active"),
                nullableLong(r, "total_usage_limit"), nullableLong(r, "per_customer_usage_limit"),
                r.getLong("version"), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant());
    }

    private static String optional(String value) {
        if (value == null) return null;
        String result = value.strip();
        return result.isEmpty() ? null : result;
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Long nullableLong(java.sql.ResultSet r, String name) throws java.sql.SQLException {
        long value = r.getLong(name);
        return r.wasNull() ? null : value;
    }

    public record Locked(UUID id, UUID restaurant, String code, String name, DiscountType type,
            BigDecimal percentage, BigDecimal fixed, BigDecimal minimum, BigDecimal maximum,
            Instant starts, Instant ends, boolean active, Long totalLimit, Long customerLimit) {}

}
