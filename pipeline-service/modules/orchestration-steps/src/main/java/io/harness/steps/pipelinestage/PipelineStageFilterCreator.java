/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.pipelinestage;

import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Set;

public class PipelineStageFilterCreator extends GenericStageFilterJsonCreatorV2<PipelineStageNode> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE);
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, PipelineStageNode stageNode) {
    // No filter required for pipeline stage, will be shown in all modules CI, CD etc.
    return null;
  }

  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, PipelineStageNode stageNode) {
    // TODO: need to be completed
    return null;
  }

  @Override
  public Class<PipelineStageNode> getFieldClass() {
    return PipelineStageNode.class;
  }
}
