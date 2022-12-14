/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.queueservice.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.queueservice.infc.DelegateResourceCriteria;

import software.wings.beans.TaskType;

import java.util.List;

@OwnedBy(HarnessTeam.DEL)
public class AndDelegateResourceCriteria implements DelegateResourceCriteria {
  private DelegateResourceCriteria[] criteriaList;

  public AndDelegateResourceCriteria(DelegateResourceCriteria... criteriaList) {
    this.criteriaList = criteriaList;
  }

  @Override
  public List<Delegate> getFilteredEligibleDelegateList(
      List<Delegate> delegateList, TaskType taskType, String accountId) {
    for (DelegateResourceCriteria criteria : criteriaList) {
      delegateList = criteria.getFilteredEligibleDelegateList(delegateList, taskType, accountId);
    }
    return delegateList;
  }
}
