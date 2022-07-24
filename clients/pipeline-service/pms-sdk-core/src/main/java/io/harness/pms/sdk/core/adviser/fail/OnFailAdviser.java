/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.fail;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
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
import javax.validation.constraints.NotNull;

@OwnedBy(PIPELINE)
public class OnFailAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_FAIL.name()).build();

  @Inject private KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    OnFailAdviserParameters parameters = extractParameters(advisingEvent);
    return AdviserResponse.newBuilder()
        .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(parameters.getNextNodeId()).build())
        .setType(AdviseType.NEXT_STEP)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    OnFailAdviserParameters parameters = extractParameters(advisingEvent);
    if (parameters.getNextNodeId() == null) {
      return false;
    }
    boolean canAdvise = StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus());
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    if (!isEmpty(failureTypesList)) {
      return canAdvise && !Collections.disjoint(parameters.getApplicableFailureTypes(), failureTypesList);
    }
    return canAdvise;
  }

  @NotNull
  private OnFailAdviserParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnFailAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
