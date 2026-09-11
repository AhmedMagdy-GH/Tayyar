CREATE TABLE notifications (
 id UUID PRIMARY KEY,
 recipient_user_id UUID NOT NULL REFERENCES users(id),
 type VARCHAR(48) NOT NULL CHECK (type IN (
   'ORDER_ACCEPTED','ORDER_REJECTED','ORDER_PREPARING','ORDER_READY_FOR_PICKUP',
   'ORDER_OUT_FOR_DELIVERY','ORDER_DELIVERED','DELIVERY_ASSIGNED',
   'RESTAURANT_APPLICATION_APPROVED','RESTAURANT_APPLICATION_REJECTED')),
 channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP' CHECK (channel = 'IN_APP'),
 title VARCHAR(120) NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
 body VARCHAR(500) NOT NULL CHECK (length(btrim(body)) BETWEEN 1 AND 500),
 related_entity_type VARCHAR(32) CHECK (related_entity_type IN (
   'ORDER','DELIVERY_ASSIGNMENT','RESTAURANT_APPLICATION')),
 related_entity_id UUID,
 read_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 deduplication_key VARCHAR(200) NOT NULL UNIQUE,
 CHECK ((related_entity_type IS NULL) = (related_entity_id IS NULL)),
 CHECK (read_at IS NULL OR read_at >= created_at)
);

CREATE INDEX notifications_recipient_created_idx
 ON notifications(recipient_user_id,created_at DESC,id DESC);
CREATE INDEX notifications_recipient_unread_idx
 ON notifications(recipient_user_id,created_at DESC,id DESC) WHERE read_at IS NULL;

CREATE FUNCTION protect_notification_ownership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.recipient_user_id,NEW.type,NEW.channel,NEW.title,NEW.body,
     NEW.related_entity_type,NEW.related_entity_id,NEW.created_at,NEW.deduplication_key)
    IS DISTINCT FROM
    (OLD.recipient_user_id,OLD.type,OLD.channel,OLD.title,OLD.body,
     OLD.related_entity_type,OLD.related_entity_id,OLD.created_at,OLD.deduplication_key) THEN
   RAISE EXCEPTION 'Notification ownership and content are immutable' USING ERRCODE='23514';
 END IF;
 IF OLD.read_at IS NOT NULL AND NEW.read_at IS DISTINCT FROM OLD.read_at THEN
   RAISE EXCEPTION 'Notification read state cannot be reversed' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER notifications_update_guard BEFORE UPDATE ON notifications
 FOR EACH ROW EXECUTE FUNCTION protect_notification_ownership();

CREATE FUNCTION protect_notification_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Notifications are retained' USING ERRCODE='23514';
END $$;
CREATE TRIGGER notifications_no_delete BEFORE DELETE ON notifications
 FOR EACH ROW EXECUTE FUNCTION protect_notification_delete();
