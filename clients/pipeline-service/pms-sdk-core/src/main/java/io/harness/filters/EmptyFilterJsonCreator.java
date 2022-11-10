/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class EmptyFilterJsonCreator implements FilterJsonCreator<YamlField> {
  private final String identifier;
  private final Set<String> types;

  public EmptyFilterJsonCreator(String identifier, Set<String> types) {
    this.identifier = identifier;
    this.types = types;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(identifier, types);
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
