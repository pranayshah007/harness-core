/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.pcf;

import static software.wings.beans.InstanceUnitType.PERCENTAGE;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.tas.TasAppResizeStepInfo;
import io.harness.cdng.tas.TasAppResizeStepNode;
import io.harness.cdng.tas.TasCountInstanceSelection;
import io.harness.cdng.tas.TasInstanceSelectionWrapper;
import io.harness.cdng.tas.TasInstanceUnitType;
import io.harness.cdng.tas.TasPercentageInstanceSelection;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.beans.InstanceUnitType;
import software.wings.sm.State;
import software.wings.sm.states.pcf.PcfDeployState;

import java.util.Map;

public class PcfDeployStepMapperImpl extends PcfAbstractStepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.TAS_APP_RESIZE;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    PcfDeployState state = new PcfDeployState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.TAS;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    PcfDeployState state = (PcfDeployState) getState(graphNode);
    TasAppResizeStepNode tasAppResizeStepNode = new TasAppResizeStepNode();
    baseSetup(state, tasAppResizeStepNode);
    TasAppResizeStepInfo tasAppResizeStepInfo =
        TasAppResizeStepInfo.infoBuilder()
            .newAppInstances(getInstanceSelectionWrapper(state.getInstanceCount(), state.getInstanceUnitType()))
            .oldAppInstances(
                getInstanceSelectionWrapper(state.getDownsizeInstanceCount(), state.getDownsizeInstanceUnitType()))
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getTags()))
            .build();
    tasAppResizeStepNode.setTasAppResizeStepInfo(tasAppResizeStepInfo);
    return tasAppResizeStepNode;
  }

  private TasInstanceSelectionWrapper getInstanceSelectionWrapper(
      Integer instanceCount, InstanceUnitType instanceUnitType) {
    if (null == instanceCount || null == instanceUnitType) {
      return null;
    }
    if (PERCENTAGE.equals(instanceUnitType)) {
      return TasInstanceSelectionWrapper.builder()
          .type(TasInstanceUnitType.PERCENTAGE)
          .spec(TasPercentageInstanceSelection.builder()
                    .value(ParameterField.createValueField(instanceCount.toString()))
                    .build())
          .build();
    } else {
      return TasInstanceSelectionWrapper.builder()
          .type(TasInstanceUnitType.COUNT)
          .spec(TasCountInstanceSelection.builder()
                    .value(ParameterField.createValueField(instanceCount.toString()))
                    .build())
          .build();
    }
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
