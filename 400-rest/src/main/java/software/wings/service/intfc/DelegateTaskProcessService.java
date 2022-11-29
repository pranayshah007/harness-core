package software.wings.service.intfc;

import io.harness.beans.DelegateTask;
import io.harness.delegate.DelegateTaskAcquireResponse;
import io.harness.delegate.DelegateTaskMetaData;
import io.harness.delegate.ProcessDelegateTaskRequest;

public interface DelegateTaskProcessService {
    DelegateTask processDelegateTask(ProcessDelegateTaskRequest processDelegateTaskRequest);
    void saveDelegateTask(DelegateTask delegateTask);
    DelegateTaskAcquireResponse acquireDelegateTask(String accountId, String delegateId, String taskId, String delegateInstanceId);
}
