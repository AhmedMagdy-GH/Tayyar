CREATE TABLE restaurant_menus (
 id UUID PRIMARY KEY,
 restaurant_id UUID NOT NULL UNIQUE REFERENCES restaurants(id),
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 currency VARCHAR(3) NOT NULL DEFAULT 'EGP' CHECK (currency='EGP'),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,restaurant_id)
);
CREATE TABLE menu_categories (
 id UUID PRIMARY KEY,
 menu_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name))>0),
 description VARCHAR(2000),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 position INTEGER NOT NULL CHECK (position>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(menu_id,restaurant_id) REFERENCES restaurant_menus(id,restaurant_id),
 UNIQUE(id,restaurant_id),
 UNIQUE(menu_id,position) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE menu_items (
 id UUID PRIMARY KEY,
 category_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name))>0),
 description VARCHAR(2000),
 base_price NUMERIC(12,2) NOT NULL CHECK (base_price>=0 AND base_price<>'NaN'::numeric),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 available BOOLEAN NOT NULL DEFAULT TRUE,
 position INTEGER NOT NULL CHECK (position>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(category_id,restaurant_id) REFERENCES menu_categories(id,restaurant_id),
 UNIQUE(id,restaurant_id),
 UNIQUE(category_id,position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX menu_items_restaurant_idx ON menu_items(restaurant_id,active,id);
CREATE TABLE branch_menu_item_overrides (
 branch_id UUID NOT NULL,
 item_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 available BOOLEAN,
 price NUMERIC(12,2) CHECK (price>=0 AND price<>'NaN'::numeric),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(branch_id,item_id),
 FOREIGN KEY(branch_id,restaurant_id) REFERENCES branches(id,restaurant_id),
 FOREIGN KEY(item_id,restaurant_id) REFERENCES menu_items(id,restaurant_id),
 CHECK (available IS NOT NULL OR price IS NOT NULL)
);
CREATE INDEX menu_overrides_item_idx ON branch_menu_item_overrides(item_id,restaurant_id);
CREATE FUNCTION protect_menu_context() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.restaurant_id<>OLD.restaurant_id THEN
   RAISE EXCEPTION 'Menu restaurant cannot change' USING ERRCODE='23514';
 END IF;
 IF TG_TABLE_NAME='menu_categories' THEN
   IF NEW.menu_id<>OLD.menu_id THEN RAISE EXCEPTION 'Category menu cannot change' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='menu_items' THEN
   IF NEW.category_id<>OLD.category_id THEN RAISE EXCEPTION 'Item category cannot change' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER menu_context_guard BEFORE UPDATE ON restaurant_menus FOR EACH ROW EXECUTE FUNCTION protect_menu_context();
CREATE TRIGGER category_context_guard BEFORE UPDATE ON menu_categories FOR EACH ROW EXECUTE FUNCTION protect_menu_context();
CREATE TRIGGER item_context_guard BEFORE UPDATE ON menu_items FOR EACH ROW EXECUTE FUNCTION protect_menu_context();
