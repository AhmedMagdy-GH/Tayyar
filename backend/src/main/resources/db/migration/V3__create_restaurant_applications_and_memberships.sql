CREATE TABLE restaurant_applications (
 id UUID PRIMARY KEY,
 applicant_id UUID NOT NULL REFERENCES users(id),
 status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 current_revision INTEGER NOT NULL CHECK (current_revision > 0),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX applications_applicant_idx ON restaurant_applications(applicant_id, created_at DESC, id DESC);
CREATE INDEX applications_queue_idx ON restaurant_applications(status, created_at DESC, id DESC);
CREATE TABLE application_submissions (
 application_id UUID NOT NULL REFERENCES restaurant_applications(id),
 revision INTEGER NOT NULL CHECK (revision > 0),
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
 description VARCHAR(2000) NOT NULL,
 submitted_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (application_id, revision)
);
ALTER TABLE restaurant_applications ADD CONSTRAINT application_current_submission_fk
 FOREIGN KEY(id,current_revision) REFERENCES application_submissions(application_id,revision)
 DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE restaurants (
 id UUID PRIMARY KEY,
 application_id UUID NOT NULL UNIQUE REFERENCES restaurant_applications(id),
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
 description VARCHAR(2000) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX restaurants_status_idx ON restaurants(status, created_at DESC, id DESC);
CREATE TABLE application_decisions (
 application_id UUID NOT NULL,
 revision INTEGER NOT NULL,
 reviewer_id UUID NOT NULL REFERENCES users(id),
 outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('APPROVED','REJECTED')),
 reason VARCHAR(1000),
 restaurant_id UUID UNIQUE REFERENCES restaurants(id),
 decided_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (application_id,revision),
 FOREIGN KEY(application_id,revision) REFERENCES application_submissions(application_id,revision),
 CHECK (outcome <> 'REJECTED' OR (reason IS NOT NULL AND length(btrim(reason)) > 0)),
 CHECK ((outcome = 'APPROVED') = (restaurant_id IS NOT NULL))
);
CREATE TABLE restaurant_memberships (
 restaurant_id UUID NOT NULL REFERENCES restaurants(id),
 user_id UUID NOT NULL REFERENCES users(id),
 membership_type VARCHAR(16) NOT NULL CHECK (membership_type = 'OWNER'),
 created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (restaurant_id,user_id)
);
CREATE INDEX memberships_user_idx ON restaurant_memberships(user_id,restaurant_id);
CREATE TABLE restaurant_status_history (
 id UUID PRIMARY KEY,
 restaurant_id UUID NOT NULL REFERENCES restaurants(id),
 actor_id UUID NOT NULL REFERENCES users(id),
 previous_status VARCHAR(16) NOT NULL CHECK (previous_status IN ('ACTIVE','SUSPENDED')),
 new_status VARCHAR(16) NOT NULL CHECK (new_status IN ('ACTIVE','SUSPENDED')),
 reason VARCHAR(1000) NOT NULL CHECK (length(btrim(reason)) > 0),
 changed_at TIMESTAMPTZ NOT NULL,
 CHECK (previous_status <> new_status)
);
CREATE INDEX restaurant_status_history_idx ON restaurant_status_history(restaurant_id,changed_at DESC,id DESC);

CREATE FUNCTION protect_restaurant_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Restaurant history is immutable' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER submissions_immutable BEFORE UPDATE OR DELETE ON application_submissions
 FOR EACH ROW EXECUTE FUNCTION protect_restaurant_history();
CREATE TRIGGER decisions_immutable BEFORE UPDATE OR DELETE ON application_decisions
 FOR EACH ROW EXECUTE FUNCTION protect_restaurant_history();
CREATE TRIGGER status_history_immutable BEFORE UPDATE OR DELETE ON restaurant_status_history
 FOR EACH ROW EXECUTE FUNCTION protect_restaurant_history();

CREATE FUNCTION validate_application_decision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.reviewer_id = (SELECT applicant_id FROM restaurant_applications WHERE id = NEW.application_id) THEN
   RAISE EXCEPTION 'Self-review is prohibited' USING ERRCODE = '23514';
 END IF;
 IF NEW.restaurant_id IS NOT NULL AND NOT EXISTS
   (SELECT 1 FROM restaurants WHERE id = NEW.restaurant_id AND application_id = NEW.application_id) THEN
   RAISE EXCEPTION 'Approval restaurant mismatch' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER decision_review_guard BEFORE INSERT ON application_decisions
 FOR EACH ROW EXECUTE FUNCTION validate_application_decision();

CREATE FUNCTION lock_membership_restaurant() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'UPDATE' AND NEW.restaurant_id <> OLD.restaurant_id THEN
   RAISE EXCEPTION 'Membership transfers are not supported' USING ERRCODE = '23514';
 END IF;
 IF TG_OP = 'DELETE' THEN
   PERFORM 1 FROM restaurants WHERE id = OLD.restaurant_id FOR UPDATE;
   RETURN OLD;
 END IF;
 PERFORM 1 FROM restaurants WHERE id = NEW.restaurant_id FOR UPDATE;
 RETURN NEW;
END $$;
CREATE TRIGGER membership_restaurant_lock BEFORE INSERT OR UPDATE OR DELETE ON restaurant_memberships
 FOR EACH ROW EXECUTE FUNCTION lock_membership_restaurant();
CREATE FUNCTION enforce_restaurant_owner() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target UUID;
BEGIN
 IF TG_TABLE_NAME = 'restaurants' THEN target := NEW.id;
 ELSIF TG_OP = 'DELETE' THEN target := OLD.restaurant_id;
 ELSE target := NEW.restaurant_id;
 END IF;
 IF EXISTS (SELECT 1 FROM restaurants WHERE id = target) AND NOT EXISTS
   (SELECT 1 FROM restaurant_memberships WHERE restaurant_id = target AND membership_type = 'OWNER') THEN
   RAISE EXCEPTION 'Restaurant requires an owner' USING ERRCODE = '23514';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER restaurant_requires_owner AFTER INSERT ON restaurants
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_restaurant_owner();
CREATE CONSTRAINT TRIGGER membership_requires_owner AFTER INSERT OR UPDATE OR DELETE ON restaurant_memberships
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_restaurant_owner();
