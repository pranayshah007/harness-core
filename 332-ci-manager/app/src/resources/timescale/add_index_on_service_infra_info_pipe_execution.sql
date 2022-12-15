-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;
CREATE INDEX IF NOT EXISTS service_infra_info_pipeline_execution_idx ON service_infra_info USING btree (pipeline_execution_summary_cd_id);
CREATE INDEX IF NOT EXISTS pipeline_summery_ci_account_org_proj_idx ON pipeline_execution_summary_ci USING btree (accountid, orgidentifier, projectidentifier);
COMMIT;
