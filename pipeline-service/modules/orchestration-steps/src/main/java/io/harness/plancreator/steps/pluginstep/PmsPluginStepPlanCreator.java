/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.plugin.PmsPluginStepNode;

import com.google.common.collect.Sets;
import java.util.Set;

public class PmsPluginStepPlanCreator extends PMSStepPlanCreatorV2<PmsPluginStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.PLUGIN_STEP);
  }

  @Override
  public Class<PmsPluginStepNode> getFieldClass() {
    return PmsPluginStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PmsPluginStepNode field) {
    return super.createPlanForField(ctx, field);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V0);
  }
}
