package com.tayyar.delivery;

/** Authoritative eligibility precedence for scalar reads; aliases r,b,c,z,d. */
public final class DeliveryReadSql {
    private DeliveryReadSql() {}

    public static final String REASON =
            """
            CASE WHEN z.id IS NULL THEN 'ADDRESS_ZONE_REQUIRED'
                 WHEN r.status <> 'ACTIVE' THEN 'RESTAURANT_SUSPENDED'
                 WHEN b.status <> 'ACTIVE' THEN 'BRANCH_INACTIVE'
                 WHEN b.paused THEN 'BRANCH_PAUSED'
                 WHEN NOT c.active THEN 'CITY_INACTIVE'
                 WHEN NOT z.active THEN 'ZONE_INACTIVE'
                 WHEN d.id IS NULL THEN 'ZONE_NOT_SERVED'
                 WHEN NOT d.enabled THEN 'RELATIONSHIP_DISABLED'
                 ELSE 'SERVICEABLE' END
            """;
}
