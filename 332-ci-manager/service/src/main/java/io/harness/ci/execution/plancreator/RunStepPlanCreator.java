/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.google.common.collect.Sets;
import java.util.Set;

public class RunStepPlanCreator extends CIPMSStepPlanCreatorV2<RunStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.RUN.getDisplayName());
  }

  @Override
  public Class<RunStepNode> getFieldClass() {
    return RunStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, RunStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}
