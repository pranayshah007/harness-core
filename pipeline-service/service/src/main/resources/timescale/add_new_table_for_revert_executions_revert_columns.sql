-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- PIPELINE EXECUTION SUMMARY CD TABLE COLUMN ADDITION START ------------

BEGIN;
ALTER TABLE pipeline_execution_summary_cd ADD COLUMN IF NOT EXISTS original_execution_id TEXT;
ALTER TABLE pipeline_execution_summary_cd ADD COLUMN IF NOT EXISTS mean_time_to_restore BIGINT;
ALTER TABLE pipeline_execution_summary_cd ADD COLUMN IF NOT EXISTS is_revert_execution BOOLEAN NOT NULL DEFAULT FALSE;
COMMIT;

BEGIN;
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_cd_acct_original_executionid_idx ON pipeline_execution_summary_cd(accountid, original_execution_id) INCLUDE (planexecutionid);
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_cd__acct_planexecutionid_idx ON pipeline_execution_summary_cd(accountid, planexecutionid) INCLUDE (original_execution_id);
COMMIT;

---------- PIPELINE EXECUTION SUMMARY CD TABLE COLUMN ADDITION END ------------

