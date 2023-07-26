/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StrategyVariableCreator implements VariableCreator<StrategyConfig> {
  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STRATEGY, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public VariableCreationResponse createVariablesForField(VariableCreationContext ctx, YamlField field) {
    return VariableCreationResponse.builder().build();
  }

  @Override
  public VariableCreationResponse createVariablesForFieldV2(VariableCreationContext ctx, StrategyConfig config) {
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    if (ParameterField.isNotNull(config.getMatrixConfig()) && config.getMatrixConfig().getValue() != null) {
      Set<String> axisKeys = new HashSet<>(((MatrixConfig) config.getMatrixConfig().getValue()).getAxes().keySet());
      axisKeys.addAll(((MatrixConfig) config.getMatrixConfig().getValue()).getExpressionAxes().keySet());
      String placeHolder = "matrix.%s";
      for (String axis : axisKeys) {
        String qualifiedName = String.format(placeHolder, axis);
        yamlPropertiesMap.put(generateUuid(),
            YamlProperties.newBuilder().setFqn(qualifiedName).setLocalName(qualifiedName).setVisible(true).build());
      }
    }
    if (config.getRepeat() != null) {
      if (!ParameterField.isBlank(config.getRepeat().getItems())) {
        String forExpression = "repeat.item";
        yamlPropertiesMap.put(generateUuid(),
            YamlProperties.newBuilder().setFqn(forExpression).setLocalName(forExpression).setVisible(true).build());
      }
    }
    String currentIterationExpression = "strategy.iteration";
    String totalIterationExpression = "strategy.iterations";

    yamlPropertiesMap.put(generateUuid(),
        YamlProperties.newBuilder()
            .setFqn(currentIterationExpression)
            .setLocalName(currentIterationExpression)
            .setVisible(true)
            .build());
    yamlPropertiesMap.put(generateUuid(),
        YamlProperties.newBuilder()
            .setFqn(totalIterationExpression)
            .setLocalName(totalIterationExpression)
            .setVisible(true)
            .build());

    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  @Override
  public Class<StrategyConfig> getFieldClass() {
    return StrategyConfig.class;
  }
}
