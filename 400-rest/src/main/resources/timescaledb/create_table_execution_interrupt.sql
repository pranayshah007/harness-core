-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;
CREATE TABLE IF NOT EXISTS EXECUTION_INTERRUPT (
    ID TEXT NOT NULL,
    ACCOUNT_ID TEXT NOT NULL,
    APP_ID TEXT NOT NULL,
    EXECUTION_ID TEXT NOT NULL,
    STATE_EXECUTION_INSTANCE_ID TEXT,
    TYPE TEXT NOT NULL,
    CREATED_BY TEXT,
    CREATED_AT TIMESTAMP NOT NULL,
    LAST_UPDATED_BY TEXT,
    LAST_UPDATED_AT TIMESTAMP,
    PRIMARY KEY(ID,CREATED_AT)
);
COMMIT;

CREATE INDEX IF NOT EXISTS execution_interrupt_account_id_idx ON execution_interrupt USING btree (account_id);
CREATE INDEX IF NOT EXISTS execution_interrupt_last_updated_at_idx ON execution_interrupt USING btree (last_updated_at);

SELECT CREATE_HYPERTABLE('EXECUTION_INTERRUPT','created_at',if_not_exists => TRUE,migrate_data => true,chunk_time_interval => INTERVAL '7 days');

SELECT add_retention_policy('EXECUTION_INTERRUPT', INTERVAL '3 months');
