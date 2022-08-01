/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.variables;

import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.variables.ChildrenVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExecutionVariableCreator extends ChildrenVariableCreator<YamlField> {
  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stepYamlFields = VariableCreatorHelper.getStepYamlFields(config);
    for (YamlField stepYamlField : stepYamlFields) {
      Map<String, YamlField> stepYamlFieldMap = new HashMap<>();
      stepYamlFieldMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
      responseMap.put(stepYamlField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(stepYamlFieldMap))
              .build());
    }

    YamlField rollbackStepsField = config.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS);
    if (rollbackStepsField != null) {
      List<YamlNode> yamlNodes = rollbackStepsField.getNode().asArray();
      List<YamlField> rollbackStepYamlFields = VariableCreatorHelper.getStepYamlFields(yamlNodes);
      for (YamlField stepYamlField : rollbackStepYamlFields) {
        Map<String, YamlField> stepYamlFieldMap = new HashMap<>();
        stepYamlFieldMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
        responseMap.put(stepYamlField.getNode().getUuid(),
            VariableCreationResponse.builder()
                .dependencies(DependenciesUtils.toDependenciesProto(stepYamlFieldMap))
                .build());
      }
    }
    return responseMap;
  }

  @Override
  public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
    return VariableCreationResponse.builder().build();
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.EXECUTION, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, YamlField config) {
    return createVariablesForChildrenNodes(ctx, config);
  }

  @Override
  public VariableCreationResponse createVariablesForParentNodeV2(VariableCreationContext ctx, YamlField config) {
    return createVariablesForParentNode(ctx, config);
  }
}
