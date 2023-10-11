/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables.v1;

import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;

import java.util.LinkedHashMap;

public abstract class ChildrenVariableCreatorV1<T> implements VariableCreator<T> {
  public abstract LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config);

  public abstract VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config);

  @Override
  public VariableCreationResponse createVariablesForField(VariableCreationContext ctx, YamlField field) {
    VariableCreationResponse finalResponse = VariableCreationResponse.builder().build();
    return finalResponse;
  }

  @Override
  public VariableCreationResponse createVariablesForFieldV2(VariableCreationContext ctx, T field) {
    VariableCreationResponse finalResponse = VariableCreationResponse.builder().build();
    return finalResponse;
  }

  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, T config) {
    return new LinkedHashMap<>();
  }

  public VariableCreationResponse createVariablesForParentNodeV2(VariableCreationContext ctx, T config) {
    return VariableCreationResponse.builder().build();
  }
}
