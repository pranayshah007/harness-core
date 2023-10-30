/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.BitriseStepNode;
import io.harness.beans.steps.nodes.V1.BitriseStepNodeV1;
import io.harness.beans.steps.stepinfo.BitriseStepInfo;
import io.harness.beans.steps.stepinfo.V1.BitriseStepInfoV1;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.google.common.collect.Sets;
import java.util.Set;

public class BitriseStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<BitriseStepNodeV1> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.BITRISE_V1.getDisplayName());
  }

  @Override
  public Class<BitriseStepNodeV1> getFieldClass() {
    return BitriseStepNodeV1.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, BitriseStepNodeV1 stepElement) {
    return super.createPlanForFieldV2(ctx, stepElement);
  }

  @Override
  public CIAbstractStepNode getStepNode(BitriseStepNodeV1 stepElement) {
    BitriseStepInfoV1 stepInfo = stepElement.getBitriseStepInfoV1();
    return BitriseStepNode.builder()
        .uuid(stepElement.getUuid())
        .identifier(IdentifierGeneratorUtils.getId(stepElement.getName()))
        .name(stepElement.getName())
        .failureStrategies(stepElement.getFailureStrategies())
        .timeout(stepElement.getTimeout())
        .bitriseStepInfo(
            BitriseStepInfo.builder().uses(stepInfo.getUses()).env(stepInfo.getEnvs()).with(stepInfo.getWith()).build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }
}
