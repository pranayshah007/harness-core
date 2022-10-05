package software.wings.service.impl;

public interface CIDelegateTaskObserver {
  void onTaskAssigned(String accountId, String taskId, String delegateId, String stageId, String taskType);
}
