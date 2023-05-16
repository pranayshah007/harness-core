-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.cd_stage_execution (
                                                      id text  NOT NULL,
                                                      service_id text NULL,
                                                      artifact_display_name text  NULL,
                                                      env_id text  NULL,
                                                      env_name text  NULL,
                                                      env_group_id text  NULL,
                                                      env_group_name text  NULL,
                                                      infra_id text  NULL,
                                                      infra_name text  NULL,
                                                      failure_message text  NULL,
                                                      rollback_duration bigint  NULL,
                                                      PRIMARY KEY (id)
    );
COMMIT;