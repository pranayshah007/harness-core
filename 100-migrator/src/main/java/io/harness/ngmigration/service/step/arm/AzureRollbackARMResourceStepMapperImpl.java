/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.arm;

import io.harness.cdng.provision.azure.AzureARMRollbackStepInfo;
import io.harness.cdng.provision.azure.AzureARMRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.ARMRollbackState;

import java.util.Map;

public class AzureRollbackARMResourceStepMapperImpl extends BaseAzureARMProvisionerMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.AZURE_ROLLBACK_ARM_RESOURCE;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    ARMRollbackState state = new ARMRollbackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    AzureARMRollbackStepNode azureARMRollbackStepNode = new AzureARMRollbackStepNode();
    baseSetup(graphNode, azureARMRollbackStepNode, context.getIdentifierCaseFormat());

    AzureARMRollbackStepInfo azureARMRollbackStepInfo = new AzureARMRollbackStepInfo();
    azureARMRollbackStepInfo.setProvisionerIdentifier(getProvisionerIdentifier());
    azureARMRollbackStepNode.setAzureARMRollbackStepInfo(azureARMRollbackStepInfo);
    return azureARMRollbackStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }
}
