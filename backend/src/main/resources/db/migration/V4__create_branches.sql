CREATE TABLE branches (
 id UUID PRIMARY KEY,
 restaurant_id UUID NOT NULL REFERENCES restaurants(id),
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
 address_line1 VARCHAR(200) NOT NULL CHECK (length(btrim(address_line1)) > 0),
 address_line2 VARCHAR(200),
 city VARCHAR(100) NOT NULL CHECK (length(btrim(city)) > 0),
 region VARCHAR(100),
 postal_code VARCHAR(20),
 country_code VARCHAR(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
 phone VARCHAR(16) CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
 latitude NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
 longitude NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
 timezone VARCHAR(100) NOT NULL,
 delivery_model VARCHAR(24) NOT NULL CHECK (delivery_model IN ('RESTAURANT_DELIVERY','TAYYAR_DELIVERY')),
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
 paused BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CHECK ((latitude IS NULL) = (longitude IS NULL)),
 UNIQUE (id, restaurant_id)
);
CREATE INDEX branches_restaurant_idx ON branches(restaurant_id,status,created_at DESC,id DESC);
CREATE TABLE branch_opening_hours (
 branch_id UUID NOT NULL REFERENCES branches(id),
 weekday SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
 opens_at TIME NOT NULL,
 closes_at TIME NOT NULL,
 PRIMARY KEY (branch_id,weekday),
 CHECK (opens_at < closes_at AND closes_at < TIME '24:00'),
 CHECK (EXTRACT(SECOND FROM opens_at)=0 AND EXTRACT(SECOND FROM closes_at)=0)
);
CREATE TABLE branch_special_hours (
 branch_id UUID NOT NULL REFERENCES branches(id),
 service_date DATE NOT NULL,
 opens_at TIME,
 closes_at TIME,
 PRIMARY KEY (branch_id,service_date),
 CHECK ((opens_at IS NULL) = (closes_at IS NULL)),
 CHECK (opens_at < closes_at AND closes_at < TIME '24:00'),
 CHECK (EXTRACT(SECOND FROM opens_at)=0 AND EXTRACT(SECOND FROM closes_at)=0)
);
CREATE TABLE branch_staff_assignments (
 branch_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 user_id UUID NOT NULL,
 assigned_by UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(branch_id,user_id),
 FOREIGN KEY(branch_id,restaurant_id) REFERENCES branches(id,restaurant_id),
 FOREIGN KEY(restaurant_id,user_id) REFERENCES restaurant_memberships(restaurant_id,user_id)
);
CREATE INDEX branch_staff_user_idx ON branch_staff_assignments(user_id,branch_id);
CREATE INDEX branch_staff_membership_idx ON branch_staff_assignments(restaurant_id,user_id);
CREATE FUNCTION validate_branch_location() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'UPDATE' AND NEW.restaurant_id <> OLD.restaurant_id THEN
   RAISE EXCEPTION 'Branch restaurant cannot change' USING ERRCODE = '23514';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = NEW.timezone) THEN
   RAISE EXCEPTION 'Invalid branch timezone' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER branch_location_guard BEFORE INSERT OR UPDATE ON branches
 FOR EACH ROW EXECUTE FUNCTION validate_branch_location();
