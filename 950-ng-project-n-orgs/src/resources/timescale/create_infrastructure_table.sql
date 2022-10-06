-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.infrastructures (
    id text NOT NULL,
    identifier text,
    name text,
    account_id text,
    org_identifier text,
    project_identifier text,
    env_identifier text,
    created_at bigint,
    last_modified_at bigint,
    type text,
    region text,
    cluster text,
    subscription_id text,
    namespace text,
    resource_group text
);
ALTER TABLE ONLY public.infrastructures
    ADD CONSTRAINT infrastructures_pkey PRIMARY KEY (id);

COMMIT;
