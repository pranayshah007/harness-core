/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.marksuccess;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.MarkSuccessAdvise;
import io.harness.pms.contracts.advisers.MarkSuccessAdvise.Builder;
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

@OwnedBy(HarnessTeam.PIPELINE)
public class OnMarkSuccessAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.MARK_SUCCESS.name()).build();

  @Inject KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    OnMarkSuccessAdviserParameters parameters = extractParameters(advisingEvent);
    Builder builder = MarkSuccessAdvise.newBuilder();
    if (EmptyPredicate.isNotEmpty(parameters.getNextNodeId())) {
      builder.setNextNodeId(parameters.getNextNodeId());
    }
    return AdviserResponse.newBuilder().setMarkSuccessAdvise(builder.build()).setType(AdviseType.MARK_SUCCESS).build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    OnMarkSuccessAdviserParameters adviserParameters = extractParameters(advisingEvent);
    boolean canAdvise = StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus());
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    if (adviserParameters != null && !isEmpty(failureTypesList)) {
      return canAdvise && !Collections.disjoint(adviserParameters.getApplicableFailureTypes(), failureTypesList);
    }
    return canAdvise;
  }

  @NotNull
  private OnMarkSuccessAdviserParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnMarkSuccessAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
