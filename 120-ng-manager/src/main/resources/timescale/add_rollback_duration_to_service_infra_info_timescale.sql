BEGIN;

ALTER TABLE service_infra_info ADD COLUMN IF NOT EXISTS rollback_duration BIGINT;

COMMIT;