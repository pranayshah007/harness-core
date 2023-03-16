/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

@OwnedBy(CDC)
public interface Adviser {
  @NotNull AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent);

  boolean canAdvise(AdvisingEvent advisingEvent);

  default List<FailureType> getAllFailureTypes(AdvisingEvent advisingEvent) {
    FailureInfo failureInfo = advisingEvent.getFailureInfo();
    if (failureInfo == null) {
      // Returning UNKNOWN_FAILURE if failure-info not present.
      return Collections.singletonList(FailureType.UNKNOWN_FAILURE);
    }
    Set<FailureType> failureTypesList = new HashSet<>(failureInfo.getFailureTypesList());
    List<FailureData> failureDataList = failureInfo.getFailureDataList();
    for (FailureData failureData : failureDataList) {
      if (failureData != null && EmptyPredicate.isNotEmpty(failureData.getFailureTypesList())) {
        failureTypesList.addAll(failureData.getFailureTypesList());
      }
    }
    if (EmptyPredicate.isEmpty(failureTypesList)) {
      // Returning UNKNOWN_FAILURE if any failure-type not sent by the step.
      return Collections.singletonList(FailureType.UNKNOWN_FAILURE);
    }
    return new ArrayList<>(failureTypesList);
  }
}
