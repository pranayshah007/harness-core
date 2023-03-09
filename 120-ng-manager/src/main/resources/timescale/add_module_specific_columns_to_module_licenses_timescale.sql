-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

--- When moduleType is Chaos, Chaos specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS chaos_total_experiment_runs bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS chaos_total_infrastructures bigint;

--- When moduleType is CD, CD specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS cd_license_type text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS cd_workloads text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS cd_service_instances text;

--- When moduleType is CE, CE specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS ce_spend_limit bigint;

--- When moduleType is CF, CF specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS cf_number_of_users bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS cf_number_of_client_MAUs bigint;

--- When moduleType is CI, CI specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS ci_number_of_committers bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS ci_cache_allowance bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS ci_hosting_credits bigint;

--- When moduleType is SRM, SRM specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS srm_number_of_services bigint;

--- When moduleType is STO, STO specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS sto_number_of_developers bigint;

ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS status text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS created_by_uuid text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS created_by_name text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS created_by_email text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS created_by_external_user_id text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS last_updated_by_uuid text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS last_updated_by_name text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS last_updated_by_email text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS last_updated_by_external_user_id text;

COMMIT;