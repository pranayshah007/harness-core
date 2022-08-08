/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.validation.Create;

import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.service.intfc.ownership.OwnedByAccount;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(DEL)
@BreakDependencyOn("software.wings.service.intfc.ownership.OwnedByAccount")
public interface DelegateTaskServiceClassic extends OwnedByAccount {
  @ValidationGroups(Create.class) String queueTask(@Valid DelegateTask task);

  void scheduleSyncTask(DelegateTask task);

  <T extends DelegateResponseData> T executeTask(DelegateTask task) throws InterruptedException;

  void processDelegateTask(DelegateTask task, DelegateTask.Status taskStatus);

  String queueParkedTask(String accountId, String taskId);

  byte[] getParkedTaskResults(String accountId, String taskId, String driverId);

  DelegateTaskPackage acquireDelegateTask(
      String accountId, String delegateId, String taskId, String delegateInstanceId);

  DelegateTaskPackage reportConnectionResults(String accountId, String delegateId, String taskId,
      String delegateInstanceId, List<DelegateConnectionResult> results);

  void publishTaskProgressResponse(
      String accountId, String driverId, String delegateTaskId, DelegateProgressData responseData);

  boolean filter(String delegateId, DelegateTaskAbortEvent taskAbortEvent);

  DelegateTask abortTask(String accountId, String delegateTaskId);

  String expireTask(String accountId, String delegateTaskId);

  List<DelegateTaskEvent> getDelegateTaskEvents(String accountId, String delegateId, boolean syncOnly);

  Optional<DelegateTask> fetchDelegateTask(String accountId, String taskId);

  void convertToExecutionCapability(DelegateTask task);

  boolean checkDelegateConnected(String accountId, String delegateId);

  void markAllTasksFailedForDelegate(String accountId, String delegateId);

  void addToTaskActivityLog(DelegateTask task, String message);

  List<SelectorCapability> fetchTaskSelectorCapabilities(List<ExecutionCapability> executionCapabilities);
}
