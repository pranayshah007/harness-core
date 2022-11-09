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
import io.harness.cdng.creator.plan.steps.serverless.ServerlessAwsLambdaRollbackStepPlanCreator;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ServerlessAwsLambdaRollbackStepPlanCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void serverlessAwsLambdaRollbackStepPlanCreatorTest() throws IOException {
    ServerlessAwsLambdaRollbackStepPlanCreator serverlessAwsLambdaRollbackStepPlanCreator =
        new ServerlessAwsLambdaRollbackStepPlanCreator();
    assertThat(serverlessAwsLambdaRollbackStepPlanCreator.getSupportedStepTypes())
        .isEqualTo(Sets.newHashSet(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK));
    assertThat(serverlessAwsLambdaRollbackStepPlanCreator.getFieldClass())
        .isEqualTo(ServerlessAwsLambdaRollbackStepNode.class);
  }
}
