-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.ng_users (
                                                      id text  NOT NULL,
                                                      name text  NULL,
                                                      email text NULL,
                                                      created_at bigint  NOT NULL,
                                                      last_modified_at bigint  NULL,
                                                      PRIMARY KEY (id)
    );
COMMIT;
