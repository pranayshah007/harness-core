-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE INDEX IF NOT EXISTS ACCOUNTID_SERVICE_STARTTS_SERVICE_ENDTS_SERVICE_ID_ORGIDENTIFIER_PROJECTIDENTIFIER_ARTIFACT_DISPLAY_NAME_INDEX ON SERVICE_INFRA_INFO(ACCOUNTID, SERVICE_STARTTS, SERVICE_ENDTS, SERVICE_ID, ORGIDENTIFIER, PROJECTIDENTIFIER, ARTIFACT_DISPLAY_NAME);

COMMIT;