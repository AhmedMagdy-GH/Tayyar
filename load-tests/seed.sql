\set ON_ERROR_STOP on
BEGIN;

-- This script is destructive by design and must only be used with the isolated
-- `tayyar-load` Compose project. It resets application data, not the schema.
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE cities CASCADE;

-- All benchmark identities use the same local-only password: Load test password!
INSERT INTO users(id,full_name,email,password_hash,status,email_verified_at,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,
       'Load Customer ' || i, 'load-customer-' || i || '@example.test',
       '{bcrypt}$2a$12$RVNc/mv3GpCk2sW79ZHmguDMkoXVxl0roeDMJ.l4C7myu7COC4hoO',
       'ACTIVE', now(), now(), now()
FROM generate_series(1,40) i;

INSERT INTO users(id,full_name,email,password_hash,status,email_verified_at,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,
       CASE WHEN i=41 THEN 'Load Admin' WHEN i=42 THEN 'Load Owner'
            WHEN i BETWEEN 43 AND 52 THEN 'Load Driver ' || (i-42)
            ELSE 'Load Staff ' || (i-52) END,
       CASE WHEN i=41 THEN 'load-admin@example.test' WHEN i=42 THEN 'load-owner@example.test'
            WHEN i BETWEEN 43 AND 52 THEN 'load-driver-' || (i-42) || '@example.test'
            ELSE 'load-staff-' || (i-52) || '@example.test' END,
       '{bcrypt}$2a$12$RVNc/mv3GpCk2sW79ZHmguDMkoXVxl0roeDMJ.l4C7myu7COC4hoO',
       'ACTIVE', now(), now(), now()
FROM generate_series(41,60) i;

INSERT INTO user_roles SELECT ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'CUSTOMER' FROM generate_series(1,40) i;
INSERT INTO user_roles VALUES
 ('00000000-0000-0000-0000-000000000041','ADMIN'),
 ('00000000-0000-0000-0000-000000000042','RESTAURANT_OWNER');
INSERT INTO user_roles SELECT ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'DRIVER' FROM generate_series(43,52) i;
INSERT INTO user_roles SELECT ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'RESTAURANT_STAFF' FROM generate_series(53,60) i;

INSERT INTO restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((1000+i)::text,12,'0'))::uuid,
       '00000000-0000-0000-0000-000000000042','APPROVED',1,now()-make_interval(days=>i),now()
FROM generate_series(1,20) i;
INSERT INTO application_submissions(application_id,revision,name,description,submitted_at)
SELECT ('00000000-0000-0000-0000-'||lpad((1000+i)::text,12,'0'))::uuid,1,
       'Load Restaurant '||i,'Deterministic load-test restaurant',now()-make_interval(days=>i)
FROM generate_series(1,20) i;
INSERT INTO restaurants(id,application_id,name,description,status,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((2000+i)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((1000+i)::text,12,'0'))::uuid,
       'Load Restaurant '||i,'Representative discovery data','ACTIVE',now()-make_interval(days=>i),now()
FROM generate_series(1,20) i;
INSERT INTO restaurant_memberships
SELECT ('00000000-0000-0000-0000-'||lpad((2000+i)::text,12,'0'))::uuid,
       '00000000-0000-0000-0000-000000000042','OWNER',now()
FROM generate_series(1,20) i;

INSERT INTO branches(id,restaurant_id,name,address_line1,city,country_code,phone,latitude,longitude,timezone,delivery_model,status,paused,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((3000+i)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((2000+((i-1)/2)+1)::text,12,'0'))::uuid,
       'Branch '||i, i||' Load Street','Cairo','EG','+201000000001',30.040000+(i::numeric/10000),31.230000+(i::numeric/10000),
       'Africa/Cairo','TAYYAR_DELIVERY','ACTIVE',false,now()-make_interval(hours=>i),now()
FROM generate_series(1,40) i;
INSERT INTO branch_opening_hours
SELECT ('00000000-0000-0000-0000-'||lpad((3000+b)::text,12,'0'))::uuid,d,'00:00','23:59'
FROM generate_series(1,40) b CROSS JOIN generate_series(1,7) d;

INSERT INTO restaurant_menus(id,restaurant_id,name,active,currency,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((4000+i)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((2000+i)::text,12,'0'))::uuid,'Main Menu',true,'EGP',now(),now()
FROM generate_series(1,20) i;
INSERT INTO menu_categories(id,menu_id,restaurant_id,name,description,active,position,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((5000+(r-1)*5+c)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((4000+r)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((2000+r)::text,12,'0'))::uuid,
       'Category '||c,'Representative category',true,c-1,now(),now()
FROM generate_series(1,20) r CROSS JOIN generate_series(1,5) c;
INSERT INTO menu_items(id,category_id,restaurant_id,name,description,base_price,active,available,position,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((6000+(r-1)*50+(c-1)*10+n)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((5000+(r-1)*5+c)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((2000+r)::text,12,'0'))::uuid,
       'Item '||r||'-'||c||'-'||n,'Representative menu item',50+n+(c*2),true,true,n-1,now(),now()
FROM generate_series(1,20) r CROSS JOIN generate_series(1,5) c CROSS JOIN generate_series(1,10) n;

INSERT INTO cities(id,name,active,created_at,updated_at) VALUES
 ('00000000-0000-0000-0000-000000008001','Cairo',true,now(),now()),
 ('00000000-0000-0000-0000-000000008002','Giza',true,now(),now());
INSERT INTO delivery_zones(id,city_id,name,active,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((8100+i)::text,12,'0'))::uuid,
       CASE WHEN i<=5 THEN '00000000-0000-0000-0000-000000008001'::uuid ELSE '00000000-0000-0000-0000-000000008002'::uuid END,
       'Zone '||i,true,now(),now() FROM generate_series(1,10) i;
INSERT INTO branch_delivery_zones(id,branch_id,delivery_zone_id,delivery_fee,minimum_order,eta_min_minutes,eta_max_minutes,enabled,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((8200+(b-1)*5+z)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((3000+b)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((8100+z)::text,12,'0'))::uuid,
       20+z,50,20,45,true,now(),now()
FROM generate_series(1,40) b CROSS JOIN generate_series(1,5) z;
INSERT INTO customer_addresses(id,user_id,label,street,building,city,country_code,latitude,longitude,is_default,delivery_zone_id,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((9000+i)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'Home',i||' Customer Street','12','Cairo','EG',30.04,31.23,true,
       ('00000000-0000-0000-0000-'||lpad((8100+((i-1)%5)+1)::text,12,'0'))::uuid,now(),now()
FROM generate_series(1,40) i;

INSERT INTO promotions(id,restaurant_id,code,name,discount_type,percentage_value,minimum_merchandise_subtotal,maximum_discount,active,total_usage_limit,per_customer_usage_limit,created_at,updated_at)
VALUES ('00000000-0000-0000-0000-000000009500','00000000-0000-0000-0000-000000002001','SHARED10','Shared ten percent','PERCENTAGE',10,0,100,true,10000,20,now(),now());
INSERT INTO promotions(id,restaurant_id,code,name,discount_type,percentage_value,minimum_merchandise_subtotal,maximum_discount,active,total_usage_limit,per_customer_usage_limit,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad((9500+i)::text,12,'0'))::uuid,
       '00000000-0000-0000-0000-000000002001','PROMO'||lpad(i::text,2,'0'),'Distributed '||i,
       'PERCENTAGE',10,0,100,true,1000,20,now(),now() FROM generate_series(1,20) i;

INSERT INTO favorites(customer_id,restaurant_id,created_at)
SELECT ('00000000-0000-0000-0000-'||lpad(c::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((2000+r)::text,12,'0'))::uuid,now()-make_interval(hours=>r)
FROM generate_series(1,40) c CROSS JOIN generate_series(1,3) r;
INSERT INTO notifications(id,recipient_user_id,type,title,body,created_at,deduplication_key)
SELECT ('10000000-0000-0000-0000-'||lpad((c*100+n)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad(c::text,12,'0'))::uuid,'ORDER_ACCEPTED','Load notification','Representative notification',
       now()-make_interval(mins=>n),'load-'||c||'-'||n
FROM generate_series(1,40) c CROSS JOIN generate_series(1,20) n;

-- 600 delivered history rows, 100 PLACED operational rows, and 100 READY rows.
INSERT INTO orders(id,customer_id,restaurant_id,branch_id,status,currency,merchandise_subtotal,delivery_fee,discount_total,final_total,version,created_at,updated_at)
SELECT ('20000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((((i-1)%40)+1)::text,12,'0'))::uuid,
       '00000000-0000-0000-0000-000000002001','00000000-0000-0000-0000-000000003001',
       CASE WHEN i<=600 THEN 'DELIVERED' WHEN i<=700 THEN 'PLACED' ELSE 'READY_FOR_PICKUP' END,
       'EGP',53,21,0,74,CASE WHEN i<=600 THEN 5 WHEN i<=700 THEN 0 ELSE 3 END,
       now()-make_interval(mins=>(801-i)),now()
FROM generate_series(1,800) i;
INSERT INTO order_items(id,order_id,restaurant_id,menu_item_id,purchased_name,unit_price,quantity,line_subtotal,created_at)
SELECT ('21000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
       ('20000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'00000000-0000-0000-0000-000000002001',
       '00000000-0000-0000-0000-000000006001','Item 1-1-1',53,1,53,now() FROM generate_series(1,800) i;
INSERT INTO order_address_snapshots(order_id,label,street,building,city,country_code,delivery_zone_id,delivery_zone_name,managed_city_name,created_at)
SELECT ('20000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'Home','Snapshot Street','12','Cairo','EG',
       '00000000-0000-0000-0000-000000008101','Zone 1','Cairo',now() FROM generate_series(1,800) i;
INSERT INTO order_status_history(id,order_id,previous_status,new_status,actor_kind,actor_id,occurred_at)
SELECT ('22000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
       ('20000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,NULL,
       CASE WHEN i<=600 THEN 'DELIVERED' WHEN i<=700 THEN 'PLACED' ELSE 'READY_FOR_PICKUP' END,
       'SYSTEM',NULL,now() FROM generate_series(1,800) i;
INSERT INTO payments(id,order_id,method,status,amount,currency,version,created_at,updated_at)
SELECT ('23000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
       ('20000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'CASH',CASE WHEN i<=600 THEN 'PAID' ELSE 'PENDING' END,
       74,'EGP',CASE WHEN i<=600 THEN 1 ELSE 0 END,now(),now() FROM generate_series(1,800) i;
INSERT INTO payment_status_history(id,payment_id,previous_status,new_status,actor_kind,occurred_at)
SELECT ('24000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
       ('23000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,NULL,
       CASE WHEN i<=600 THEN 'PAID' ELSE 'PENDING' END,'SYSTEM',now() FROM generate_series(1,800) i;

INSERT INTO driver_profiles(user_id,state,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'BUSY',now(),now() FROM generate_series(43,52) i;
INSERT INTO delivery_assignments(id,order_id,driver_id,status,assigned_by,assigned_at)
SELECT ('25000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
       ('20000000-0000-0000-0000-'||lpad((700+i)::text,12,'0'))::uuid,
       ('00000000-0000-0000-0000-'||lpad((42+i)::text,12,'0'))::uuid,'ACTIVE','00000000-0000-0000-0000-000000000041',now()
FROM generate_series(1,10) i;

COMMIT;
ANALYZE;

SELECT 'users' entity,count(*) FROM users UNION ALL
SELECT 'restaurants',count(*) FROM restaurants UNION ALL SELECT 'branches',count(*) FROM branches UNION ALL
SELECT 'menu_items',count(*) FROM menu_items UNION ALL SELECT 'delivery_zones',count(*) FROM delivery_zones UNION ALL
SELECT 'addresses',count(*) FROM customer_addresses UNION ALL SELECT 'orders',count(*) FROM orders UNION ALL
SELECT 'notifications',count(*) FROM notifications;
