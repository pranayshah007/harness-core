-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

---------- SLO_HEALTH_INDICATOR TABLE START ------------
BEGIN;
CREATE TABLE IF NOT EXISTS SLO_HEALTH_INDICATOR (
                                                    ACCOUNTID TEXT,
                                                    ORGID TEXT,
                                                    PROJECTID TEXT,
                                                    SLOID TEXT,
                                                    STATUS TEXT,
                                                    ERRORBUDGETPERCENTAGE TEXT,
                                                    ERRORBUDGETREMAINING TEXT,
                                                    SLIVALUE DECIMAL,

    CONSTRAINT SLO_HEALTH_INDICATOR_UNIQUE_RECORD_INDEX UNIQUE(ACCOUNTID,ORGID,PROJECTID,SLOID)
    );
COMMIT;

---------- SLO_HEALTH_INDICATOR TABLE END ------------
