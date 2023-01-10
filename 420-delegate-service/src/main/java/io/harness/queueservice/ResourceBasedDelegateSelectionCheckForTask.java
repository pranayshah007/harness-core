/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.queueservice;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.queueservice.impl.FilterByDelegateCapacity;
import io.harness.queueservice.impl.OrderByTotalNumberOfTaskAssignedCriteria;
import io.harness.queueservice.infc.DelegateResourceCriteria;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.DEL)
public class ResourceBasedDelegateSelectionCheckForTask {
  @Inject OrderByTotalNumberOfTaskAssignedCriteria orderByTotalNumberOfTaskAssignedCriteria;
  @Inject FilterByDelegateCapacity filterByDelegateCapacity;

  public Optional<List<String>> perform(List<Delegate> delegateList, TaskType taskType, String accountId) {
    DelegateResourceCriteria delegateResourceCriteria =
        orderByTotalNumberOfTaskAssignedCriteria.and(filterByDelegateCapacity);
    List<String> filteredList =
        delegateResourceCriteria.getFilteredEligibleDelegateList(delegateList, taskType, accountId)
            .stream()
            .map(Delegate::getUuid)
            .collect(Collectors.toList());
    return Optional.of(filteredList);
  }
}
