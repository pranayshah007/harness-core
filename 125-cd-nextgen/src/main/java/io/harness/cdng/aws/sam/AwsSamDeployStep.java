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
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.container.execution.AbstractContainerStepV2;
import io.harness.yaml.core.variables.OutputNGVariable;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamDeployStep extends AbstractContainerStepV2 {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_SAM_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public ParameterField<Map<String, String>> getEnvironmentVariables() {
    return null;
  }

  @Override
  public ParameterField<List<OutputNGVariable>> getOutputVariables() {
    return null;
  }
}
