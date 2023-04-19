/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.container.execution.AbstractContainerStep;
import io.harness.yaml.core.variables.OutputNGVariable;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamBuildStep extends AbstractContainerStep {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_SAM_BUILD.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public Map<String, String> getEnvironmentVariables(StepElementParameters stepElementParameters) {
    AwsSamBuildStepInfo awsSamBuildStepInfo = (AwsSamBuildStepInfo) stepElementParameters.getSpec();
    Map<String, String> envvars = ExpressionResolverUtils.resolveMapParameterV2(
        "envVariables", "AwsSamBuildStep", awsSamBuildStepInfo.getUuid(), awsSamBuildStepInfo.getEnvVariables(), false);
    return envvars;
  }

  public ParameterField<List<OutputNGVariable>> getOutputVariables(StepElementParameters stepElementParameters) {
    AwsSamBuildStepInfo awsSamBuildStepInfo = (AwsSamBuildStepInfo) stepElementParameters.getSpec();
    return null;
  }
}
