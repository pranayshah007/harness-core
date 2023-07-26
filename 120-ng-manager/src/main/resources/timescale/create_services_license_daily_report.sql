-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- SERVICES_LICENSE_DAILY_REPORT TABLE START ------------
BEGIN;
CREATE TABLE IF NOT EXISTS PUBLIC.SERVICES_LICENSE_DAILY_REPORT (
	ACCOUNT_ID TEXT NOT NULL,
	REPORTED_DAY DATE NOT NULL,
	LICENSE_COUNT INTEGER,
	PRIMARY KEY(ACCOUNT_ID, REPORTED_DAY)
);
COMMIT;

BEGIN;
CREATE INDEX IF NOT EXISTS SERVICES_LICENSE_ACCOUNT_ID_INDEX ON SERVICES_LICENSE_DAILY_REPORT(ACCOUNT_ID);
CREATE INDEX IF NOT EXISTS SERVICES_LICENSE_ACCOUNT_ID_REPORTED_DAY_INDEX ON SERVICES_LICENSE_DAILY_REPORT(ACCOUNT_ID,REPORTED_DAY DESC);
COMMIT;

---------- SERVICES_LICENSE_DAILY_REPORT TABLE END ------------