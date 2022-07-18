/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.io;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.io.StepOutcomeProto;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class StepOutcomeMapper {
  public static String GRAPH_KEY = "_graphOutcome_";

  public StepOutcome fromStepOutcomeProto(StepOutcomeProto proto) {
    return StepOutcome.builder()
        .group(proto.getGroup())
        .name(proto.getName())
        .outcome(RecastOrchestrationUtils.fromJson(proto.getOutcome(), Outcome.class))
        .build();
  }

  public StepOutcomeProto toStepOutcomeProto(StepOutcome stepOutcome) {
    StepOutcomeProto.Builder builder = StepOutcomeProto.newBuilder().setName(stepOutcome.getName());
    if (stepOutcome.getGroup() != null) {
      builder.setGroup(stepOutcome.getGroup());
    }
    if (stepOutcome.getOutcome() != null) {
      builder.setOutcome(RecastOrchestrationUtils.toJson(stepOutcome.getOutcome()));
    }
    return builder.build();
  }
}
