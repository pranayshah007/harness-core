/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PopulateVerificationStatusInVerificationJobInstances extends CVNGBaseMigration {
  @Inject private HPersistence hPersistence;

  private static final List<ExecutionStatus> errorExecutionStatuses =
      List.of(ExecutionStatus.ABORTED, ExecutionStatus.FAILED, ExecutionStatus.TIMEOUT);

  @Override
  public void migrate() {
    log.info("Starting update of missing verification statuses in verificationJobInstances.");
    Query<VerificationJobInstance> queryToGetVerificationJobInstances =
        hPersistence.createQuery(VerificationJobInstance.class, new HashSet<>())
            .field(VerificationJobInstanceKeys.executionStatus)
            .in(errorExecutionStatuses)
            .field(VerificationJobInstanceKeys.verificationStatus)
            .doesNotExist();
    try (HIterator<VerificationJobInstance> iterator = new HIterator<>(queryToGetVerificationJobInstances.fetch())) {
      while (iterator.hasNext()) {
        try {
          VerificationJobInstance verificationJobInstance = iterator.next();
          ensureVerificationStatusIsPopulated(verificationJobInstance);
        } catch (Exception ignored) {
        }
      }
    }
    log.info("Completed update of missing verification statuses in verificationJobInstances.");
  }

  private void ensureVerificationStatusIsPopulated(VerificationJobInstance verificationJobInstance) {
    log.info("Checking verification status for VerificationJobInstanceId {}.", verificationJobInstance.getUuid());
    if (Objects.isNull(verificationJobInstance.getVerificationStatus())
        && errorExecutionStatuses.contains(verificationJobInstance.getExecutionStatus())) {
      try {
        populateVerificationStatus(verificationJobInstance);
      } catch (Exception ex) {
        log.error("Exception while populating verification status for VerificationJobInstanceId {}.",
            verificationJobInstance.getUuid());
      }
    }
  }

  private void populateVerificationStatus(VerificationJobInstance verificationJobInstance) {
    log.info("Updating verification status for VerificationJobInstanceId {}.", verificationJobInstance.getUuid());
    ActivityVerificationStatus verificationStatus = getVerificationStatusFromExecutionStatus(verificationJobInstance);
    UpdateOperations<VerificationJobInstance> verificationJobInstanceUpdateOperations =
        hPersistence.createUpdateOperations(VerificationJobInstance.class)
            .set(VerificationJobInstanceKeys.verificationStatus, verificationStatus);
    hPersistence.getDatastore(VerificationJobInstance.class)
        .update(hPersistence.createQuery(VerificationJobInstance.class)
                    .filter(VerificationJobInstanceKeys.uuid, verificationJobInstance.getUuid()),
            verificationJobInstanceUpdateOperations);
    log.info("Updated verification status {} for VerificationJobInstanceId {}.", verificationStatus,
        verificationJobInstance.getUuid());
  }

  private static ActivityVerificationStatus getVerificationStatusFromExecutionStatus(
      VerificationJobInstance verificationJobInstance) {
    switch (verificationJobInstance.getExecutionStatus()) {
      case FAILED:
      case TIMEOUT:
        return ActivityVerificationStatus.ERROR;
      case ABORTED:
        return ActivityVerificationStatus.ABORTED;
      default:
        throw new IllegalStateException("Unsupported ExecutionStatus " + verificationJobInstance.getExecutionStatus());
    }
  }
}
