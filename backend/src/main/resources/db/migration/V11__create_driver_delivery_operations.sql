CREATE TABLE driver_profiles (
 user_id UUID PRIMARY KEY REFERENCES users(id),
 state VARCHAR(16) NOT NULL CHECK (state IN ('OFFLINE','AVAILABLE','BUSY')),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE delivery_assignments (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL REFERENCES orders(id),
 driver_id UUID NOT NULL REFERENCES driver_profiles(user_id),
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','COMPLETED')),
 assigned_by UUID NOT NULL REFERENCES users(id),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 assigned_at TIMESTAMPTZ NOT NULL,
 completed_at TIMESTAMPTZ,
 CHECK ((status='ACTIVE' AND completed_at IS NULL) OR
        (status='COMPLETED' AND completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX delivery_assignments_active_order_uq
 ON delivery_assignments(order_id) WHERE status='ACTIVE';
CREATE UNIQUE INDEX delivery_assignments_active_driver_uq
 ON delivery_assignments(driver_id) WHERE status='ACTIVE';
CREATE INDEX delivery_assignments_driver_history_idx
 ON delivery_assignments(driver_id,assigned_at DESC,id DESC);
CREATE INDEX delivery_assignments_order_history_idx
 ON delivery_assignments(order_id,assigned_at DESC,id DESC);

CREATE TABLE driver_state_history (
 id UUID PRIMARY KEY,
 driver_id UUID NOT NULL REFERENCES driver_profiles(user_id),
 previous_state VARCHAR(16),
 new_state VARCHAR(16) NOT NULL CHECK (new_state IN ('OFFLINE','AVAILABLE','BUSY')),
 actor_id UUID NOT NULL REFERENCES users(id),
 reason VARCHAR(1000),
 occurred_at TIMESTAMPTZ NOT NULL,
 CHECK (previous_state IS NULL OR previous_state<>new_state)
);
CREATE INDEX driver_state_history_driver_idx
 ON driver_state_history(driver_id,occurred_at,id);

CREATE TABLE delivery_assignment_history (
 id UUID PRIMARY KEY,
 assignment_id UUID NOT NULL REFERENCES delivery_assignments(id),
 previous_status VARCHAR(16),
 new_status VARCHAR(16) NOT NULL CHECK (new_status IN ('ACTIVE','COMPLETED')),
 actor_id UUID NOT NULL REFERENCES users(id),
 reason VARCHAR(1000),
 occurred_at TIMESTAMPTZ NOT NULL,
 CHECK (previous_status IS NULL OR previous_status<>new_status)
);
CREATE INDEX delivery_assignment_history_assignment_idx
 ON delivery_assignment_history(assignment_id,occurred_at,id);

CREATE FUNCTION validate_driver_profile_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.user_id<>OLD.user_id OR NEW.created_at<>OLD.created_at THEN
   RAISE EXCEPTION 'Driver profile identity is immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.state<>OLD.state AND NOT (
   (OLD.state='OFFLINE' AND NEW.state='AVAILABLE') OR
   (OLD.state='AVAILABLE' AND NEW.state IN ('OFFLINE','BUSY')) OR
   (OLD.state='BUSY' AND NEW.state='AVAILABLE')) THEN
   RAISE EXCEPTION 'Invalid driver state transition' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER driver_profile_update_guard BEFORE UPDATE ON driver_profiles
 FOR EACH ROW EXECUTE FUNCTION validate_driver_profile_update();

CREATE FUNCTION validate_delivery_assignment_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.order_id,NEW.driver_id,NEW.assigned_by,NEW.assigned_at) IS DISTINCT FROM
    (OLD.order_id,OLD.driver_id,OLD.assigned_by,OLD.assigned_at) THEN
   RAISE EXCEPTION 'Delivery assignment context is immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.status<>OLD.status AND NOT
    (OLD.status='ACTIVE' AND NEW.status='COMPLETED') THEN
   RAISE EXCEPTION 'Invalid delivery assignment transition' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER delivery_assignment_update_guard BEFORE UPDATE ON delivery_assignments
 FOR EACH ROW EXECUTE FUNCTION validate_delivery_assignment_update();

CREATE FUNCTION protect_delivery_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Historical delivery records are immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER driver_profiles_no_delete BEFORE DELETE ON driver_profiles
 FOR EACH ROW EXECUTE FUNCTION protect_delivery_history();
CREATE TRIGGER delivery_assignments_no_delete BEFORE DELETE ON delivery_assignments
 FOR EACH ROW EXECUTE FUNCTION protect_delivery_history();
CREATE TRIGGER driver_state_history_immutable BEFORE UPDATE OR DELETE ON driver_state_history
 FOR EACH ROW EXECUTE FUNCTION protect_delivery_history();
CREATE TRIGGER delivery_assignment_history_immutable BEFORE UPDATE OR DELETE ON delivery_assignment_history
 FOR EACH ROW EXECUTE FUNCTION protect_delivery_history();
