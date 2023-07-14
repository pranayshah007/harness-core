-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.cd_stage_helm_manifest_info (
                                                      id text  NOT NULL,
                                                      type text NULL,
                                                      stage_execution_id text  NULL,
                                                      helm_version text  NULL,
                                                      PRIMARY KEY (id, stage_execution_id)
    );
COMMIT;