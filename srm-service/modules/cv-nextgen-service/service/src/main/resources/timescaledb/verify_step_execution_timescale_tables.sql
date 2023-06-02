-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

-- Start of table [verify_step_execution_cvng] commands

BEGIN;
 CREATE TABLE IF NOT EXISTS verify_step_execution_cvng (
     id text  NOT NULL,
     accountId text  NOT NULL,
     orgIdentifier text NOT NULL,
     projectIdentifier text NOT NULL,
     planExecutionId text NULL,
     stageStepId text NULL,
     nodeExecutionId text NULL,
     startedAtTimestamp bigint  NOT NULL,
     deploymentStartedAtTimestamp bigint  NOT NULL,
     lastUpdatedAtTimestamp bigint  NOT NULL,
     verificationStatus text NULL,
     executionnStatus text NULL,
     monitoredServiceType text NULL,
     appliedVerificationTypes text[] NULL,
     selectedVerificationType text NOT NULL,
     monitoredServiceIdentifier text NOT NULL,
     serviceRef text NOT NULL,
     envRef text NOT NULL,
     sensitivity text NOT NULL,
     durationInMinutes bigint NOT NULL
     );
COMMIT;

BEGIN;
    CREATE UNIQUE INDEX IF NOT EXISTS verify_step_execution_cvng_pkey ON verify_step_execution_cvng USING btree (id);
    CREATE INDEX IF NOT EXISTS accountId_idx ON verify_step_execution_cvng USING btree (accountId);
    CREATE INDEX IF NOT EXISTS startedAtTimestamp_idx ON verify_step_execution_cvng USING btree (startedAtTimestamp);
COMMIT;

BEGIN;
    alter table verify_step_execution_cvng alter column deploymentstartedattimestamp drop not null;
    alter table verify_step_execution_cvng alter column sensitivity drop not null;
COMMIT;

-- End of table [verify_step_execution_cvng] commands




-- Start of table [health_source_cvng] commands

BEGIN;
 CREATE TABLE IF NOT EXISTS health_source_cvng (
     id text NOT NULL,
     accountId text NOT NULL,
     orgIdentifier text NOT NULL,
     projectIdentifier text NOT NULL,
     healthSourceIdentifier text NOT NULL,
     name text NOT NULL,
     providerType text NULL,
     type text NULL,
     numberOfManualQueries bigint NOT NULL,
     verificationJobInstanceId text NULL
     );
COMMIT;

BEGIN;
    CREATE UNIQUE INDEX IF NOT EXISTS health_source_cvng_pkey ON health_source_cvng USING btree (id);
    CREATE INDEX IF NOT EXISTS accountId_idx ON health_source_cvng USING btree (accountId);
    CREATE INDEX IF NOT EXISTS verificationJobInstanceId_idx ON health_source_cvng USING btree (verificationJobInstanceId);
    CREATE INDEX IF NOT EXISTS dataSourceType_idx ON health_source_cvng USING btree (type);
COMMIT;

-- End of table [health_source_cvng] commands




-- Start of table [verify_step_interrupt_cvng] commands

BEGIN;
 CREATE TABLE IF NOT EXISTS verify_step_interrupt_cvng (
     id text  NOT NULL,
     planExecutionId text  NOT NULL,
     nodeExecutionId text NOT NULL,
     type text NOT NULL,
     createdAtTimestamp bigint  NOT NULL);
COMMIT;

BEGIN;
    CREATE UNIQUE INDEX IF NOT EXISTS verify_step_interrupt_cvng_pkey ON verify_step_interrupt_cvng USING btree (id);
    CREATE INDEX IF NOT EXISTS nodeExecutionId_idx ON verify_step_interrupt_cvng USING btree (nodeExecutionId);
COMMIT;

BEGIN;
ALTER TABLE verify_step_interrupt_cvng ADD COLUMN issuerType TEXT NULL;
COMMIT;

-- End of table [verify_step_interrupt_cvng] commands