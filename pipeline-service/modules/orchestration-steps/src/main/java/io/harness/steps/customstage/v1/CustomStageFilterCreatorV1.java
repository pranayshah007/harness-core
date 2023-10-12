/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.customstage.v1;

import io.harness.filters.v1.GenericStageFilterJsonCreatorV3;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.steps.StepSpecTypeConstantsV1;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CustomStageFilterCreatorV1 extends GenericStageFilterJsonCreatorV3<CustomStageNodeV1> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return new HashSet<>(Arrays.asList(StepSpecTypeConstantsV1.CUSTOM_STAGE));
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, CustomStageNodeV1 stageNode) {
    return null;
  }

  @Override
  public Class<CustomStageNodeV1> getFieldClass() {
    return CustomStageNodeV1.class;
  }
}
