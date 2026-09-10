CREATE TABLE users (
 id UUID PRIMARY KEY,
 full_name VARCHAR(120) NOT NULL CHECK (length(btrim(full_name)) > 0),
 email VARCHAR(254) NOT NULL CONSTRAINT users_email_unique UNIQUE,
 phone VARCHAR(16),
 password_hash VARCHAR(255) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED')),
 email_verified_at TIMESTAMPTZ,
 auth_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email))),
 CONSTRAINT users_phone_e164 CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{7,14}$')
);
CREATE TABLE roles (name VARCHAR(32) PRIMARY KEY);
INSERT INTO roles(name) VALUES ('CUSTOMER'),('RESTAURANT_OWNER'),('RESTAURANT_STAFF'),('DRIVER'),('ADMIN');
CREATE TABLE user_roles (
 user_id UUID NOT NULL REFERENCES users(id),
 role_name VARCHAR(32) NOT NULL REFERENCES roles(name),
 PRIMARY KEY (user_id, role_name)
);
CREATE FUNCTION update_user_security_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.password_hash, NEW.status, NEW.email) IS DISTINCT FROM
    (OLD.password_hash, OLD.status, OLD.email) THEN
   NEW.auth_version := OLD.auth_version + 1;
 END IF;
 NEW.updated_at := CURRENT_TIMESTAMP;
 RETURN NEW;
END $$;
CREATE TRIGGER users_security_version BEFORE UPDATE ON users
 FOR EACH ROW EXECUTE FUNCTION update_user_security_version();
CREATE FUNCTION update_membership_security_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN ('DELETE','UPDATE') THEN
   UPDATE users SET auth_version = auth_version + 1 WHERE id = OLD.user_id;
 END IF;
 IF TG_OP IN ('INSERT','UPDATE') THEN
   UPDATE users SET auth_version = auth_version + 1 WHERE id = NEW.user_id;
 END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER roles_security_version AFTER INSERT OR UPDATE OR DELETE ON user_roles
 FOR EACH ROW EXECUTE FUNCTION update_membership_security_version();
