\pset pager off
\timing on

-- Representative parameters from the deterministic Module 21 dataset.
-- Run only outside load intervals.

\echo 'restaurant list count'
EXPLAIN (ANALYZE, BUFFERS)
WITH candidates AS (
 SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
        b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
        COALESCE(CASE WHEN sh.branch_id IS NOT NULL
          THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
          ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END,false) AS schedule_open,
        false AS serviceable,d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
 FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
 LEFT JOIN delivery_zones z ON false
 LEFT JOIN cities ci ON ci.id=z.city_id
 LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
 CROSS JOIN LATERAL (SELECT timestamptz '2026-09-12 10:00:00+00' AT TIME ZONE b.timezone AS local_now) t
 LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
 LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
 WHERE r.status='ACTIVE' AND b.status='ACTIVE'
), eligible AS (SELECT *,schedule_open AND NOT paused AS open_now FROM candidates),
cards AS (SELECT restaurant_id,restaurant_name,description,count(*) branch_count,
 bool_or(open_now) open_now,min(delivery_fee) delivery_fee,min(minimum_order) minimum_order,
 min(eta_min_minutes) eta_min_minutes FROM eligible GROUP BY restaurant_id,restaurant_name,description)
SELECT count(*) FROM cards;

\echo 'restaurant list page'
EXPLAIN (ANALYZE, BUFFERS)
WITH candidates AS (
 SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
        b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
        COALESCE(CASE WHEN sh.branch_id IS NOT NULL
          THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
          ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END,false) AS schedule_open,
        false AS serviceable,d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
 FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
 LEFT JOIN delivery_zones z ON false
 LEFT JOIN cities ci ON ci.id=z.city_id
 LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
 CROSS JOIN LATERAL (SELECT timestamptz '2026-09-12 10:00:00+00' AT TIME ZONE b.timezone AS local_now) t
 LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
 LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
 WHERE r.status='ACTIVE' AND b.status='ACTIVE'
), eligible AS (SELECT *,schedule_open AND NOT paused AS open_now FROM candidates),
cards AS (SELECT restaurant_id,restaurant_name,description,count(*) branch_count,
 bool_or(open_now) open_now,min(delivery_fee) delivery_fee,min(minimum_order) minimum_order,
 min(eta_min_minutes) eta_min_minutes FROM eligible GROUP BY restaurant_id,restaurant_name,description)
SELECT * FROM cards ORDER BY lower(restaurant_name),restaurant_id LIMIT 20 OFFSET 0;

\echo 'restaurant substring search page'
EXPLAIN (ANALYZE, BUFFERS)
WITH candidates AS (
 SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
        b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
        COALESCE(CASE WHEN sh.branch_id IS NOT NULL
          THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
          ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END,false) AS schedule_open,
        false AS serviceable,d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
 FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
 LEFT JOIN delivery_zones z ON false
 LEFT JOIN cities ci ON ci.id=z.city_id
 LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
 CROSS JOIN LATERAL (SELECT timestamptz '2026-09-12 10:00:00+00' AT TIME ZONE b.timezone AS local_now) t
 LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
 LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
 WHERE r.status='ACTIVE' AND b.status='ACTIVE' AND
 (r.name ILIKE '%Load Restaurant 1%' ESCAPE '!' OR EXISTS (
   SELECT 1 FROM restaurant_menus m
   JOIN menu_categories mc ON mc.menu_id=m.id AND mc.active
   LEFT JOIN menu_items i ON i.category_id=mc.id AND i.active
   LEFT JOIN branch_menu_item_overrides o ON o.item_id=i.id AND o.branch_id=b.id
   WHERE m.restaurant_id=r.id AND m.active
     AND (r.name ILIKE '%Load Restaurant 1%' ESCAPE '!'
       OR mc.name ILIKE '%Load Restaurant 1%' ESCAPE '!'
       OR i.name ILIKE '%Load Restaurant 1%' ESCAPE '!')))
), eligible AS (SELECT *,schedule_open AND NOT paused AS open_now FROM candidates),
cards AS (SELECT restaurant_id,restaurant_name,description,count(*) branch_count,
 bool_or(open_now) open_now,min(delivery_fee) delivery_fee,min(minimum_order) minimum_order,
 min(eta_min_minutes) eta_min_minutes FROM eligible GROUP BY restaurant_id,restaurant_name,description)
SELECT * FROM cards ORDER BY lower(restaurant_name),restaurant_id LIMIT 20 OFFSET 0;

\echo 'zone-filtered restaurant page'
EXPLAIN (ANALYZE, BUFFERS)
WITH candidates AS (
 SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
        b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
        COALESCE(CASE WHEN sh.branch_id IS NOT NULL
          THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
          ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END,false) AS schedule_open,
        (CASE WHEN z.id IS NULL THEN 'ADDRESS_ZONE_REQUIRED' WHEN r.status<>'ACTIVE' THEN 'RESTAURANT_SUSPENDED'
          WHEN b.status<>'ACTIVE' THEN 'BRANCH_INACTIVE' WHEN b.paused THEN 'BRANCH_PAUSED'
          WHEN NOT ci.active THEN 'CITY_INACTIVE' WHEN NOT z.active THEN 'ZONE_INACTIVE'
          WHEN d.id IS NULL THEN 'ZONE_NOT_SERVED' WHEN NOT d.enabled THEN 'RELATIONSHIP_DISABLED'
          ELSE 'SERVICEABLE' END)='SERVICEABLE' AS serviceable,
        d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
 FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
 LEFT JOIN delivery_zones z ON z.id='00000000-0000-0000-0000-000000008101'
 LEFT JOIN cities ci ON ci.id=z.city_id
 LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
 CROSS JOIN LATERAL (SELECT timestamptz '2026-09-12 10:00:00+00' AT TIME ZONE b.timezone AS local_now) t
 LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
 LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
 WHERE r.status='ACTIVE' AND b.status='ACTIVE'
), eligible AS (SELECT *,schedule_open AND NOT paused AS open_now FROM candidates WHERE serviceable),
cards AS (SELECT restaurant_id,restaurant_name,description,count(*) branch_count,
 bool_or(open_now) open_now,min(delivery_fee) delivery_fee,min(minimum_order) minimum_order,
 min(eta_min_minutes) eta_min_minutes FROM eligible GROUP BY restaurant_id,restaurant_name,description)
SELECT * FROM cards ORDER BY lower(restaurant_name),restaurant_id LIMIT 20 OFFSET 0;

\echo 'openNow-filtered restaurant page'
EXPLAIN (ANALYZE, BUFFERS)
WITH candidates AS (
 SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
        b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
        COALESCE(CASE WHEN sh.branch_id IS NOT NULL
          THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
          ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END,false) AS schedule_open,
        false AS serviceable,d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
 FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
 LEFT JOIN delivery_zones z ON false
 LEFT JOIN cities ci ON ci.id=z.city_id
 LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
 CROSS JOIN LATERAL (SELECT timestamptz '2026-09-12 10:00:00+00' AT TIME ZONE b.timezone AS local_now) t
 LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
 LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
 WHERE r.status='ACTIVE' AND b.status='ACTIVE'
), eligible AS (SELECT *,schedule_open AND NOT paused AS open_now FROM candidates WHERE schedule_open AND NOT paused),
cards AS (SELECT restaurant_id,restaurant_name,description,count(*) branch_count,
 bool_or(open_now) open_now,min(delivery_fee) delivery_fee,min(minimum_order) minimum_order,
 min(eta_min_minutes) eta_min_minutes FROM eligible GROUP BY restaurant_id,restaurant_name,description)
SELECT * FROM cards ORDER BY lower(restaurant_name),restaurant_id LIMIT 20 OFFSET 0;

\echo 'restaurant detail page (same candidate/card shape, id predicate)'
EXPLAIN (ANALYZE, BUFFERS)
WITH candidates AS (
 SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
        b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
        COALESCE(CASE WHEN sh.branch_id IS NOT NULL
          THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
          ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END,false) AS schedule_open,
        false AS serviceable,d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
 FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
 LEFT JOIN delivery_zones z ON false
 LEFT JOIN cities ci ON ci.id=z.city_id
 LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
 CROSS JOIN LATERAL (SELECT timestamptz '2026-09-12 10:00:00+00' AT TIME ZONE b.timezone AS local_now) t
 LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
 LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
 WHERE r.status='ACTIVE' AND b.status='ACTIVE' AND r.id='00000000-0000-0000-0000-000000002001'
), eligible AS (SELECT *,schedule_open AND NOT paused AS open_now FROM candidates),
cards AS (SELECT restaurant_id,restaurant_name,description,count(*) branch_count,
 bool_or(open_now) open_now,min(delivery_fee) delivery_fee,min(minimum_order) minimum_order,
 min(eta_min_minutes) eta_min_minutes FROM eligible GROUP BY restaurant_id,restaurant_name,description)
SELECT * FROM cards ORDER BY lower(restaurant_name),restaurant_id LIMIT 1 OFFSET 0;

\echo 'branch existence check'
EXPLAIN (ANALYZE, BUFFERS)
SELECT EXISTS(SELECT 1 FROM restaurants r WHERE r.id='00000000-0000-0000-0000-000000002001'
 AND r.status='ACTIVE' AND EXISTS(SELECT 1 FROM branches b WHERE b.restaurant_id=r.id AND b.status='ACTIVE'));

\echo 'branch choices page'
EXPLAIN (ANALYZE, BUFFERS)
WITH candidates AS (
 SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
        b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
        COALESCE(CASE WHEN sh.branch_id IS NOT NULL
          THEN t.local_now::time >= sh.opens_at AND t.local_now::time < sh.closes_at
          ELSE t.local_now::time >= wh.opens_at AND t.local_now::time < wh.closes_at END,false) AS schedule_open,
        false AS serviceable,d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
 FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
 LEFT JOIN delivery_zones z ON false
 LEFT JOIN cities ci ON ci.id=z.city_id
 LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
 CROSS JOIN LATERAL (SELECT timestamptz '2026-09-12 10:00:00+00' AT TIME ZONE b.timezone AS local_now) t
 LEFT JOIN branch_special_hours sh ON sh.branch_id=b.id AND sh.service_date=t.local_now::date
 LEFT JOIN branch_opening_hours wh ON wh.branch_id=b.id AND wh.weekday=EXTRACT(ISODOW FROM t.local_now)
 WHERE r.status='ACTIVE' AND b.status='ACTIVE' AND r.id='00000000-0000-0000-0000-000000002001'
), eligible AS (SELECT *,schedule_open AND NOT paused AS open_now FROM candidates)
SELECT * FROM eligible ORDER BY lower(name),id LIMIT 20 OFFSET 0;

\echo 'effective menu header'
EXPLAIN (ANALYZE, BUFFERS)
SELECT m.id,m.name,m.currency FROM restaurant_menus m JOIN restaurants r ON r.id=m.restaurant_id
JOIN branches b ON b.restaurant_id=r.id WHERE r.id='00000000-0000-0000-0000-000000002001'
AND b.id='00000000-0000-0000-0000-000000003001' AND r.status='ACTIVE' AND b.status='ACTIVE' AND m.active;

\echo 'effective menu category count'
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM menu_categories WHERE menu_id='00000000-0000-0000-0000-000000004001' AND active;

\echo 'effective menu categories page'
EXPLAIN (ANALYZE, BUFFERS)
SELECT id,name,description FROM menu_categories
WHERE menu_id='00000000-0000-0000-0000-000000004001' AND active
ORDER BY position,id LIMIT 5 OFFSET 0;

\echo 'effective menu items batch'
EXPLAIN (ANALYZE, BUFFERS)
WITH effective AS (
 SELECT i.id,i.category_id,i.name,i.description,i.position,COALESCE(o.price,i.base_price) effective_price,
        (m.active AND c.active AND i.active AND COALESCE(o.available,i.available)) effective_available
 FROM menu_items i JOIN menu_categories c ON c.id=i.category_id JOIN restaurant_menus m ON m.id=c.menu_id
 LEFT JOIN branch_menu_item_overrides o ON o.item_id=i.id AND o.branch_id='00000000-0000-0000-0000-000000003001'
 WHERE c.id IN ('00000000-0000-0000-0000-000000005001','00000000-0000-0000-0000-000000005002',
                '00000000-0000-0000-0000-000000005003','00000000-0000-0000-0000-000000005004',
                '00000000-0000-0000-0000-000000005005')
   AND m.active AND c.active AND i.active
   AND (m.active AND c.active AND i.active AND COALESCE(o.available,i.available))
), ranked AS (SELECT *,row_number() OVER(PARTITION BY category_id ORDER BY position,id) rn FROM effective),
totals AS (SELECT category_id,count(*) total FROM effective GROUP BY category_id)
SELECT totals.category_id,totals.total,r.id,r.name,r.description,r.effective_price,r.effective_available
FROM totals LEFT JOIN ranked r ON r.category_id=totals.category_id AND r.rn>0 AND r.rn<=20
ORDER BY totals.category_id,r.rn;
