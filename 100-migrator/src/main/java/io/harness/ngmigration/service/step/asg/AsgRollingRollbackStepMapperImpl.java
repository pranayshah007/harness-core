/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.asg;

import io.harness.cdng.aws.asg.AsgRollingRollbackStepInfo;
import io.harness.cdng.aws.asg.AsgRollingRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.AwsAmiServiceRollback;

import java.util.Map;

public class AsgRollingRollbackStepMapperImpl extends StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.ASG_ROLLING_ROLLBACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    AwsAmiServiceRollback state = new AwsAmiServiceRollback(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    AwsAmiServiceRollback state = (AwsAmiServiceRollback) getState(graphNode);

    AsgRollingRollbackStepNode node = new AsgRollingRollbackStepNode();
    baseSetup(state, node, context.getIdentifierCaseFormat());
    AsgRollingRollbackStepInfo rollbackStepInfo = AsgRollingRollbackStepInfo.infoBuilder().build();

    node.setAsgRollingRollbackStepInfo(rollbackStepInfo);
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
