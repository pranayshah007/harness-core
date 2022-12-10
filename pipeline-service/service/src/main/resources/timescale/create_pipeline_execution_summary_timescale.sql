-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- PIPELINE EXECUTION SUMMARY TABLE START ------------
BEGIN;
 CREATE TABLE IF NOT EXISTS pipeline_execution_summary (
     id text  NOT NULL,
     accountid text  NULL,
     orgidentifier text  NULL,
     projectidentifier text  NULL,
     pipelineidentifier text  NULL,
     name text  NULL,
     status text  NULL,
     author_avatar text  NULL,
     author_name text  NULL,
     author_id text  NULL,
     startts bigint  NOT NULL,
     endts bigint  NULL,
     trigger_type text  NULL,
     planexecutionid text  NULL);
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS pipeline_execution_summary_pkey ON pipeline_execution_summary USING btree (id, startts);
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_startts_idx ON pipeline_execution_summary USING btree (startts DESC);
COMMIT;

BEGIN;
SELECT CREATE_HYPERTABLE('pipeline_execution_summary','startts', chunk_time_interval => 7 * 86400000, if_not_exists => TRUE, migrate_data => true);
COMMIT;

---------- PIPELINE EXECUTION SUMMARY TABLE END ------------