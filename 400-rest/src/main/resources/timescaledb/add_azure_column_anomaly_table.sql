-- Copyright 2020 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;
ALTER TABLE ANOMALIES ADD COLUMN IF NOT EXISTS AZURESUBSCRIPTIONGUID TEXT;
ALTER TABLE ANOMALIES ADD COLUMN IF NOT EXISTS AZURERESOURCEGROUP TEXT;
ALTER TABLE ANOMALIES ADD COLUMN IF NOT EXISTS AZUREMETERCATEGORY TEXT;
COMMIT;
