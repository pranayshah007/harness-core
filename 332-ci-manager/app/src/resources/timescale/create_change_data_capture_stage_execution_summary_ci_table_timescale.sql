-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- STAGE EXECUTION SUMMARY CI TABLE START ------------
BEGIN;
 CREATE TABLE IF NOT EXISTS stage_execution_summary_ci (
     id text NOT NULL,
     accountidentifier text NULL,
     orgidentifier text NULL,
     projectidentifier text NULL,
     pipelineidentifier text NULL,
     stageidentifier text NULL,
     pipelinename text NULL,
     stagename text NULL,
     planexecutionid text NULL,
     stageexecutionid text NOT NULL,
     cputime bigint NULL,
     stagebuildtime bigint NULL,
     infratype text NULL,
     ostype text NULL,
     osarch text NULL,
     startts bigint NOT NULL,
     buildmultiplier double precision DEFAULT 1);
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS stage_execution_summary_ci_pkey ON stage_execution_summary_ci USING btree (id, stageexecutionid, startts);
CREATE INDEX IF NOT EXISTS stage_execution_summary_ci_startts_idx ON stage_execution_summary_ci USING btree (startts DESC);
COMMIT;

BEGIN;
SELECT CREATE_HYPERTABLE('stage_execution_summary_ci','startts', chunk_time_interval => 7 * 86400000, if_not_exists => TRUE, migrate_data => true);
COMMIT;

---------- STAGE EXECUTION SUMMARY CI TABLE END ------------