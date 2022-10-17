BEGIN;
CREATE TABLE IF NOT EXISTS EXECUTION_INTERRUPT (
    ID TEXT NOT NULL,
    Type TEXT NOT NULL,
    ExecutionId TEXT NOT NULL,
    AppId TEXT NOT NULL,
    CreatedBy TEXT,
    CreatedAt BIGINT NOT NULL,
    LastUpdatedBy TEXT,
    LastUpdatedAt BIGINT,
    AccountId TEXT NOT NULL,
    StateExecutionInstanceId TEXT,
    PRIMARY KEY(ID,CreatedAt)
);
COMMIT;


SELECT CREATE_HYPERTABLE('EXECUTION_INTERRUPT','createdat',if_not_exists => TRUE,migrate_data => true,chunk_time_interval => 86400000000);

CREATE OR REPLACE FUNCTION unix_now() returns BIGINT LANGUAGE SQL STABLE as $$ SELECT extract(epoch from now())::BIGINT $$;

SELECT set_integer_now_func('EXECUTION_INTERRUPT', 'unix_now');
SELECT add_retention_policy('EXECUTION_INTERRUPT', BIGINT '86400000000');
