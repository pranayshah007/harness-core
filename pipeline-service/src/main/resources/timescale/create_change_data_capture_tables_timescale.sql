-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- PIPELINE EXECUTION SUMMARY CI TABLE START ------------
BEGIN;
 CREATE TABLE IF NOT EXISTS pipeline_execution_summary_ci (
     id text  NOT NULL,
     accountid text  NOT NULL,
     orgidentifier text  NULL,
     projectidentifier text  NULL,
     pipelineidentifier text  NULL,
     name text  NULL,
     status text  NULL,
     moduleinfo_type text  NULL,
     moduleinfo_event text  NULL,
     moduleinfo_author_id text  NULL,
     moduleinfo_repository text  NULL,
     moduleinfo_branch_name text  NULL,
     moduleinfo_branch_commit_id text  NULL,
     moduleinfo_branch_commit_message text  NULL,
     author_name text  NULL,
     author_avatar text  NULL,
     startts bigint  NOT NULL,
     endts bigint  NULL,
     planexecutionid text  NULL,
     errormessage text  NULL,
     trigger_type text  NULL,
     source_branch text  NULL);
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS pipeline_execution_summary_ci_pkey ON pipeline_execution_summary_ci USING btree (id, startts);
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_ci_startts_idx ON pipeline_execution_summary_ci USING btree (startts DESC);
COMMIT;

BEGIN;
SELECT CREATE_HYPERTABLE('pipeline_execution_summary_ci','startts', chunk_time_interval => 7 * 86400000, if_not_exists => TRUE, migrate_data => true);
COMMIT;

---------- PIPELINE EXECUTION SUMMARY CI TABLE END ------------

---------- PIPELINE EXECUTION SUMMARY CD TABLE START ------------
BEGIN;
 CREATE TABLE IF NOT EXISTS pipeline_execution_summary_cd (
     id text  NOT NULL,
     accountid text  NULL,
     orgidentifier text  NULL,
     projectidentifier text  NULL,
     pipelineidentifier text  NULL,
     name text  NULL,
     status text  NULL,
     moduleinfo_type text  NULL,
     startts bigint  NOT NULL,
     endts bigint  NULL,
     planexecutionid text  NULL,
     trigger_type text  NULL,
     author_name text  NULL,
     moduleinfo_author_id text  NULL,
     author_avatar text  NULL,
     moduleinfo_repository text  NULL,
     moduleinfo_branch_name text  NULL,
     source_branch text  NULL,
     moduleinfo_event text  NULL,
     moduleinfo_branch_commit_id text  NULL,
     moduleinfo_branch_commit_message text  NULL);
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS pipeline_execution_summary_cd_pkey ON pipeline_execution_summary_cd USING btree (id, startts);
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_cd_startts_idx ON pipeline_execution_summary_cd USING btree (startts DESC);
COMMIT;

BEGIN;
SELECT CREATE_HYPERTABLE('pipeline_execution_summary_cd','startts', chunk_time_interval => 7 * 86400000, if_not_exists => TRUE, migrate_data => true);
COMMIT;

---------- PIPELINE EXECUTION SUMMARY CD TABLE END ------------

---------- SERVICE INFRA INFO TABLE START ------------
BEGIN;
 CREATE TABLE IF NOT EXISTS service_infra_info (
     id text  NOT NULL,
     service_name text  NULL,
     service_id text  NULL,
     tag text  NULL,
     env_name text  NULL,
     env_id text  NULL,
     env_type text  NULL,
     pipeline_execution_summary_cd_id text  NULL,
     deployment_type text  NULL,
     service_status text  NULL,
     service_startts bigint  NOT NULL,
     service_endts bigint  NULL,
     orgidentifier text  NULL,
     accountid text  NULL,
     projectidentifier text  NULL,
     executionId text  NULL,
     artifact_image text  NULL);
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS service_infra_info_pkey ON service_infra_info USING btree (id, service_startts);
CREATE INDEX IF NOT EXISTS service_infra_info_service_startts_idx ON service_infra_info USING btree (service_startts DESC);
COMMIT;

BEGIN;
SELECT CREATE_HYPERTABLE('service_infra_info','service_startts', chunk_time_interval => 7 * 86400000, if_not_exists => TRUE, migrate_data => true);
COMMIT;
---------- SERVICE INFRA INFO TABLE END ------------
