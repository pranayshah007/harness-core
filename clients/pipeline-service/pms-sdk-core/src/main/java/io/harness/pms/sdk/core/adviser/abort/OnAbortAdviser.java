/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.abort;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.contracts.execution.Status.ABORTED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public class OnAbortAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ABORT.name()).build();

  @Inject private KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    return AdviserResponse.newBuilder()
        .setEndPlanAdvise(EndPlanAdvise.newBuilder().setIsAbort(true).build())
        .setType(AdviseType.END_PLAN)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    OnAbortAdviserParameters adviserParameters = extractParameters(advisingEvent);
    boolean canAdvise =
        StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus()) || ABORTED == advisingEvent.getToStatus();
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    if (adviserParameters != null && !isEmpty(failureTypesList)) {
      return canAdvise && !Collections.disjoint(adviserParameters.getApplicableFailureTypes(), failureTypesList);
    }
    return canAdvise;
  }

  private OnAbortAdviserParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnAbortAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
