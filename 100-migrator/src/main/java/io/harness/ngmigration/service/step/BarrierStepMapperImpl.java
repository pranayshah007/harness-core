/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;
import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.barrier.BarrierStepInfo;
import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.BarrierState;

import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class BarrierStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.BARRIER;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    BarrierState state = new BarrierState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    BarrierState state = (BarrierState) getState(graphNode);
    BarrierStepNode barrierStepNode = new BarrierStepNode();
    baseSetup(graphNode, barrierStepNode, context.getIdentifierCaseFormat());
    BarrierStepInfo barrierStepInfo = BarrierStepInfo.builder()
                                          .name(state.getName())
                                          .identifier(String.format("%s.default('%s')", RUNTIME_INPUT,
                                              MigratorUtility.generateIdentifier(state.getIdentifier(),
                                                  migrationContext.getInputDTO().getIdentifierCaseFormat())))
                                          .build();
    barrierStepNode.setBarrierStepInfo(barrierStepInfo);
    return barrierStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    // Barrier steps are pretty much same across.
    return true;
  }
}
