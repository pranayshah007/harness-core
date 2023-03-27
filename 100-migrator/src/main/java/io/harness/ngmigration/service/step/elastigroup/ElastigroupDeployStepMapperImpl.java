/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.elastigroup;

import io.harness.cdng.common.capacity.Capacity;
import io.harness.cdng.common.capacity.CapacitySpecType;
import io.harness.cdng.common.capacity.CountCapacitySpec;
import io.harness.cdng.common.capacity.PercentageCapacitySpec;
import io.harness.cdng.elastigroup.deploy.ElastigroupDeployStepInfo;
import io.harness.cdng.elastigroup.deploy.ElastigroupDeployStepNode;
import io.harness.data.structure.CompareUtils;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.beans.InstanceUnitType;
import software.wings.sm.State;
import software.wings.sm.states.spotinst.SpotInstDeployState;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class ElastigroupDeployStepMapperImpl extends StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.ELASTIGROUP_DEPLOY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    SpotInstDeployState state = new SpotInstDeployState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    SpotInstDeployState state = (SpotInstDeployState) getState(graphNode);

    ElastigroupDeployStepNode node = new ElastigroupDeployStepNode();
    baseSetup(state, node, context.getIdentifierCaseFormat());
    ElastigroupDeployStepInfo elastigroupDeployStepInfo =
        ElastigroupDeployStepInfo.infoBuilder()
            .newService(getCapacity(state.getInstanceCount(), state.getInstanceUnitType()))
            .oldService(getCapacity(state.getDownsizeInstanceCount(), state.getDownsizeInstanceUnitType()))
            .build();

    node.setElastigroupDeployStepInfo(elastigroupDeployStepInfo);
    return node;
  }

  private Capacity getCapacity(Integer unit, InstanceUnitType unitType) {
    if (unit == null || unitType == null) {
      return null;
    }
    Capacity capacity;
    if (unitType == InstanceUnitType.COUNT) {
      capacity = Capacity.builder()
                     .type(CapacitySpecType.COUNT)
                     .spec(CountCapacitySpec.builder().count(ParameterField.createValueField(unit)).build())
                     .build();
    } else {
      capacity = Capacity.builder()
                     .type(CapacitySpecType.PERCENTAGE)
                     .spec(PercentageCapacitySpec.builder().percentage(ParameterField.createValueField(unit)).build())
                     .build();
    }
    return capacity;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    SpotInstDeployState state1 = (SpotInstDeployState) getState(stepYaml1);
    SpotInstDeployState state2 = (SpotInstDeployState) getState(stepYaml2);
    return state1.getDownsizeInstanceUnitType() == state2.getDownsizeInstanceUnitType()
        && StringUtils.equals(state1.getName(), state2.getName())
        && CompareUtils.compareObjects(state1.getDownsizeInstanceCount(), state2.getDownsizeInstanceCount())
        && CompareUtils.compareObjects(state1.getInstanceCount(), state2.getInstanceCount());
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }
}
