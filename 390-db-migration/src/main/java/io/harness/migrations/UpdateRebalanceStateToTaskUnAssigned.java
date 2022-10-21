package io.harness.migrations;

import static io.harness.perpetualtask.PerpetualTaskState.TASK_UNASSIGNED;

import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;

public class UpdateRebalanceStateToTaskUnAssigned implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    persistence.update(persistence.createQuery(PerpetualTaskRecord.class)
                           .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_TO_REBALANCE),
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED));
  }
}
