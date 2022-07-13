BEGIN;

ALTER TABLE service_infra_info ADD COLUMN infrastructureIdentifier text;

COMMIT;