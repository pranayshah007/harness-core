-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

ALTER TABLE custom_stage_execution ADD COLUMN IF NOT EXISTS env_id text NULL;
ALTER TABLE custom_stage_execution ADD COLUMN IF NOT EXISTS env_name text NULL;
ALTER TABLE custom_stage_execution ADD COLUMN IF NOT EXISTS env_type text NULL;
ALTER TABLE custom_stage_execution ADD COLUMN IF NOT EXISTS infra_id text NULL;
ALTER TABLE custom_stage_execution ADD COLUMN IF NOT EXISTS infra_name text NULL;

COMMIT;
