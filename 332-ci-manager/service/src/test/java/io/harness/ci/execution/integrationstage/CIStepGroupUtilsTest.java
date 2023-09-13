/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CIStepGroupUtilsTest extends CIExecutionTestBase {
  //@Inject CILiteEngineStepGroupUtils ciLiteEngineStepGroupUtils;
  @Inject CIStepGroupUtils ciExecutionPlanTestHelper;

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void createExecutionWrapperWithLiteEngineSteps() {
    //    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();
    //    List<ExecutionWrapper> executionWrapperWithLiteEngineSteps =
    //        ciLiteEngineStepGroupUtils.createExecutionWrapperWithLiteEngineSteps(
    //            ciExecutionPlanTestHelper.getIntegrationStage(), ciExecutionArgs, null, "accountId", "podName");
    //
    //    List<ExecutionWrapper> expectedExecutionWrapper = ciExecutionPlanTestHelper.getExpectedExecutionWrappers();
    //    expectedExecutionWrapper.addAll(ciExecutionPlanTestHelper.getExpectedExecutionElement(false).getSteps());
    //
    //    assertThat(executionWrapperWithLiteEngineSteps.get(0)).isInstanceOf(StepElement.class);
    //    StepElement stepElement = (StepElement) executionWrapperWithLiteEngineSteps.get(0);
    //    LiteEngineTaskStepInfo liteEngineTaskStepInfo = (LiteEngineTaskStepInfo) stepElement.getStepSpecType();
    //    ((K8BuildJobEnvInfo) liteEngineTaskStepInfo.getBuildJobEnvInfo())
    //        .getPodsSetupInfo()
    //        .getPodSetupInfoList()
    //        .get(0)
    //        .getPvcParamsList()
    //        .get(0)
    //        .setClaimName("");
    //    ((K8BuildJobEnvInfo) liteEngineTaskStepInfo.getBuildJobEnvInfo())
    //        .getPodsSetupInfo()
    //        .getPodSetupInfoList()
    //        .get(0)
    //        .setName("");
    //
    //    assertThat(executionWrapperWithLiteEngineSteps).isEqualTo(expectedExecutionWrapper);
  }
}
