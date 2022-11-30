/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class CleanupDeprecatedDocuments implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private CVConfigService cvConfigService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Override
  public void migrate() {
    /*
    Commenting out old migration to remove dependency with activity source
    List<ActivitySource> activitySources = hPersistence.createQuery(ActivitySource.class).asList();
    for (ActivitySource activitySource : activitySources) {
      log.info("Deleting activity source: {}", activitySource);
      activitySourceService.deleteActivitySource(activitySource.getAccountId(), activitySource.getOrgIdentifier(),
          activitySource.getProjectIdentifier(), activitySource.getIdentifier());
      log.info("Deleted activity source: {}", activitySource);
    }
    */

    Query<VerificationJob> verificationJobs = hPersistence.createQuery(VerificationJob.class);
    log.info("Deleting VerificationJob source: {}", verificationJobs.count());
    hPersistence.delete(verificationJobs);
    log.info("Deleted Verification job source: {}");

    List<CVConfig> cvConfigs = hPersistence.createQuery(CVConfig.class).asList();
    for (CVConfig cvConfig : cvConfigs) {
      log.info("Deleting CVConfig source: {}", cvConfig);
      if (isOldCVConfig(cvConfig)) {
        cvConfigService.delete(cvConfig.getUuid());
        monitoringSourcePerpetualTaskService.deleteTask(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
            cvConfig.getProjectIdentifier(), cvConfig.getIdentifier());
        log.info("Deleted CVConfig job source: {}", cvConfig);
      } else {
        log.info("CVConfig belong to monitored service", cvConfig);
      }
    }
  }

  private boolean isOldCVConfig(CVConfig cvConfig) {
    return !cvConfig.getIdentifier().contains("/");
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
