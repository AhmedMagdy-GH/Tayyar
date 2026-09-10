CREATE TABLE foundation_probe (
    id UUID PRIMARY KEY,
    label VARCHAR(80) NOT NULL
);

REVOKE ALL ON TABLE flyway_schema_history FROM tayyar_app;
