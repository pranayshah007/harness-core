package io.harness.service.intfc;

import io.harness.delegate.beans.DelegateTaskPackage;

public interface DMSTaskServiceClassic {
  DelegateTaskPackage acquireDelegateTask(
      String accountId, String delegateId, String taskId, String delegateInstanceId);
}
