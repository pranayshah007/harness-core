/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations;

import static io.harness.perpetualtask.PerpetualTaskState.TASK_UNASSIGNED;

import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateK8sWatchInvalidStatePerpetualTaskMigration implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    log.info("Starting migration of CCM perpetual task type from Invalid to unassigned");
    persistence.update(persistence.createQuery(PerpetualTaskRecord.class)
                           .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_INVALID)
                           .filter(PerpetualTaskRecordKeys.perpetualTaskType, PerpetualTaskType.K8S_WATCH),
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED));

    log.info("Migration completed, State changed to task unassigned");
  }
}
