package io.harness.queueservice.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.queueservice.infc.DelegateResourceCriteria;

import software.wings.beans.TaskType;

import java.util.List;

@OwnedBy(HarnessTeam.DEL)
public class AndDelegateResourceCriteria implements DelegateResourceCriteria {
  private DelegateResourceCriteria criteria1;
  private DelegateResourceCriteria criteria2;

  public AndDelegateResourceCriteria(DelegateResourceCriteria criteria1, DelegateResourceCriteria criteria2) {
    this.criteria1 = criteria1;
    this.criteria2 = criteria2;
  }

  @Override
  public List<Delegate> getFilteredEligibleDelegateList(
      List<Delegate> delegateList, TaskType taskType, String accountId) {
    return criteria1.getFilteredEligibleDelegateList(
        criteria2.getFilteredEligibleDelegateList(delegateList, taskType, accountId), taskType, accountId);
  }
}
