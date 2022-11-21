package software.wings.service.intfc;

import io.harness.beans.DelegateTask;
import io.harness.delegate.DelegateTaskMetaData;
import io.harness.delegate.TaskAcquireResponse;
import io.harness.delegate.beans.DelegateTaskPackage;

public interface DelegateTaskProcessService {
    DelegateTask processDelegateTask(DelegateTaskMetaData delegateTaskMetaData);
    void saveDelegateTask(DelegateTask delegateTask);
    TaskAcquireResponse acquireDelegateTask(String accountId, String delegateId, String taskId, String delegateInstanceId);
}
