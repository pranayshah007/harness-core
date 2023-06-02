/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.cloudformation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepConfiguration;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepConfigurationTypes;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepNode;
import io.harness.cdng.provision.cloudformation.InlineCloudformationDeleteStackStepConfiguration;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.CloudFormationDeleteStackState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class CloudformationDeleteStepMapperImpl extends BaseCloudformationProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    CloudFormationDeleteStackState state = new CloudFormationDeleteStackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    CloudFormationDeleteStackState state = (CloudFormationDeleteStackState) getState(graphNode);

    CloudformationDeleteStackStepNode cloudformationDeleteStackStepNode = new CloudformationDeleteStackStepNode();
    baseSetup(graphNode, cloudformationDeleteStackStepNode, context.getIdentifierCaseFormat());

    CloudformationDeleteStackStepInfo cloudformationDeleteStackStepInfo =
        CloudformationDeleteStackStepInfo.infoBuilder()
            .cloudformationStepConfiguration(
                CloudformationDeleteStackStepConfiguration.builder()
                    .type(CloudformationDeleteStackStepConfigurationTypes.Inline)
                    .spec(InlineCloudformationDeleteStackStepConfiguration.builder()
                              .stackName(getStackName(state.getCustomStackName(), state.isUseCustomStackName()))
                              .connectorRef(MigratorUtility.RUNTIME_INPUT)
                              .region(ParameterField.createValueField(state.getRegion()))
                              .roleArn(getRoleArn(state.getCloudFormationRoleArn()))
                              .build())
                    .build())
            .build();

    cloudformationDeleteStackStepNode.setCloudformationDeleteStackStepInfo(cloudformationDeleteStackStepInfo);
    return cloudformationDeleteStackStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    CloudFormationDeleteStackState state1 = (CloudFormationDeleteStackState) getState(stepYaml1);
    CloudFormationDeleteStackState state2 = (CloudFormationDeleteStackState) getState(stepYaml2);
    return false;
  }
}
