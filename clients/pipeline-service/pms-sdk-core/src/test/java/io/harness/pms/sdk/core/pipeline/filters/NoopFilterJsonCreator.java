/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.filters;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class NoopFilterJsonCreator implements FilterJsonCreator<YamlField> {
  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("pipeline", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, YamlField yamlField) {
    return FilterCreationResponse.builder().build();
  }
}
