-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.projects (
    id text NOT NULL,
    identifier text,
    name text,
    deleted boolean,
    org_identifier text,
    last_modified_at bigint,
    created_at bigint,
    account_identifier text
);
-- drop constraint first if it exists
ALTER TABLE ONLY public.projects
    DROP CONSTRAINT IF EXISTS projects_pkey;

-- add primary key constraint, this is done to resolve issues in re-running migrations
-- this query will fail as only one primary key constraint can exist
ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);

COMMIT;
