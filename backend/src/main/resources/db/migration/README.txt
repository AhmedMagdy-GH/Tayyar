Production migrations begin with V1__create_users_and_roles.sql in Identity.
Foundation creates no placeholder business schema. Its migration/mapping tests
use separate resources under src/test and are never packaged for production.
