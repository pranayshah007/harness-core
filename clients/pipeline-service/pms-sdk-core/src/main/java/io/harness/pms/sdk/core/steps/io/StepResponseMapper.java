/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.io;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.io.StepResponseProto;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class StepResponseMapper {
  public StepResponse fromStepResponseProto(StepResponseProto proto) {
    return StepResponse.builder()
        .status(proto.getStatus())
        .stepOutcomes(proto.getStepOutcomesList() == null ? null
                                                          : proto.getStepOutcomesList()
                                                                .stream()
                                                                .map(StepOutcomeMapper::fromStepOutcomeProto)
                                                                .collect(Collectors.toList()))
        .failureInfo(proto.getFailureInfo())
        .build();
  }

  public StepResponseProto toStepResponseProto(StepResponse stepResponse) {
    StepResponseProto.Builder builder = StepResponseProto.newBuilder().setStatus(stepResponse.getStatus());
    if (stepResponse.getStepOutcomes() != null) {
      builder.addAllStepOutcomes(stepResponse.getStepOutcomes()
                                     .stream()
                                     .map(StepOutcomeMapper::toStepOutcomeProto)
                                     .collect(Collectors.toList()));
    }

    if (stepResponse.getFailureInfo() != null) {
      builder.setFailureInfo(stepResponse.getFailureInfo());
    }
    if (stepResponse.getUnitProgressList() != null) {
      builder.addAllUnitProgress(stepResponse.getUnitProgressList());
    }
    return builder.build();
  }
}
