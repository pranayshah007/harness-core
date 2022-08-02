package software.wings.service.impl;

public interface DelegateTaskObserver {
    void onTaskAssigned(String accountId, String taskId, String delegateId);
}
