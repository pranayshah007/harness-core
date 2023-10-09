/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.ActionStepNode;
import io.harness.beans.steps.nodes.V1.ActionStepNodeV1;
import io.harness.beans.steps.stepinfo.ActionStepInfo;
import io.harness.beans.steps.stepinfo.V1.ActionStepInfoV1;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.google.common.collect.Sets;
import java.util.Set;

public class ActionStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<ActionStepNodeV1> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.ACTION_V1.getDisplayName());
  }

  @Override
  public Class<ActionStepNodeV1> getFieldClass() {
    return ActionStepNodeV1.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ActionStepNodeV1 stepElement) {
    return super.createPlanForFieldV2(ctx, stepElement);
  }

  @Override
  public CIAbstractStepNode getStepNode(ActionStepNodeV1 stepElement) {
    ActionStepInfoV1 stepInfo = stepElement.getActionStepInfoV1();
    return ActionStepNode.builder()
        .uuid(stepElement.getUuid())
        .identifier(IdentifierGeneratorUtils.getId(stepElement.getName()))
        .name(stepElement.getName())
        .failureStrategies(stepElement.getFailureStrategies())
        .timeout(stepElement.getTimeout())
        .actionStepInfo(
            ActionStepInfo.builder().uses(stepInfo.getUses()).env(stepInfo.getEnvs()).with(stepInfo.getWith()).build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }
}
