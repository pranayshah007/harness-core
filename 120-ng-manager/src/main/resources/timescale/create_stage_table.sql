-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.stage_execution (
                                                      id text  NOT NULL,
                                                      name text  NULL,
                                                      status text NULL,
                                                      duration bigint NULL,
                                                      account_identifier text  NULL,
                                                      org_identifier text  NULL,
                                                      project_identifier text  NULL,
                                                      pipeline_identifier text  NULL,
                                                      plan_execution_id text  NULL,
                                                      stage_execution_id text  NULL,
                                                      type text  NULL,
                                                      start_time bigint  NULL,
                                                      end_time bigint  NULL,
                                                      PRIMARY KEY (id)
    );
COMMIT;