/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.pipelinestage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineStagePlanCreator implements PartialPlanCreator<PipelineStageNode> {
  @Override
  public Class<PipelineStageNode> getFieldClass() {
    return PipelineStageNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE));
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PipelineStageNode field) {
    // TODO: to be completed
    return null;
  }
}
