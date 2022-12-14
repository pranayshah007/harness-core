package io.harness.queueservice.infc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.queueservice.impl.AndDelegateResourceCriteria;

import software.wings.beans.TaskType;

import java.util.List;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateResourceCriteria {
  List<Delegate> getFilteredEligibleDelegateList(List<Delegate> delegateList, TaskType taskType, String accountId);

  default DelegateResourceCriteria and(DelegateResourceCriteria other) {
    return new AndDelegateResourceCriteria(this, other);
  }
}
