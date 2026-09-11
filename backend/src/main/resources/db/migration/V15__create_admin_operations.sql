CREATE TABLE admin_audit_log (
 id UUID PRIMARY KEY,
 actor_id UUID NOT NULL REFERENCES users(id),
 action_type VARCHAR(48) NOT NULL CHECK (action_type IN ('ACCOUNT_SUSPENDED','ACCOUNT_REACTIVATED')),
 target_entity_type VARCHAR(32) NOT NULL CHECK (target_entity_type IN ('USER')),
 target_entity_id UUID NOT NULL,
 reason VARCHAR(1000) NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 1000),
 before_state VARCHAR(32) NOT NULL,
 after_state VARCHAR(32) NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL,
 CHECK (before_state <> after_state)
);

CREATE INDEX admin_audit_log_time_idx ON admin_audit_log(occurred_at DESC,id DESC);
CREATE INDEX admin_audit_log_actor_idx ON admin_audit_log(actor_id,occurred_at DESC,id DESC);
CREATE INDEX admin_audit_log_target_idx ON admin_audit_log(target_entity_type,target_entity_id,occurred_at DESC,id DESC);
CREATE INDEX users_admin_status_idx ON users(status,created_at DESC,id DESC);
CREATE INDEX users_admin_recent_idx ON users(created_at DESC,id DESC);
CREATE INDEX user_roles_admin_role_idx ON user_roles(role_name,user_id);
CREATE INDEX orders_admin_recent_idx ON orders(created_at DESC,id DESC);
CREATE INDEX orders_admin_status_idx ON orders(status,created_at DESC,id DESC);
CREATE INDEX driver_profiles_admin_state_idx ON driver_profiles(state,created_at DESC,user_id DESC);
CREATE INDEX admin_audit_log_action_idx ON admin_audit_log(action_type,occurred_at DESC,id DESC);

CREATE FUNCTION protect_admin_audit_log() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Administrative audit records are immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER admin_audit_log_immutable BEFORE UPDATE OR DELETE ON admin_audit_log
 FOR EACH ROW EXECUTE FUNCTION protect_admin_audit_log();
