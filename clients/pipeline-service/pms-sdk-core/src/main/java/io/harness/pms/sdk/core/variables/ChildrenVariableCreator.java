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

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ChildrenVariableCreator<T> implements VariableCreator<T> {
  public abstract LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config);

  public abstract VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config);

  @Override
  public VariableCreationResponse createVariablesForField(VariableCreationContext ctx, YamlField field) {
    VariableCreationResponse finalResponse = VariableCreationResponse.builder().build();

    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodes =
        createVariablesForChildrenNodes(ctx, field);
    for (Map.Entry<String, VariableCreationResponse> entry : variablesForChildrenNodes.entrySet()) {
      mergeResponses(finalResponse, entry.getValue());
    }
    VariableCreationResponse variablesForParentNode = createVariablesForParentNode(ctx, field);
    mergeResponses(finalResponse, variablesForParentNode);
    return finalResponse;
  }

  @Override
  public VariableCreationResponse createVariablesForFieldV2(VariableCreationContext ctx, T field) {
    VariableCreationResponse finalResponse = VariableCreationResponse.builder().build();

    VariableCreationResponse variablesForParentNode = createVariablesForParentNodeV2(ctx, field);
    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodes =
        createVariablesForChildrenNodesV2(ctx, field);
    for (Map.Entry<String, VariableCreationResponse> entry : variablesForChildrenNodes.entrySet()) {
      mergeResponses(finalResponse, entry.getValue());
    }
    mergeResponses(finalResponse, variablesForParentNode);
    return finalResponse;
  }

  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, T config) {
    return new LinkedHashMap<>();
  }

  public VariableCreationResponse createVariablesForParentNodeV2(VariableCreationContext ctx, T config) {
    return VariableCreationResponse.builder().build();
  }

  private void mergeResponses(VariableCreationResponse finalResponse, VariableCreationResponse givenResponse) {
    finalResponse.addYamlProperties(givenResponse.getYamlProperties());
    finalResponse.addYamlOutputProperties(givenResponse.getYamlOutputProperties());
    finalResponse.addYamlExtraProperties(givenResponse.getYamlExtraProperties());
    finalResponse.addResolvedDependencies(givenResponse.getResolvedDependencies());
    finalResponse.addDependencies(givenResponse.getDependencies());
    finalResponse.addYamlUpdates(givenResponse.getYamlUpdates());
  }
}
