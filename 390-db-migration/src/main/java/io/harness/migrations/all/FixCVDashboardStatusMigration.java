/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.beans.ExecutionStatus;
import io.harness.migrations.Migration;
import io.harness.time.Timestamp;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData.ContinuousVerificationExecutionMetaDataKeys;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixCVDashboardStatusMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try {
      List<ContinuousVerificationExecutionMetaData> cvMetadataList =
          wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class)
              .filter(ContinuousVerificationExecutionMetaDataKeys.executionStatus, "RUNNING")
              .field(ContinuousVerificationExecutionMetaData.CREATED_AT_KEY)
              .greaterThanOrEq(Timestamp.currentMinuteBoundary() - TimeUnit.DAYS.toMillis(30))
              .asList();

      for (ContinuousVerificationExecutionMetaData cvData : cvMetadataList) {
        String stateExecId = cvData.getStateExecutionId();
        StateExecutionInstance instance = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(StateExecutionInstanceKeys.uuid, stateExecId)
                                              .get();
        ExecutionStatus realStatus = cvData.getExecutionStatus();
        if (instance != null) {
          realStatus = instance.getStatus();
        }
        UpdateOperations<ContinuousVerificationExecutionMetaData> op =
            wingsPersistence.createUpdateOperations(ContinuousVerificationExecutionMetaData.class);
        setUnset(op, "executionStatus", realStatus);
        wingsPersistence.update(wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class)
                                    .filter(ContinuousVerificationExecutionMetaDataKeys.stateExecutionId, stateExecId),
            op);
        log.info("Updating CVMetadata for {} to {}", stateExecId, realStatus);
      }
    } catch (Exception ex) {
      log.error("Exception while running FixCVDashboardStatusMigration", ex);
    }
  }
}
