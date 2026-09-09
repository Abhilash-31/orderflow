-- Creates one database per service. This is a pragmatic single-container
-- compromise for local development: each service still only ever talks to
-- its own database (no cross-database joins, no shared schema), which is
-- the part of "database-per-service" that actually matters architecturally.
-- In the real AWS deployment (see infra/terraform, future work) each service
-- gets its own RDS instance instead.
CREATE DATABASE inventorydb;
CREATE DATABASE paymentdb;
CREATE DATABASE shippingdb;
-- orderdb is created automatically via POSTGRES_DB in docker-compose.yml
