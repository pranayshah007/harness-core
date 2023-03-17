/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.lambda;

import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStepInfo;
import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.AwsLambdaRollback;

import java.util.Map;

public class LambdaRollbackStepMapperImpl extends StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.AWS_LAMBDA_ROLLBACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    AwsLambdaRollback state = new AwsLambdaRollback(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    AwsLambdaRollback state = (AwsLambdaRollback) getState(graphNode);
    AwsLambdaRollbackStepNode node = new AwsLambdaRollbackStepNode();
    baseSetup(state, node, context.getIdentifierCaseFormat());
    node.setAwsLambdaRollbackStepInfo(AwsLambdaRollbackStepInfo.infoBuilder().build());
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
