package com.tayyar.branch;

/** Schedule-only state, independent of pause/visibility. Alias b; parameter :now. */
public final class BranchReadSql {
    private BranchReadSql() {}

    public static final String HOURS_JOIN =
            """
CROSS JOIN LATERAL (SELECT CAST(:now AS timestamptz) AT TIME ZONE b.timezone AS local_now) t
LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
""";
    public static final String OPEN =
            """
COALESCE(CASE WHEN sh.branch_id IS NOT NULL
  THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
  ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END, false)
""";
}
