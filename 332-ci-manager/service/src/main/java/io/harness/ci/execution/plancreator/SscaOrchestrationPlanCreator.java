/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.SscaOrchestrationStepNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import java.util.Set;

public class SscaOrchestrationPlanCreator extends CIPMSStepPlanCreatorV2<SscaOrchestrationStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.SSCA_ORCHESTRATION.getDisplayName());
  }

  @Override
  public Class<SscaOrchestrationStepNode> getFieldClass() {
    return SscaOrchestrationStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, SscaOrchestrationStepNode stepElement) {
    if (!"CI".equals(getStageType(ctx.getCurrentField()))) {
      stepElement.getSscaOrchestrationStepInfo().setStepType("dummy");
    } else {
      stepElement.getSscaOrchestrationStepInfo().setStepType(CIStepInfoType.SSCA_ORCHESTRATION.getDisplayName());
    }
    return super.createPlanForField(ctx, stepElement);
  }

  public String getStageType(YamlField currentField) {
    YamlNode stageNode = YamlUtils.findParentNode(currentField.getNode(), STAGE);
    if (stageNode == null) {
      return null;
    }
    return stageNode.getType();
  }
}
