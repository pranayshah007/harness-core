package io.harness.queueservice.infc;

import com.google.inject.ImplementedBy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateCapacity;
import io.harness.delegate.beans.TaskGroup;
import io.harness.queueservice.impl.DelegateCapacityManagementServiceImpl;
import lombok.extern.slf4j.Slf4j;

@ImplementedBy(DelegateCapacityManagementServiceImpl.class)
@OwnedBy(HarnessTeam.DEL)
public interface DelegateCapacityManagementService {

    DelegateCapacity getDelegateCapacity(String delegateId, String accountId);
    DelegateCapacity getDelegateCapacity(Delegate delegate);
    void registerDelegateCapacity(String accountId, String delegateId, DelegateCapacity delegateCapacity);
    String getDefaultCapacityForTaskGroup(TaskType taskType);
    boolean hasCapacity(Delegate delegate);
}
