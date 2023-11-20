/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.asg;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.aws.asg.AsgCurrentRunningInstances;
import io.harness.cdng.aws.asg.AsgFixedInstances;
import io.harness.cdng.aws.asg.AsgInstances;
import io.harness.cdng.aws.asg.AsgInstancesSpec;
import io.harness.ngmigration.beans.StepOutput;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.step.AsgAmiStepFunctor;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.WorkflowPhase;
import software.wings.sm.states.AwsAmiServiceSetup;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public abstract class AsgBaseStepMapper extends StepMapper {
  @Override
  public List<StepExpressionFunctor> getExpressionFunctor(
      WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode) {
    String sweepingOutputName = "ami";

    return Lists.newArrayList(String.format("context.%s", sweepingOutputName), String.format("%s", sweepingOutputName))
        .stream()
        .map(exp
            -> StepOutput.builder()
                   .stageIdentifier(
                       MigratorUtility.generateIdentifier(phase.getName(), context.getIdentifierCaseFormat()))
                   .stepIdentifier(
                       MigratorUtility.generateIdentifier(graphNode.getName(), context.getIdentifierCaseFormat()))
                   .stepGroupIdentifier(
                       MigratorUtility.generateIdentifier(phaseStep.getName(), context.getIdentifierCaseFormat()))
                   .expression(exp)
                   .build())
        .map(so -> new AsgAmiStepFunctor(so, context.getWorkflow()))
        .collect(Collectors.toList());
  }

  protected AsgInstances getAsgInstancesNode(AwsAmiServiceSetup state) {
    AsgInstancesSpec spec;
    if (state.isUseCurrentRunningCount()) {
      spec = AsgCurrentRunningInstances.builder().build();
    } else {
      spec = AsgFixedInstances.builder()
                 .max(getParameterFieldFromInstances(state.getMaxInstances()))
                 .min(getParameterFieldFromInstances(state.getMinInstances()))
                 .desired(getParameterFieldFromInstances(state.getDesiredInstances()))
                 .build();
    }
    return AsgInstances.builder().spec(spec).type(spec.getType()).build();
  }

  private ParameterField<Integer> getParameterFieldFromInstances(String instanceValue) {
    try {
      return ParameterField.createValueField(Integer.parseInt(instanceValue));
    } catch (NumberFormatException e) {
      return ParameterField.createExpressionField(true, instanceValue, null, true);
    }
  }
}
