-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Create a partial index that includes only non-NULL ORGID and PROJECTID values
-- Create a custom unique index that treats (ACCOUNTID, ORGID, PROJECTID) as unique

-- drop the existing tables as they might have duplicate values with new constraint
DELETE FROM SERVICE_LEVEL_OBJECTIVE;
DELETE FROM SLO_HEALTH_INDICATOR;
DELETE FROM SLO_HISTORY;

-- Add a custom column in SERVICE_LEVEL_OBJECTIVE to represent the combination of ACCOUNTID, ORGID, and PROJECTID
ALTER TABLE SERVICE_LEVEL_OBJECTIVE
ADD COLUMN IF NOT EXISTS CUSTOM_ACCOUNT_ORG_PROJECT TEXT;

-- Add a custom column in SLO_HEALTH_INDICATOR to represent the combination of ACCOUNTID, ORGID, and PROJECTID
ALTER TABLE SLO_HEALTH_INDICATOR
ADD COLUMN IF NOT EXISTS CUSTOM_ACCOUNT_ORG_PROJECT TEXT;

-- Add a custom column to represent the combination of ACCOUNTID, ORGID, and PROJECTID
ALTER TABLE SLO_HISTORY
ADD COLUMN IF NOT EXISTS CUSTOM_ACCOUNT_ORG_PROJECT TEXT;


ALTER TABLE SLO_HISTORY
DROP CONSTRAINT IF EXISTS SLO_HISTORY_UNIQUE_RECORD_INDEX;

-- Next, create a new UNIQUE constraint with the updated columns
ALTER TABLE SLO_HISTORY
ADD CONSTRAINT SLO_HISTORY_UNIQUE_RECORD_INDEX UNIQUE(CUSTOM_ACCOUNT_ORG_PROJECT, SLOID, STARTTIME, ENDTIME);

-- drop the existing UNIQUE constraint
ALTER TABLE SERVICE_LEVEL_OBJECTIVE
DROP CONSTRAINT IF EXISTS SERVICE_LEVEL_OBJECTIVE_UNIQUE_RECORD_INDEX;

-- Next, create a new UNIQUE constraint with the updated columns
ALTER TABLE SERVICE_LEVEL_OBJECTIVE
ADD CONSTRAINT SERVICE_LEVEL_OBJECTIVE_UNIQUE_RECORD_INDEX UNIQUE(CUSTOM_ACCOUNT_ORG_PROJECT, SLOID);

-- drop the existing UNIQUE constraint
ALTER TABLE SLO_HEALTH_INDICATOR
DROP CONSTRAINT IF EXISTS SLO_HEALTH_INDICATOR_UNIQUE_RECORD_INDEX;

-- Next, create a new UNIQUE constraint with the updated columns
ALTER TABLE SLO_HEALTH_INDICATOR
ADD CONSTRAINT SLO_HEALTH_INDICATOR_UNIQUE_RECORD_INDEX UNIQUE(CUSTOM_ACCOUNT_ORG_PROJECT, SLOID);
