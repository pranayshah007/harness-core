/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CIIntegrationStageModifierTest extends CIExecutionTestBase {
  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;

  @Before
  public void setUp() {}

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldModifyExecutionPlan() {
    //    IntegrationStage stage = ciExecutionPlanTestHelper.getIntegrationStage();
    //    ExecutionPlanCreationContextImpl executionPlanCreationContextWithExecutionArgs =
    //        ciExecutionPlanTestHelper.getExecutionPlanCreationContextWithExecutionArgs();
    //    ExecutionElement modifiedExecution = stageExecutionModifier.modifyExecutionPlan(
    //        stage.getExecution(), stage, executionPlanCreationContextWithExecutionArgs, "podName");
    //    assertThat(modifiedExecution).isNotNull();
    //    assertThat(modifiedExecution.getSteps()).isNotNull();
    //    StepElement step = (StepElement) modifiedExecution.getSteps().get(0);
    //    assertThat(step.getStepSpecType()).isInstanceOf(LiteEngineTaskStepInfo.class);
    //    LiteEngineTaskStepInfo liteEngineTask = (LiteEngineTaskStepInfo) step.getStepSpecType();
    //    assertThat(liteEngineTask).isInstanceOf(LiteEngineTaskStepInfo.class);
    //    assertThat(liteEngineTask.getSteps())
    //        .isEqualTo(ciExecutionPlanTestHelper.getExpectedExecutionElementWithoutCleanup());
    //    K8BuildJobEnvInfo envInfo = (K8BuildJobEnvInfo) liteEngineTask.getBuildJobEnvInfo();
    //    assertThat(envInfo.getType()).isEqualTo(K8);
    //    assertThat(envInfo.getWorkDir()).isEqualTo(stage.getWorkingDirectory());
    //    assertThat(envInfo.getPublishArtifactStepIds()).isEqualTo(ciExecutionPlanTestHelper.getPublishArtifactStepIds());
    //    assertThat(envInfo.getPodsSetupInfo().getPodSetupInfoList().get(0).getPodSetupParams())
    //        .isEqualTo(
    //            ciExecutionPlanTestHelper.getCIPodsSetupInfoOnFirstPod().getPodSetupInfoList().get(0).getPodSetupParams());
    //    assertThat(envInfo.getPodsSetupInfo().getPodSetupInfoList().get(0).getPvcParamsList().get(0).getVolumeName())
    //        .isEqualTo(ciExecutionPlanTestHelper.getCIPodsSetupInfoOnFirstPod()
    //                       .getPodSetupInfoList()
    //                       .get(0)
    //                       .getPvcParamsList()
    //                       .get(0)
    //                       .getVolumeName());
  }
}
