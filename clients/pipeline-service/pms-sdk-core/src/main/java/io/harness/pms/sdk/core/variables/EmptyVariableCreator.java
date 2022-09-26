/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class EmptyVariableCreator implements VariableCreator<YamlField> {
  private final String identifier;
  private final Set<String> types;

  public EmptyVariableCreator(String identifier, Set<String> types) {
    this.identifier = identifier;
    this.types = types;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(identifier, types);
  }

  @Override
  public VariableCreationResponse createVariablesForField(VariableCreationContext ctx, YamlField field) {
    return VariableCreationResponse.builder().build();
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }
}
