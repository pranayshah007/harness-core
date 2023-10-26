/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.event;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.CIResourceCleanup.CIResourceCleanupResponseKeys;
import io.harness.app.beans.entities.InfraResourceDetails;
import io.harness.app.beans.entities.ResourceDetails;
import io.harness.ci.execution.execution.StageCleanupUtility;
import io.harness.persistence.HPersistence;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CI)
@Slf4j
public class CIInfraCleanupService implements CIResourceCleanupService {
  @Inject private HPersistence persistence;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StageCleanupUtility stageCleanupUtility;
  private static final long MAX_PROCESSING_DURATION_MILLIS = 1800000L; // 30 min
  private static final long TTL = 86400000L; // 24hrs
  private static final int MAX_RETRY_COUNT = 10;

  public void run() {
    String stageExecutionID = "";
    String planExecutionID = "";
    try {
      Query<CIResourceCleanup> query =
          persistence.createQuery(CIResourceCleanup.class, excludeAuthority)
              .filter(CIResourceCleanupResponseKeys.shouldStart, true)
              .filter(CIResourceCleanupResponseKeys.type, ResourceDetails.Type.INFRA.toString())
              .field(CIResourceCleanupResponseKeys.processAfter)
              .lessThan(currentTimeMillis() - MAX_PROCESSING_DURATION_MILLIS);

      UpdateOperations<CIResourceCleanup> updateOperations =
          persistence.createUpdateOperations(CIResourceCleanup.class)
              .set(CIResourceCleanupResponseKeys.processAfter, currentTimeMillis())
              .inc(CIResourceCleanupResponseKeys.retryCount, 1);
      CIResourceCleanup lockedCIResourceCleanup =
          persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);
      if (lockedCIResourceCleanup == null) {
        return;
      }
      stageExecutionID = lockedCIResourceCleanup.getStageExecutionId();
      planExecutionID = lockedCIResourceCleanup.getPlanExecutionId();

      ResourceDetails resourceDetails = (ResourceDetails) kryoSerializer.asObject(lockedCIResourceCleanup.getData());
      // should not happen ideally
      if (resourceDetails == null) {
        log.warn("ResourceDetails is null, deleting entry for planExecutionId {}, stageExecutionID {}", planExecutionID,
            stageExecutionID);
        delete(lockedCIResourceCleanup);
        return;
      }
      // delete expired documents
      if (lockedCIResourceCleanup.getRetryCount() > MAX_RETRY_COUNT
          || lockedCIResourceCleanup.getCreatedAt() + TTL < currentTimeMillis()) {
        log.warn("Deleting expired cleanup task for planExecutionId {}, stageExecutionID {}", planExecutionID,
            stageExecutionID);
        delete(lockedCIResourceCleanup);
        return;
      }
      InfraResourceDetails infraResourceDetails = (InfraResourceDetails) resourceDetails;
      stageCleanupUtility.submitCleanupRequest(
          infraResourceDetails.getAmbiance(), lockedCIResourceCleanup.getStageExecutionId());
    } catch (Exception ex) {
      log.warn(String.format("Ignoring async cleanup task for planExecutionId %s, stageExecutionID %s, error: %s",
                   planExecutionID, stageExecutionID, ex.getMessage()),
          ex);
    }
  }

  private void delete(CIResourceCleanup ciResourceCleanup) {
    boolean deleteSuccessful = persistence.delete(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)
                                                      .field(CIResourceCleanupResponseKeys.stageExecutionId)
                                                      .equal(ciResourceCleanup.getStageExecutionId()));

    if (deleteSuccessful) {
      log.info("Deleted cleanup task for planExecutionId {}, stageExecutionID {}",
          ciResourceCleanup.getPlanExecutionId(), ciResourceCleanup.getStageExecutionId());
    }
  }
}
