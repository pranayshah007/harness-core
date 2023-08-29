/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@EqualsAndHashCode(callSuper = true)
@OwnedBy(PIPELINE)
@TypeAlias("policyStepSpecParameters")
@RecasterAlias("io.harness.steps.policy.PolicyStepSpecParameters")
public class PolicyStepSpecParameters extends PolicyStepBase implements SpecParameters {
  public String COMMAND_UNIT = "Execute";
  @Builder
  public PolicyStepSpecParameters(
      @NonNull ParameterField<List<String>> policySets, @NonNull String type, PolicySpec policySpec) {
    super(policySets, type, policySpec);
  }
}
