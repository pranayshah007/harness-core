/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common.steps.stepgroup;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("stepGroupStepParameters")
@RecasterAlias("io.harness.steps.common.steps.stepgroup.StepGroupStepParameters")
public class StepGroupStepParameters implements StepParameters {
  String identifier;
  String name;
  ParameterField<String> skipCondition;
  StepWhenCondition when;
  List<FailureStrategyConfig> failureStrategies;

  String childNodeID;

  public static StepGroupStepParameters getStepParameters(StepGroupElementConfig config, String childNodeID) {
    if (config == null) {
      return StepGroupStepParameters.builder().childNodeID(childNodeID).build();
    }
    return StepGroupStepParameters.builder()
        .identifier(config.getIdentifier())
        .name(config.getName())
        .skipCondition(config.getSkipCondition())
        .when(config.getWhen())
        .failureStrategies(config.getFailureStrategies())
        .childNodeID(childNodeID)
        .build();
  }
}
