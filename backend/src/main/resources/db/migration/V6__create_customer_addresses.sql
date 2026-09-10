CREATE TABLE customer_addresses (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id),
 label VARCHAR(80) NOT NULL CHECK (length(btrim(label))>0),
 street VARCHAR(200) NOT NULL CHECK (length(btrim(street))>0),
 building VARCHAR(80) NOT NULL CHECK (length(btrim(building))>0),
 floor VARCHAR(40),
 apartment VARCHAR(40),
 landmark VARCHAR(200),
 instructions VARCHAR(1000),
 city VARCHAR(100) NOT NULL CHECK (length(btrim(city))>0),
 region VARCHAR(100),
 postal_code VARCHAR(20),
 country_code VARCHAR(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
 latitude NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
 longitude NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
 is_default BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CHECK ((latitude IS NULL)=(longitude IS NULL))
);
CREATE INDEX customer_addresses_user_idx ON customer_addresses(user_id,created_at DESC,id DESC);
CREATE UNIQUE INDEX customer_addresses_one_default_idx ON customer_addresses(user_id) WHERE is_default;
CREATE FUNCTION protect_address_owner() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.user_id<>OLD.user_id THEN
   RAISE EXCEPTION 'Address owner cannot change' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER address_owner_guard BEFORE UPDATE ON customer_addresses
 FOR EACH ROW EXECUTE FUNCTION protect_address_owner();
