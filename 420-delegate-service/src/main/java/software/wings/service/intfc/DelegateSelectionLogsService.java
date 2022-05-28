/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.DelegateSelectionLogResponse;
import io.harness.selection.log.DelegateSelectionLog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(DEL)
public interface DelegateSelectionLogsService {
  void save(DelegateSelectionLog log);

  void logNoEligibleDelegatesToExecuteTask(DelegateTask delegateTask);

  void logEligibleDelegatesToExecuteTask(Set<String> delegateIds, DelegateTask delegateTask, boolean preAssigned);

  void logNonSelectedDelegates(DelegateTask delegateTask, Map<String, List<String>> nonAssignableDelegates);

  void logBroadcastToDelegate(Set<String> delegateIds, DelegateTask delegateTask);

  void logTaskAssigned(String delegateId, DelegateTask delegateTask);

  void logTaskValidationFailed(DelegateTask delegateTask, String failureMessage);

  List<DelegateSelectionLogParams> fetchTaskSelectionLogs(String accountId, String taskId);

  List<Pair<String, List<DelegateSelectionLogParams>>> fetchTaskSelectionLogsGroupByAssessment(
      String accountId, String taskId);

  DelegateSelectionLogResponse fetchTaskSelectionLogsData(String accountId, String taskId);

  Optional<DelegateSelectionLogParams> fetchSelectedDelegateForTask(String accountId, String taskId);

  void logDelegateTaskInfo(DelegateTask delegateTask);
}
