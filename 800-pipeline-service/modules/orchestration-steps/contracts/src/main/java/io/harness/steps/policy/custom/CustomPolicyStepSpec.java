/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.policy.custom;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.policy.PolicyStepConstants.CUSTOM_POLICY_STEP_TYPE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.policy.PolicySpec;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonTypeName(CUSTOM_POLICY_STEP_TYPE)
@RecasterAlias("io.harness.steps.policy.custom.CustomPolicyStepSpec")
public class CustomPolicyStepSpec implements PolicySpec {
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> payload;

  @Override
  public String getType() {
    return CUSTOM_POLICY_STEP_TYPE;
  }
}
