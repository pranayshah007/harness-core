-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- MODULE LICENSES TABLE START ------------
BEGIN;

CREATE TABLE IF NOT EXISTS public.module_licenses (
    id text  NOT NULL,
    account_identifier text  NOT NULL,
    module_type text NOT NULL,
    edition text  NOT NULL,
    license_type text NOT NULL,
    expiry_time bigint  NULL,
    start_time bigint  NULL,
    premium_support boolean  NULL,
    trial_extended boolean NULL,
    self_service boolean  NULL,
    created_at bigint  NULL,
    last_updated_at bigint  NULL,
    PRIMARY KEY (id)
 );
COMMIT;