/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;

import java.util.Map;
import java.util.Set;

public interface VariableCreator<T> {
  Map<String, Set<String>> getSupportedTypes();
  VariableCreationResponse createVariablesForField(VariableCreationContext ctx, YamlField field);
  default VariableCreationResponse createVariablesForFieldV2(VariableCreationContext ctx, T field) {
    return VariableCreationResponse.builder().build();
  }

  default Class<T> getFieldClass() {
    return null;
  }
}
