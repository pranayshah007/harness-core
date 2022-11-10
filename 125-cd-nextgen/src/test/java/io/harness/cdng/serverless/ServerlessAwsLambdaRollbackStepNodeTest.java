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
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ServerlessAwsLambdaRollbackStepNodeTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void serverlessAwsLambdaRollbackStepNodeTest() {
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("delegateSelector");
    ServerlessAwsLambdaRollbackStepInfo serverlessAwsLambdaRollbackStepInfo =
        ServerlessAwsLambdaRollbackStepInfo.infoBuilder()
            .delegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)))
            .serverlessAwsLambdaRollbackFnq("abc")
            .build();
    ServerlessAwsLambdaRollbackStepNode serverlessAwsLambdaRollbackStepNode = new ServerlessAwsLambdaRollbackStepNode();
    serverlessAwsLambdaRollbackStepNode.serverlessAwsLambdaRollbackStepInfo = serverlessAwsLambdaRollbackStepInfo;
    assertThat(serverlessAwsLambdaRollbackStepNode.getType())
        .isEqualTo(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK);
    assertThat(serverlessAwsLambdaRollbackStepNode.getStepSpecType())
        .isEqualTo(serverlessAwsLambdaRollbackStepNode.serverlessAwsLambdaRollbackStepInfo);
  }
}
