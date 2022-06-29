/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;
@Slf4j
@OwnedBy(HarnessTeam.CV)
public class FixRuntimeParamsInDefaultHealthJob implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Override
  public void migrate() {
    UpdateOperations<VerificationJob> updateOperations = hPersistence.createUpdateOperations(VerificationJob.class);
    updateOperations.set("serviceIdentifier.value", "<+input>");
    updateOperations.set("envIdentifier.value", "<+input>");
    updateOperations.set("serviceIdentifier.isRuntimeParam", true);
    updateOperations.set("envIdentifier.isRuntimeParam", true);
    Query<VerificationJob> query = hPersistence.createQuery(VerificationJob.class);
    query.filter(VerificationJobKeys.isDefaultJob, true);
    query.filter("type", VerificationJobType.HEALTH);
    UpdateResults updateResults = hPersistence.update(query, updateOperations);
    log.info("Update results count: " + updateResults.getUpdatedCount());
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.builder()
        .desc("This is a migration to fix the incorrect data so after rollback  old version will have fixed data.")
        .build();
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.builder().desc("Verification job does not have any iterator.").build();
  }
}
