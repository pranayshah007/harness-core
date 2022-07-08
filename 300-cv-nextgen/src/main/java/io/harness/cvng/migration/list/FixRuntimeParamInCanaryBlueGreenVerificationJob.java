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
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.CanaryVerificationJob.CanaryVerificationJobKeys;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.persistence.HPersistence;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class FixRuntimeParamInCanaryBlueGreenVerificationJob implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Override
  public void migrate() {
    List<VerificationJob> verificationJobList =
        hPersistence.createQuery(VerificationJob.class)
            .field(CanaryVerificationJobKeys.type)
            .in(Lists.newArrayList(VerificationJobType.CANARY, VerificationJobType.BLUE_GREEN))
            .asList();

    verificationJobList.forEach(verificationJob -> {
      UpdateOperations<VerificationJob> updateOperations = hPersistence.createUpdateOperations(VerificationJob.class);
      if (verificationJob.isDefaultJob()
          || ((CanaryBlueGreenVerificationJob) verificationJob).getTrafficSplitPercentage() == null) {
        updateOperations.set("trafficSplitPercentageV2.value", "<+input>");
        updateOperations.set("trafficSplitPercentageV2.isRuntimeParam", true);
      } else {
        updateOperations.set("trafficSplitPercentageV2.value",
            ((CanaryBlueGreenVerificationJob) verificationJob).getTrafficSplitPercentage());
        updateOperations.set("trafficSplitPercentageV2.isRuntimeParam", false);
      }
      hPersistence.update(verificationJob, updateOperations);
      log.info("Migrated Traffic Split Percentage for Verification Job: " + verificationJob.getIdentifier());
    });
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
