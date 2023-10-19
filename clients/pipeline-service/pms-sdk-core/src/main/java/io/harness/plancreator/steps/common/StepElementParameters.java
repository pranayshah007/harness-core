/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.policy.PolicyConfig;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import java.util.LinkedList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("stepElementParameters")
@RecasterAlias("io.harness.plancreator.steps.common.StepElementParameters")
public class StepElementParameters implements StepBaseParameters {
  String uuid;
  String identifier;
  String name;
  String description;
  ParameterField<String> timeout;
  List<FailureStrategyConfig> failureStrategies;

  ParameterField<String> skipCondition;
  StepWhenCondition when;

  String type;
  SpecParameters spec;
  PolicyConfig enforce;

  ParameterField<List<String>> delegateSelectors;

  @Deprecated
  // Only for rollback failures
  OnFailRollbackParameters rollbackParameters;

  @Override
  public List<String> excludeKeysFromStepInputs() {
    if (spec != null) {
      return spec.stepInputsKeyExclude();
    }
    return new LinkedList<>();
  }
}
