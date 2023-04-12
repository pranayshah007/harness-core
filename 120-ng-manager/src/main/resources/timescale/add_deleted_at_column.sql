-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted_at bigint NULL;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS deleted_at bigint NULL;
ALTER TABLE environments ADD COLUMN IF NOT EXISTS deleted_at bigint NULL;
ALTER TABLE infrastructures ADD COLUMN IF NOT EXISTS deleted boolean DEFAULT false NULL;
ALTER TABLE infrastructures ADD COLUMN IF NOT EXISTS deleted_at bigint NULL;
ALTER TABLE services ADD COLUMN IF NOT EXISTS deleted_at bigint NULL;
ALTER TABLE pipelines ADD COLUMN IF NOT EXISTS deleted_at bigint NULL;

COMMIT;
