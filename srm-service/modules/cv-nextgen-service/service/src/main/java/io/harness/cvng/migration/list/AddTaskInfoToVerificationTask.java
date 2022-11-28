/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.VerificationTaskKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class AddTaskInfoToVerificationTask implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for updating TaskInfo in VerificationTasks");
    Query<VerificationTask> verificationTaskQuery =
        hPersistence.createQuery(VerificationTask.class).filter(VerificationTaskKeys.taskInfo, null);
    try (HIterator<VerificationTask> iterator = new HIterator<>(verificationTaskQuery.fetch())) {
      while (iterator.hasNext()) {
        VerificationTask verificationTask = iterator.next();
        UpdateResults updateResults = hPersistence.update(verificationTask,
            hPersistence.createUpdateOperations(VerificationTask.class)
                .set(VerificationTaskKeys.taskInfo, verificationTask.getTaskInfo()));
        log.info("Updated VerifcationTask {}", updateResults);
      }
    }
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
