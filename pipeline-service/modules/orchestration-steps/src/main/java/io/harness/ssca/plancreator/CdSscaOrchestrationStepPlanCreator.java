/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.plancreator;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.pluginstep.AbstractContainerStepPlanCreator;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.cd.beans.stepnode.CdSscaOrchestrationStepNode;
import io.harness.steps.plugin.ContainerCommandUnitConstants;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaOrchestrationStepPlanCreator extends AbstractContainerStepPlanCreator<CdSscaOrchestrationStepNode> {
  @Override
  public Class<CdSscaOrchestrationStepNode> getFieldClass() {
    return CdSscaOrchestrationStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Collections.singleton(SscaConstants.CD_SSCA_ORCHESTRATION));
  }

  @Override
  public PlanNode createPlanForStep(String stepNodeId, StepParameters stepParameters) {
    return PlanNode.builder()
        .uuid(stepNodeId)
        .name(ContainerCommandUnitConstants.SscaOrchestrationStep)
        .identifier(ContainerCommandUnitConstants.SscaOrchestrationStep.replaceAll("\\s", ""))
        .stepType(SscaConstants.CD_SSCA_ORCHESTRATION_STEP_TYPE)
        .group(StepOutcomeGroup.STEP.name())
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(false)
        .skipGraphType(SkipType.NOOP)
        .build();
  }
}
