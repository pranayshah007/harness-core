/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ServerlessAwsLambdaRollbackStepInfoTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void serverlessAwsLambdaRollbackStepInfoTest() {
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("delegateSelector");
    ParameterField<List<TaskSelectorYaml>> delegateSelectors =
        ParameterField.createValueField(Arrays.asList(taskSelectorYaml));
    ServerlessAwsLambdaRollbackStepInfo serverlessAwsLambdaRollbackStepInfo =
        ServerlessAwsLambdaRollbackStepInfo.infoBuilder()
            .delegateSelectors(delegateSelectors)
            .serverlessAwsLambdaRollbackFnq("abc")
            .build();

    assertThat(serverlessAwsLambdaRollbackStepInfo.getStepType()).isEqualTo(ServerlessAwsLambdaRollbackStep.STEP_TYPE);
    assertThat(serverlessAwsLambdaRollbackStepInfo.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.TASK);
    assertThat(((ServerlessAwsLambdaRollbackStepParameters) serverlessAwsLambdaRollbackStepInfo.getSpecParameters())
                   .getDelegateSelectors())
        .isEqualTo(serverlessAwsLambdaRollbackStepInfo.getDelegateSelectors());
    assertThat(serverlessAwsLambdaRollbackStepInfo.fetchDelegateSelectors())
        .isEqualTo(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
  }
}
