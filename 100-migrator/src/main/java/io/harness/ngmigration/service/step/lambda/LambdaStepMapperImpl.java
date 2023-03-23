/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.lambda;

import io.harness.cdng.aws.lambda.deploy.AwsLambdaDeployStepInfo;
import io.harness.cdng.aws.lambda.deploy.AwsLambdaDeployStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.AwsLambdaState;

import java.util.Map;

public class LambdaStepMapperImpl extends StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.AWS_LAMBDA_DEPLOY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    AwsLambdaState state = new AwsLambdaState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    AwsLambdaState state = (AwsLambdaState) getState(graphNode);
    AwsLambdaDeployStepNode node = new AwsLambdaDeployStepNode();
    baseSetup(state, node, context.getIdentifierCaseFormat());
    node.setLambdaDeployStepInfo(AwsLambdaDeployStepInfo.infoBuilder().build());
    return node;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }
}
