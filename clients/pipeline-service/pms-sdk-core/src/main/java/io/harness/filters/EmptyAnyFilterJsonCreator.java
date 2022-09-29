/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.pms.plan.creation.PlanCreatorUtils.ANY_TYPE;

import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;

/**
 * An empty filter creator without any additional rules.
 *
 * Create the map of supported types to each element received in constructor and the keyword {@value
 * io.harness.pms.plan.creation.PlanCreatorUtils#ANY_TYPE} as value.
 */
public class EmptyAnyFilterJsonCreator implements FilterJsonCreator<YamlField> {
  private final Set<String> types;

  public EmptyAnyFilterJsonCreator(Set<String> types) {
    if (CollectionUtils.isEmpty(types)) {
      throw new IllegalArgumentException("Type list cannot be null or empty");
    }
    this.types = types;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    types.forEach(type -> supportedTypes.put(type, Collections.singleton(ANY_TYPE)));
    return supportedTypes;
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, YamlField yamlField) {
    return FilterCreationResponse.builder().build();
  }
}
