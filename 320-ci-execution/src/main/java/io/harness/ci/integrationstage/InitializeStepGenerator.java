/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.steps.matrix.StrategyHelper;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CI)
public class InitializeStepGenerator {
  private static final String INITIALIZE_TASK = InitializeStepInfo.STEP_TYPE.getType();
  @Inject private BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;

  @Inject private StrategyHelper strategyHelper;
  @Inject private CIFeatureFlagService ciFeatureFlagService;

  InitializeStepInfo createInitializeStepInfo(ExecutionElementConfig executionElement, CodeBase ciCodebase,
      StageElementConfig stageElementConfig, CIExecutionArgs ciExecutionArgs, Infrastructure infrastructure,
      String accountId) {
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    List<ExecutionWrapperConfig> expandedExecutionElement = new ArrayList<>();
    boolean pipelineMatrixEnabled = ciFeatureFlagService.isEnabled(FeatureName.PIPELINE_MATRIX, accountId);
    if (pipelineMatrixEnabled) {
      for (ExecutionWrapperConfig config : executionElement.getSteps()) {
        expandedExecutionElement.addAll(strategyHelper.expandExecutionWrapperConfig(config));
      }
    }

    boolean gitClone = RunTimeInputHandler.resolveGitClone(integrationStageConfig.getCloneCodebase());
    return InitializeStepInfo.builder()
        .identifier(INITIALIZE_TASK)
        .name(INITIALIZE_TASK)
        .infrastructure(infrastructure)
        .stageIdentifier(stageElementConfig.getIdentifier())
        .variables(stageElementConfig.getVariables())
        .stageElementConfig(integrationStageConfig)
        .executionSource(ciExecutionArgs.getExecutionSource())
        .ciCodebase(ciCodebase)
        .skipGitClone(!gitClone)
        .executionElementConfig(pipelineMatrixEnabled
                ? ExecutionElementConfig.builder().steps(expandedExecutionElement).build()
                : executionElement)
        .timeout(buildJobEnvInfoBuilder.getTimeout(infrastructure))
        .build();
  }
}
