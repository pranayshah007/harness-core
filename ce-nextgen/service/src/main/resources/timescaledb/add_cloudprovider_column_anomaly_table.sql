-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

ALTER TABLE anomalies ADD COLUMN IF NOT EXISTS cloudprovider TEXT;

UPDATE anomalies SET cloudprovider = 'CLUSTER' where clustername is not null;
UPDATE anomalies SET cloudprovider = 'GCP' where gcpproject is not null;
UPDATE anomalies SET cloudprovider = 'AWS' where awsaccount is not null;
UPDATE anomalies SET cloudprovider = 'AZURE' where azuresubscriptionguid is not null;

COMMIT;