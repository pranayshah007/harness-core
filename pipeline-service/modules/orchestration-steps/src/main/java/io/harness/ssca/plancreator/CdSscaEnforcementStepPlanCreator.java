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
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.cd.beans.enforcement.CdSscaEnforcementStepInfo;
import io.harness.ssca.cd.beans.enforcement.CdSscaEnforcementStepNode;
import io.harness.steps.plugin.ContainerCommandUnitConstants;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaEnforcementStepPlanCreator extends AbstractContainerStepPlanCreator<CdSscaEnforcementStepNode> {
  @Override
  public Class<CdSscaEnforcementStepNode> getFieldClass() {
    return CdSscaEnforcementStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Collections.singleton(SscaConstants.CD_SSCA_ENFORCEMENT));
  }

  @Override
  public PlanNode createPlanForStep(
      String stepNodeId, StepParameters stepParameters, List<AdviserObtainment> adviserObtainments) {
    CdSscaEnforcementStepInfo stepInfo = (CdSscaEnforcementStepInfo) stepParameters;
    return PlanNode.builder()
        .uuid(stepNodeId)
        .name(ContainerCommandUnitConstants.SscaEnforcementStep)
        .identifier(ContainerCommandUnitConstants.SscaEnforcementStep.replaceAll("\\s", ""))
        .stepType(SscaConstants.CD_SSCA_ENFORCEMENT_STEP_TYPE)
        .group(StepOutcomeGroup.STEP.name())
        .stepParameters(stepParameters)
        .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                   .setType(FacilitatorType.newBuilder().setType(stepInfo.getFacilitatorType()).build())
                                   .build())
        .skipExpressionChain(false)
        .skipGraphType(SkipType.NOOP)
        .build();
  }
}
