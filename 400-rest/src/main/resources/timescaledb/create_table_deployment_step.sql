-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;
CREATE TABLE IF NOT EXISTS DEPLOYMENT_STEP (
    ID TEXT NOT NULL,
    ACCOUNT_ID TEXT NOT NULL,
    APP_ID TEXT NOT NULL,
    STEP_NAME TEXT NOT NULL,
    STEP_TYPE TEXT NOT NULL,
    STATUS VARCHAR(20),
    FAILURE_DETAILS TEXT,
    START_TIME TIMESTAMP NOT NULL,
    END_TIME TIMESTAMP,
    DURATION BIGINT,
    STAGE_NAME TEXT,
    EXECUTION_ID TEXT NOT NULL,
    APPROVED_BY TEXT,
    APPROVAL_TYPE TEXT,
    APPROVED_AT TIMESTAMP,
    APPROVAL_COMMENT TEXT,
    APPROVAL_EXPIRY TIMESTAMP,
    MANUAL_INTERVENTION boolean,
    PRIMARY KEY(ID,START_TIME)
);
COMMIT;

CREATE INDEX IF NOT EXISTS deployment_step_account_id_idx ON deployment_step USING btree (account_id);
CREATE INDEX IF NOT EXISTS deployment_step_end_time_idx ON deployment_step USING btree (end_time);

SELECT CREATE_HYPERTABLE('DEPLOYMENT_STEP','start_time',if_not_exists => TRUE,migrate_data => true,chunk_time_interval => INTERVAL '7 days');

SELECT add_retention_policy('DEPLOYMENT_STEP', INTERVAL '3 months');
