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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaDeployStepVariableCreatorTest extends CategoryTest {
  private final ServerlessAwsLambdaDeployStepVariableCreator serverlessAwsLambdaDeployStepVariableCreator =
      new ServerlessAwsLambdaDeployStepVariableCreator();

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(serverlessAwsLambdaDeployStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY));
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(serverlessAwsLambdaDeployStepVariableCreator.getFieldClass())
        .isEqualTo(ServerlessAwsLambdaDeployStepNode.class);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithServerlessAwsLambdaDeployStep.json", serverlessAwsLambdaDeployStepVariableCreator,
        ServerlessAwsLambdaDeployStepNode.class);

    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_deploy_step.description",
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_deploy_step.timeout",
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_deploy_step.spec.delegateSelectors",
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_deploy_step.name",
            "pipeline.stages.Serverless_Aws_Lambda.spec.execution.steps.serverless_aws_lambda_deploy_step.spec.commandOptions");
  }
}
