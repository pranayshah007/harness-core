package io.harness.queueservice.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.queueservice.infc.DelegateResourceCriteria;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.TaskType;

import java.util.List;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class FilterByDelegateHealthCriteria implements DelegateResourceCriteria {

  @Override
  public List<Delegate> getFilteredEligibleDelegateList(List<Delegate> delegateList, TaskType taskType, String accountId) {
    return delegateList;
  }


  private boolean isDelegateHealthy(Delegate delegate){
    //TBD
    return true;
  }

}
