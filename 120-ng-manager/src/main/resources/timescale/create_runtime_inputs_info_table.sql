-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.runtime_inputs_info(
    id text NOT NULL,
    account_id text NOT NULL,
    org_identifier text,
    project_identifier text,
    plan_execution_id text NOT NULL,
    fqn_hash text,
    fqn text,
    display_name text,
    input_value text,
    PRIMARY KEY(id, fqn_hash));

COMMIT;
