package io.harness.queueservice.impl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.TaskGroup;
import io.harness.queueservice.infc.DelegateCapacityManagementService;
import io.harness.queueservice.infc.DelegateResourceCriteria;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class FilterByDelegateCapacity implements DelegateResourceCriteria {
  @Inject DelegateCapacityManagementService delegateCapacityManagementService;

  @Override
  public List<Delegate> getFilteredEligibleDelegateList(
      List<Delegate> delegateList, TaskType taskType, String accountId) {
    return delegateList.stream()
        .filter(delegate -> delegateHasCapacityToAssignTask(delegate, taskType))
        .collect(Collectors.toList());
  }

  private boolean delegateHasCapacityToAssignTask(Delegate delegate, TaskType taskType) {
    if (delegateCapacityManagementService.hasCapacity(delegate)) {
      TaskGroup taskGroup =
          taskType != null && isNotBlank(taskType.name()) ? TaskType.valueOf(taskType.name()).getTaskGroup() : null;
      if (taskGroup == TaskGroup.CI) {
        return delegate.getDelegateCapacity().getMaximumNumberOfBuilds() >= delegate.getNumberOfTaskAssigned();
      }
      return delegate.getDelegateCapacity().getTaskLimit() >= delegate.getNumberOfTaskAssigned();
    }
    return true;
  }
}
