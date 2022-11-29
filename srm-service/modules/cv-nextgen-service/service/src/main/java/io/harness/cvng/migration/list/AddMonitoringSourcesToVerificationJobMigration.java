/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddMonitoringSourcesToVerificationJobMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for updating Verification Job with monitoring sources");
    Query<VerificationJob> verificationJobQuery = hPersistence.createQuery(VerificationJob.class);

    try (HIterator<VerificationJob> iterator = new HIterator<>(verificationJobQuery.fetch())) {
      if (iterator.hasNext()) {
        VerificationJob verificationJob = iterator.next();

        List<String> monitoringSources = getMonitoringSourcesIdentifiers(verificationJob);
        hPersistence.update(
            hPersistence.createQuery(VerificationJob.class).filter(VerificationJobKeys.uuid, verificationJob.getUuid()),

            hPersistence.createUpdateOperations(VerificationJob.class)
                .set(VerificationJobKeys.monitoringSources, monitoringSources));
      }
    }
  }

  private List<String> getMonitoringSourcesIdentifiers(VerificationJob verificationJob) {
    Query<CVConfig> cvConfigQuery = hPersistence.createQuery(CVConfig.class, excludeAuthority)
                                        .filter(CVConfigKeys.accountId, verificationJob.getAccountId())
                                        .filter(CVConfigKeys.projectIdentifier, verificationJob.getProjectIdentifier())
                                        .filter(CVConfigKeys.orgIdentifier, verificationJob.getOrgIdentifier());

    List<CVConfig> cvConfigs = cvConfigQuery.asList();
    List<CVConfig> filteredConfigs =
        cvConfigs.stream()
            .filter(cvConfig -> verificationJob.getDataSources().contains(cvConfig.getType()))
            .collect(Collectors.toList());
    return filteredConfigs.stream().map(CVConfig::getIdentifier).distinct().collect(Collectors.toList());
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
