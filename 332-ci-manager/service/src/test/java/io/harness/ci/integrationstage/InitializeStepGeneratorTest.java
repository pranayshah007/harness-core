/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InitializeStepGeneratorTest extends CIExecutionTestBase {
  @Inject InitializeStepGenerator initializeStepGenerator;
  @Inject CIExecutionPlanTestHelper ciExecutionPlanTestHelper;

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldCreateLiteEngineTaskStepInfoFirstPod() {
    // input
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    StageElementConfig stageElementConfig = ciExecutionPlanTestHelper.getIntegrationStageElementConfig();
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();
    String podName = "pod";
    Integer liteEngineCounter = 1;

    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();
    InitializeStepInfo actual = initializeStepGenerator.createInitializeStepInfo(executionElementConfig,
        ciExecutionPlanTestHelper.getCICodebase(), stageElementConfig, ciExecutionArgs, infrastructure, "abc");

    InitializeStepInfo expected = ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnFirstPod(
        ciExecutionArgs.getExecutionSource(), ciExecutionPlanTestHelper.getIntegrationStageElementConfig());

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldCreateLiteEngineTaskStepInfoOtherPod() {
    // input
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    StageElementConfig stageElementConfig = ciExecutionPlanTestHelper.getIntegrationStageElementConfig();
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();

    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();
    InitializeStepInfo actual = initializeStepGenerator.createInitializeStepInfo(
        executionElementConfig, null, stageElementConfig, ciExecutionArgs, infrastructure, "ABX");

    InitializeStepInfo expected = ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnOtherPods(
        ciExecutionArgs.getExecutionSource(), stageElementConfig);

    assertThat(actual).isEqualTo(expected);
  }
}
