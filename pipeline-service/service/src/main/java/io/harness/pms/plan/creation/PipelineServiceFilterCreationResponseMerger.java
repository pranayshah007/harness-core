/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import io.harness.plancreator.PipelineServiceFilter;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.pipeline.filters.FilterCreationResponseMerger;

import java.util.HashSet;

public class PipelineServiceFilterCreationResponseMerger implements FilterCreationResponseMerger {
  @Override
  public void mergeFilterCreationResponse(FilterCreationResponse finalResponse, FilterCreationResponse current) {
    if (finalResponse.getPipelineFilter() == null) {
      finalResponse.setPipelineFilter(PipelineServiceFilter.builder().stageTypes(new HashSet<>()).build());
    }
    PipelineServiceFilter finalFilter = (PipelineServiceFilter) finalResponse.getPipelineFilter();
    PipelineServiceFilter currentFilter = (PipelineServiceFilter) current.getPipelineFilter();
    if (currentFilter != null) {
      finalFilter.setFeatureFlagStepCount(
          currentFilter.getFeatureFlagStepCount() + finalFilter.getFeatureFlagStepCount());
      finalFilter.mergeStageTypes(currentFilter.getStageTypes());
    }
  }
}
