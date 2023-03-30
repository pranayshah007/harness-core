-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

---------- SERVICE_LEVEL_OBJECTIVE TABLE START ------------
BEGIN;
CREATE TABLE IF NOT EXISTS SERVICE_LEVEL_OBJECTIVE (
                                                       REPORTEDAT TIMESTAMPTZ NOT NULL,
                                                       UPDATEDAT TIMESTAMPTZ DEFAULT (NOW()),
                                                       ACCOUNTID TEXT,
                                                       ORGID TEXT,
                                                       PROJECTID TEXT,
                                                       SLOID TEXT,
                                                       SLONAME TEXT,
                                                       USERJOURNEY TEXT,
                                                       PERIODLENGTH INTEGER,
                                                       SLITYPE TEXT,
                                                       PERIODTYPE TEXT,
                                                       SLOPERCENTAGE DECIMAL,
                                                       TOTALERRORBUDGET INTEGER,
                                                       SERVICE TEXT,
                                                       ENV TEXT,
    CONSTRAINT SERVICE_LEVEL_OBJECTIVE_UNIQUE_RECORD_INDEX UNIQUE(ACCOUNTID,ORGID,PROJECTID,SLOID)
    );
COMMIT;

---------- SERVICE_LEVEL_OBJECTIVE TABLE END ------------
