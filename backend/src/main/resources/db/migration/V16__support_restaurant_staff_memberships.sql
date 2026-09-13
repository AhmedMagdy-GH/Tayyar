ALTER TABLE restaurant_memberships
 DROP CONSTRAINT restaurant_memberships_membership_type_check;

ALTER TABLE restaurant_memberships
 ADD CONSTRAINT restaurant_memberships_membership_type_check
 CHECK (membership_type IN ('OWNER', 'STAFF'));
