/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.pcf;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.tas.TasSwapRollbackStepInfo;
import io.harness.cdng.tas.TasSwapRollbackStepNode;
import io.harness.cdng.tas.TasSwapRoutesStepInfo;
import io.harness.cdng.tas.TasSwapRoutesStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.pcf.PcfSwitchBlueGreenRoutes;

import java.util.Map;

public class PcfBGMapRouteStepMapperImpl extends PcfAbstractStepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    PcfSwitchBlueGreenRoutes state = (PcfSwitchBlueGreenRoutes) getState(stepYaml);
    if (stepYaml.isRollback()) {
      return StepSpecTypeConstants.TAS_SWAP_ROUTES;
    } else {
      return StepSpecTypeConstants.SWAP_ROLLBACK;
    }
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.TAS;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    PcfSwitchBlueGreenRoutes state = new PcfSwitchBlueGreenRoutes(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    PcfSwitchBlueGreenRoutes state = (PcfSwitchBlueGreenRoutes) getState(graphNode);

    if (graphNode.isRollback()) {
      TasSwapRollbackStepNode tasSwapRollbackStepNode = new TasSwapRollbackStepNode();
      baseSetup(state, tasSwapRollbackStepNode, context.getIdentifierCaseFormat());
      TasSwapRollbackStepInfo tasSwapRollbackStepInfo =
          TasSwapRollbackStepInfo.infoBuilder()
              .upsizeInActiveApp(ParameterField.createValueField(state.isUpSizeInActiveApp()))
              .build();
      tasSwapRollbackStepNode.setTasSwapRollbackStepInfo(tasSwapRollbackStepInfo);
      return tasSwapRollbackStepNode;
    } else {
      TasSwapRoutesStepNode tasSwapRoutesStepNode = new TasSwapRoutesStepNode();
      baseSetup(state, tasSwapRoutesStepNode, context.getIdentifierCaseFormat());
      TasSwapRoutesStepInfo tasSwapRoutesStepInfo =
          TasSwapRoutesStepInfo.infoBuilder()
              .downSizeOldApplication(ParameterField.createValueField(state.isDownsizeOldApps()))
              .build();
      tasSwapRoutesStepNode.setTasSwapRoutesStepInfo(tasSwapRoutesStepInfo);
      return tasSwapRoutesStepNode;
    }
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
