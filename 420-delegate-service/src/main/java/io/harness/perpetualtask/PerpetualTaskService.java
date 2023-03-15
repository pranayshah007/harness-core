/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import java.util.List;

public interface PerpetualTaskService {
  String createTask(String perpetualTaskType, String accountId, PerpetualTaskClientContext clientContext,
      PerpetualTaskSchedule schedule, boolean allowDuplicate, String taskDescription);

  boolean resetTask(String accountId, String taskId, PerpetualTaskExecutionBundle taskExecutionBundle);

  long updateTasksSchedule(String accountId, String perpetualTaskType, long intervalInMillis);

  boolean deleteTask(String accountId, String taskId);

  boolean pauseTask(String accountId, String taskId);

  boolean resumeTask(String accountId, String taskId);

  boolean deleteAllTasksForAccount(String accountId);

  List<PerpetualTaskAssignDetails> listAssignedTasks(String delegateId);

  List<PerpetualTaskAssignDetails> listAssignedTasks(String delegateId, String accountId);

  List<PerpetualTaskRecord> listAllTasksForAccount(String accountId);

  PerpetualTaskRecord getTaskRecord(String taskId);

  String getPerpetualTaskType(String taskId);

  PerpetualTaskExecutionContext perpetualTaskContext(String taskId);

  boolean triggerCallback(String taskId, long heartbeatMillis, PerpetualTaskResponse perpetualTaskResponse);

  void recordTaskFailure(String taskId, String exceptionMessage);

  void appointDelegate(String accountId, String taskId, String delegateId, long lastContextUpdated);

  void updateTaskUnassignedReason(String taskId, PerpetualTaskUnassignedReason reason, int assignTryCount);

  void markStateAndNonAssignedReason_OnAssignTryCount(PerpetualTaskRecord perpetualTaskRecord,
      PerpetualTaskUnassignedReason reason, PerpetualTaskState perpetualTaskState, String exception);

  void setTaskUnassigned(String taskId);

  String createPerpetualTaskInternal(String perpetualTaskType, String accountId,
      PerpetualTaskClientContext clientContext, PerpetualTaskSchedule schedule, boolean allowDuplicate,
      String taskDescription);
}
