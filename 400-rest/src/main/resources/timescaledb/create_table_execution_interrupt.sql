BEGIN;
CREATE TABLE IF NOT EXISTS EXECUTION_INTERRUPT (
    ID TEXT NOT NULL,
    ACCOUNT_ID TEXT NOT NULL,
    APP_ID TEXT NOT NULL,
    EXECUTION_ID TEXT NOT NULL,
    STATE_EXECUTION_INSTANCE_ID TEXT,
    TYPE TEXT NOT NULL,
    CREATED_BY TEXT,
    CREATED_AT BIGINT NOT NULL,
    LAST_UPDATED_BY TEXT,
    LAST_UPDATED_AT BIGINT,
    PRIMARY KEY(ID,CREATED_AT)
);
COMMIT;


SELECT CREATE_HYPERTABLE('EXECUTION_INTERRUPT','created_at',if_not_exists => TRUE,migrate_data => true,chunk_time_interval => 86400000000);

CREATE OR REPLACE FUNCTION unix_now() returns BIGINT LANGUAGE SQL STABLE as $$ SELECT extract(epoch from now())::BIGINT $$;

SELECT set_integer_now_func('EXECUTION_INTERRUPT', 'unix_now');
SELECT add_retention_policy('EXECUTION_INTERRUPT', BIGINT '86400000000');
