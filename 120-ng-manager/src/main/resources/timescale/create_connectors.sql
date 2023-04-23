-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

---------- CONNECTORS TABLE START ------------

BEGIN;
 CREATE TABLE IF NOT EXISTS connectors (
     id text  NOT NULL,
     account_id text  NOT NULL,
     org_identifier text  NULL,
     project_identifier text  NULL,
     identifier text  NOT NULL,
     scope text  NULL,
     name text  NOT NULL,
     type text  NULL,
     categories text  NULL,
     created_by text  NULL,
     last_updated_by text  NULL,
     created_at bigint  NOT NULL,
     last_modified_at bigint  NULL);
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS connectors_pkey ON connectors USING btree (id);
COMMIT;

---------- CONNECTOR END ------------
