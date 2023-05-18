-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

ALTER TABLE CD_STAGE_EXECUTION ADD COLUMN IF NOT EXISTS ENV_TYPE text;
ALTER TABLE CD_STAGE_EXECUTION ADD COLUMN IF NOT EXISTS DEPLOYMENT_TYPE text;
ALTER TABLE CD_STAGE_EXECUTION ADD COLUMN IF NOT EXISTS SERVICE_NAME text;
