package io.harness.migrations;

import static io.harness.perpetualtask.PerpetualTaskState.TASK_UNASSIGNED;

import io.harness.network.FibonacciBackOff;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class UpdateRebalanceStateToTaskUnAssigned implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    List<String> ptsToUpdate = new ArrayList<>();

    try (HIterator<PerpetualTaskRecord> iterator =
             new HIterator<>(persistence.createQuery(PerpetualTaskRecord.class)
                                 .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_TO_REBALANCE)
                                 .fetch())) {
      while (iterator.hasNext()) {
        ptsToUpdate.add(iterator.next().getUuid());
        if (ptsToUpdate.size() % 500 == 0) {
          persistence.update(
              persistence.createQuery(PerpetualTaskRecord.class).field(PerpetualTaskRecordKeys.uuid).in(ptsToUpdate),
              persistence.createUpdateOperations(PerpetualTaskRecord.class)
                  .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED)
                  .set(PerpetualTaskRecordKeys.assignAfterMs,
                      System.currentTimeMillis()
                          + TimeUnit.MINUTES.toMillis(FibonacciBackOff.getFibonacciElement(new Random().nextInt(10)))));
          ptsToUpdate.clear();
        }
      }
      if (!ptsToUpdate.isEmpty()) {
        persistence.update(
            persistence.createQuery(PerpetualTaskRecord.class).field(PerpetualTaskRecordKeys.uuid).in(ptsToUpdate),
            persistence.createUpdateOperations(PerpetualTaskRecord.class)
                .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED)
                .set(PerpetualTaskRecordKeys.assignAfterMs,
                    System.currentTimeMillis()
                        + TimeUnit.MINUTES.toMillis(FibonacciBackOff.getFibonacciElement(new Random().nextInt(10)))));
      }
    }
  }
}
