package io.harness.service.intfc;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;

import java.util.List;

public interface DMSTaskService {
  DelegateTaskPackage acquireDelegateTask(
      String accountId, String delegateId, String taskId, String delegateInstanceId);

  List<SelectorCapability> fetchTaskSelectorCapabilities(List<ExecutionCapability> executionCapabilities);
}
