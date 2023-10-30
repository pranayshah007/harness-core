/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions.step;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.ngmigration.beans.StepOutput;

import software.wings.beans.Workflow;

import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class AsgAmiStepFunctor extends StepExpressionFunctor {
  private final StepOutput stepOutput;
  private final Workflow workflow;
  private final String ngKeyGroup = "autoScalingGroupName";

  public AsgAmiStepFunctor(StepOutput stepOutput, Workflow workflow) {
    super(stepOutput);
    this.stepOutput = stepOutput;
    this.workflow = workflow;
  }

  @Override
  public synchronized Object get(Object keyObject) {
    String key = keyObject.toString();

    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    if (OrchestrationWorkflowType.BLUE_GREEN == workflowType) {
      return handleASGBlueGreenWorkflow(key);
    }

    return handleASGBasicOrCanaryWorkflow(key);
  }

  private Object handleASGBlueGreenWorkflow(String key) {
    if (key.equals("oldAsgName") || key.equals("ami.oldAsgName")) {
      return handleOldAsgVariableMigration();
    }

    if (key.equals("newAsgName") || key.equals("ami.newAsgName")) {
      return handleNewAsgVariableMigration();
    }
    return null;
  }

  private String handleNewAsgVariableMigration() {
    if (StringUtils.equals(stepOutput.getStageIdentifier(), getCurrentStageIdentifier())) {
      return String.format("<+execution.steps.%s.steps.%s.output.prodAsg.%s>", stepOutput.getStepGroupIdentifier(),
          stepOutput.getStepIdentifier(), ngKeyGroup);
    }

    return String.format("<+pipeline.stages.%s.spec.execution.steps.%s.steps.%s.output.prodAsg.%s>",
        stepOutput.getStageIdentifier(), stepOutput.getStepGroupIdentifier(), stepOutput.getStepIdentifier(),
        ngKeyGroup);
  }

  private String handleOldAsgVariableMigration() {
    if (StringUtils.equals(stepOutput.getStageIdentifier(), getCurrentStageIdentifier())) {
      return String.format("<+execution.steps.%s.steps.%s.output.stageAsg.%s>", stepOutput.getStepGroupIdentifier(),
          stepOutput.getStepIdentifier(), ngKeyGroup);
    }

    return String.format("<+pipeline.stages.%s.spec.execution.steps.%s.steps.%s.output.stageAsg.%s>",
        stepOutput.getStageIdentifier(), stepOutput.getStepGroupIdentifier(), stepOutput.getStepIdentifier(),
        ngKeyGroup);
  }

  private Object handleASGBasicOrCanaryWorkflow(String key) {
    if (!key.equals("oldAsgName") && !key.equals("newAsgName")) {
      return null;
    }

    if (StringUtils.equals(stepOutput.getStageIdentifier(), getCurrentStageIdentifier())) {
      return String.format("<+execution.steps.%s.steps.%s.output.asg.%s>", stepOutput.getStepGroupIdentifier(),
          stepOutput.getStepIdentifier(), ngKeyGroup);
    }

    return String.format("<+pipeline.stages.%s.spec.execution.steps.%s.steps.%s.output.asg.%s>",
        stepOutput.getStageIdentifier(), stepOutput.getStepGroupIdentifier(), stepOutput.getStepIdentifier(),
        ngKeyGroup);
  }
}
