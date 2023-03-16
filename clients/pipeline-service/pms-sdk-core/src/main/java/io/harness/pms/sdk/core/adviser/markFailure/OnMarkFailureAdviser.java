/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.markFailure;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.MarkAsFailureAdvise;
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

/***
 * This Class implements FailureStrategy "MarkAsFailure", but internally it runs nextNode without updating node status
 * as the node already has Failed status
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class OnMarkFailureAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.MARK_AS_FAILURE.name()).build();

  @Inject KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    OnMarkFailureAdviserParameters parameters = extractParameters(advisingEvent);
    MarkAsFailureAdvise.Builder builder = MarkAsFailureAdvise.newBuilder();
    if (EmptyPredicate.isNotEmpty(parameters.getNextNodeId())) {
      builder.setNextNodeId(parameters.getNextNodeId());
    }
    return AdviserResponse.newBuilder()
        .setMarkAsFailureAdvise(builder.build())
        .setType(AdviseType.MARK_AS_FAILURE)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    OnMarkFailureAdviserParameters adviserParameters = extractParameters(advisingEvent);
    boolean canAdvise = StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus())
        || StatusUtils.isAdvisingStatus(advisingEvent.getFromStatus());
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    if (adviserParameters != null && !isEmpty(failureTypesList)) {
      return canAdvise && !Collections.disjoint(adviserParameters.getApplicableFailureTypes(), failureTypesList);
    }
    return canAdvise;
  }

  @NotNull
  private OnMarkFailureAdviserParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnMarkFailureAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
