/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServerlessAwsLambdaRollbackStepVariableCreatorTest extends CategoryTest {
  private final ServerlessAwsLambdaRollbackStepVariableCreator serverlessAwsLambdaRollbackStepVariableCreator =
      new ServerlessAwsLambdaRollbackStepVariableCreator();

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(serverlessAwsLambdaRollbackStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK));
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(serverlessAwsLambdaRollbackStepVariableCreator.getFieldClass())
        .isEqualTo(ServerlessAwsLambdaRollbackStepNode.class);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithServerlessAwsLambdaRollbackStep.json",
        serverlessAwsLambdaRollbackStepVariableCreator, ServerlessAwsLambdaRollbackStepNode.class);

    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_rollback_step.description",
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_rollback_step.timeout",
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_rollback_step.spec.delegateSelectors",
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_rollback_step.name");
  }
}
