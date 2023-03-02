-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

ALTER TABLE pipeline_execution_summary_cd DROP COLUMN IF EXISTS moduleinfo_type , DROP COLUMN IF  EXISTS author_name , DROP COLUMN IF  EXISTS moduleinfo_author_id , DROP COLUMN IF  EXISTS author_avatar , DROP COLUMN IF  EXISTS moduleinfo_repository , DROP COLUMN IF  EXISTS moduleinfo_branch_name , DROP COLUMN IF  EXISTS source_branch , DROP COLUMN IF  EXISTS moduleinfo_event , DROP COLUMN IF  EXISTS moduleinfo_branch_commit_id , DROP COLUMN IF  EXISTS moduleinfo_branch_commit_message ;

COMMIT;
