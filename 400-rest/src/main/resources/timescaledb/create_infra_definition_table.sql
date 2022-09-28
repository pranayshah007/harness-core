-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.cg_infra_definition (
    id text NOT NULL,
    name text NOT NULL,
    account_id text,
    app_id text,
    created_at bigint,
    last_updated_at bigint,
    created_by text,
    last_updated_by text,
    cloud_provider_id text,
    cloud_provider_type text,
    deployment_type text,
    namespace text,
    region text ,
    autoScaling_group_name text,
    resource_group text,
    resource_group_name text,
    subscription_id text,
    deployment_group text,
    username text,
    organization text,
    cluster_name text,
    PRIMARY KEY(id)
);

COMMIT;