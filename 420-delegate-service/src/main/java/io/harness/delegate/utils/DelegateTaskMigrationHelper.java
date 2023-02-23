/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class DelegateTaskMigrationHelper {
  private static final String NEW_DELEGATE_TASK_UUID_STATE = "-DEL";
  private static final String DELEGATE_TASK_CLASS_NAME = "delegateTask";
  private static final String DELEGATE_TASK_ROLLBACK_CLASS_NAME = "delegateTask-rollback";
  private static final String DELEGATE_TASK_MIGRATION_FINISHED = "delegateTask-finished";

  @Inject HPersistence persistence;

  public String generateDelegateTaskUUID() {
    String taskUUID = generateUuid();
    if (persistence.isMigrationEnabled(DELEGATE_TASK_CLASS_NAME)) {
      return taskUUID + NEW_DELEGATE_TASK_UUID_STATE;
    }
    return taskUUID;
  }

  public boolean isDelegateTaskMigrationEnabled() {
    if (persistence.isMigrationEnabled(DELEGATE_TASK_CLASS_NAME)) {
      return true;
    }
    if (persistence.isMigrationEnabled(DELEGATE_TASK_ROLLBACK_CLASS_NAME)) {
      // If we are in mid of rollback process, return true.
      return true;
    }
    // Return false if either migration is not enabled or rollback is complete.
    return false;
  }

  public boolean isMigrationEnabledForTask(String taskUUID) {
    String taskUUIDState = StringUtils.right(taskUUID, 4);
    return NEW_DELEGATE_TASK_UUID_STATE.equals(taskUUIDState);
  }

  public boolean isDelegateTaskMigrationFinished() {
    return persistence.isMigrationEnabled(DELEGATE_TASK_MIGRATION_FINISHED);
  }
}
