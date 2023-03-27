/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.asg;

import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;

import io.harness.cdng.aws.asg.AsgBlueGreenDeployStepInfo;
import io.harness.cdng.aws.asg.AsgBlueGreenDeployStepNode;
import io.harness.cdng.aws.asg.AsgRollingDeployStepInfo;
import io.harness.cdng.aws.asg.AsgRollingDeployStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.AwsAmiServiceSetup;

import java.util.Map;

public class AsgRollingDeployStepMapperImpl extends StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    AwsAmiServiceSetup state = (AwsAmiServiceSetup) getState(stepYaml);
    return state.isBlueGreen() ? StepSpecTypeConstants.ASG_BLUE_GREEN_DEPLOY : StepSpecTypeConstants.ASG_ROLLING_DEPLOY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    AwsAmiServiceSetup state = new AwsAmiServiceSetup(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    AwsAmiServiceSetup state = (AwsAmiServiceSetup) getState(graphNode);
    if (state.isBlueGreen()) {
      return getBGRollingStepNode(state, context.getIdentifierCaseFormat());
    } else {
      return getRollingStepNode(state, context.getIdentifierCaseFormat());
    }
  }

  private AbstractStepNode getBGRollingStepNode(AwsAmiServiceSetup state, CaseFormat identifierCaseFormat) {
    AsgBlueGreenDeployStepNode node = new AsgBlueGreenDeployStepNode();
    baseSetup(state, node, identifierCaseFormat);
    node.setAsgBlueGreenDeployStepInfo(AsgBlueGreenDeployStepInfo.infoBuilder()
                                           .loadBalancer(ParameterField.createValueField(PLEASE_FIX_ME))
                                           .prodListener(ParameterField.createValueField(PLEASE_FIX_ME))
                                           .prodListenerRuleArn(ParameterField.createValueField(PLEASE_FIX_ME))
                                           .stageListener(ParameterField.createValueField(PLEASE_FIX_ME))
                                           .stageListenerRuleArn(ParameterField.createValueField(PLEASE_FIX_ME))
                                           .useAlreadyRunningInstances(ParameterField.createValueField(false))
                                           .build());
    return node;
  }

  private AbstractStepNode getRollingStepNode(AwsAmiServiceSetup state, CaseFormat identifierCaseFormat) {
    AsgRollingDeployStepNode node = new AsgRollingDeployStepNode();
    baseSetup(state, node, identifierCaseFormat);
    node.setAsgRollingDeployStepInfo(
        AsgRollingDeployStepInfo.infoBuilder()
            .useAlreadyRunningInstances(ParameterField.createValueField(state.isUseCurrentRunningCount()))
            .skipMatching(ParameterField.createValueField(true))
            .build());
    return node;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    AwsAmiServiceSetup state1 = (AwsAmiServiceSetup) getState(stepYaml1);
    AwsAmiServiceSetup state2 = (AwsAmiServiceSetup) getState(stepYaml2);
    return state1.isUseCurrentRunningCount() == state2.isUseCurrentRunningCount();
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }
}
