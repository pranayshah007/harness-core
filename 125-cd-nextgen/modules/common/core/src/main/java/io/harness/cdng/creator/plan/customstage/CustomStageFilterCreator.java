/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.customstage;

import static io.harness.pms.yaml.YAMLFieldNameConstants.CUSTOM;

import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;

import java.util.Collections;
import java.util.Set;

public class CustomStageFilterCreator extends GenericStageFilterJsonCreatorV2<CustomStageNode> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton(CUSTOM);
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, CustomStageNode stageNode) {
    // No filter required for custom stage, will be shown in all modules CI, CD etc.
    return null;
  }

  @Override
  public Class<CustomStageNode> getFieldClass() {
    return CustomStageNode.class;
  }
}
