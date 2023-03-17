/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.cloudformation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStepConfiguration;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.CloudFormationRollbackStackState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class CloudformationRollbackStepMapperImpl extends BaseCloudformationProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    CloudFormationRollbackStackState state = new CloudFormationRollbackStackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    CloudformationRollbackStepInfo cloudformationRollbackStepInfo =
        CloudformationRollbackStepInfo.infoBuilder()
            .cloudformationStepConfiguration(CloudformationRollbackStepConfiguration.builder()
                                                 .provisionerIdentifier(MigratorUtility.RUNTIME_INPUT)
                                                 .build())
            .build();

    CloudformationRollbackStepNode cloudformationRollbackStepNode = new CloudformationRollbackStepNode();
    cloudformationRollbackStepNode.setCloudformationRollbackStepInfo(cloudformationRollbackStepInfo);
    baseSetup(graphNode, cloudformationRollbackStepNode, context.getIdentifierCaseFormat());
    return cloudformationRollbackStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
