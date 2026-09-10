CREATE TABLE cities (
 id UUID PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 active BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CHECK (length(regexp_replace(lower(name), '[[:space:]-]+', '', 'g')) > 0)
);
CREATE UNIQUE INDEX cities_normalized_name_uq
 ON cities ((regexp_replace(lower(name), '[[:space:]-]+', '', 'g')));

CREATE TABLE delivery_zones (
 id UUID PRIMARY KEY,
 city_id UUID NOT NULL REFERENCES cities(id),
 name VARCHAR(100) NOT NULL,
 active BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CHECK (length(regexp_replace(lower(name), '[[:space:]-]+', '', 'g')) > 0)
);
CREATE UNIQUE INDEX delivery_zones_city_name_uq
 ON delivery_zones (city_id, (regexp_replace(lower(name), '[[:space:]-]+', '', 'g')));

CREATE TABLE branch_delivery_zones (
 id UUID PRIMARY KEY,
 branch_id UUID NOT NULL REFERENCES branches(id),
 delivery_zone_id UUID NOT NULL REFERENCES delivery_zones(id),
 delivery_fee NUMERIC(12,2) NOT NULL CHECK (delivery_fee >= 0 AND delivery_fee < 10000000000),
 minimum_order NUMERIC(12,2) NOT NULL CHECK (minimum_order >= 0 AND minimum_order < 10000000000),
 eta_min_minutes INTEGER NOT NULL CHECK (eta_min_minutes > 0),
 eta_max_minutes INTEGER NOT NULL CHECK (eta_max_minutes >= eta_min_minutes),
 enabled BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE (branch_id, delivery_zone_id)
);
CREATE INDEX branch_delivery_zones_zone_idx ON branch_delivery_zones(delivery_zone_id,branch_id);
ALTER TABLE customer_addresses ADD COLUMN delivery_zone_id UUID REFERENCES delivery_zones(id);
CREATE INDEX customer_addresses_zone_idx ON customer_addresses(delivery_zone_id)
 WHERE delivery_zone_id IS NOT NULL;

-- Referenced geography and relationship identities cannot be reassigned.
CREATE FUNCTION protect_delivery_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME = 'delivery_zones' THEN
   IF NEW.city_id <> OLD.city_id THEN
     RAISE EXCEPTION 'Zone city cannot change' USING ERRCODE = '23514';
   END IF;
 ELSE
   IF NEW.branch_id <> OLD.branch_id OR NEW.delivery_zone_id <> OLD.delivery_zone_id THEN
     RAISE EXCEPTION 'Service area identity cannot change' USING ERRCODE = '23514';
   END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER delivery_zones_identity BEFORE UPDATE ON delivery_zones
 FOR EACH ROW EXECUTE FUNCTION protect_delivery_identity();
CREATE TRIGGER branch_delivery_zones_identity BEFORE UPDATE ON branch_delivery_zones
 FOR EACH ROW EXECUTE FUNCTION protect_delivery_identity();
