-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.execution_tags_info_ng(
    id text NOT NULL,
    account_id text NOT NULL,
    org_id text,
    project_id text,
    parent_type text NOT NULL, -- type of record(pipeline/stage execution)
    parent_id text NOT NULL, -- id of the record(plan/stage execution id)
    tags text[],
    PRIMARY KEY(id, parent_type));

COMMIT;

CREATE INDEX IF NOT EXISTS account_id_parent_type_parent_id_org_id_project_id_index ON execution_tags_info_ng(account_id,parent_type,parent_id,org_id,project_id);
