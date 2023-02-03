/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.evaluators;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.expressionEvaluator.CustomSecretFunctor;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class CDExpressionEvaluator extends EngineExpressionEvaluator {
  private String yaml;
  private String fqnPathToElement;
  private List<YamlField> aliasYamlFields;
  private int secretFunctor;

  public CDExpressionEvaluator() {
    super(null);
  }

  public CDExpressionEvaluator(
      String yaml, String fqnPathToElement, List<YamlField> aliasYamlFields, int secretFunctor) {
    super(null);
    this.yaml = yaml;
    this.fqnPathToElement = fqnPathToElement;
    this.aliasYamlFields = aliasYamlFields;
    this.secretFunctor = secretFunctor;
  }

  @Override
  protected void initialize() {
    super.initialize();
    if (isNotEmpty(yaml)) {
      addToContext("__yamlExpression",
          CDYamlExpressionFunctor.builder()
              .rootYamlField(getPipelineYamlField())
              .fqnPathToElement(fqnPathToElement)
              .aliasYamlFields(aliasYamlFields)
              .build());
    }

    addToContext("secrets", new CustomSecretFunctor(secretFunctor));
  }

  @Override
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    return listBuilder.add("__yamlExpression").addAll(super.fetchPrefixes()).build();
  }

  /**
   * Evaluate the properties value and return the map with the names and evaluated values.
   * If the property value is unresolved the expressions will be put in the map.
   *
   * @param properties the map of properties where property value is expression
   * @param contextMap context
   * @return evaluated map of properties
   */
  public Map<String, Object> evaluateProperties(Map<String, String> properties, Map<String, Object> contextMap) {
    Map<String, Object> propertyNameEvaluatedMap = new HashMap<>();
    for (Map.Entry<String, String> property : properties.entrySet()) {
      if (isEmpty(property.getValue())) {
        continue;
      }
      if (!EngineExpressionEvaluator.hasExpressions(property.getValue())) {
        propertyNameEvaluatedMap.put(property.getKey(), property.getValue());
        continue;
      }
      Object evaluated =
          renderExpression(property.getValue(), contextMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

      propertyNameEvaluatedMap.put(property.getKey(), evaluated);
    }
    return propertyNameEvaluatedMap;
  }

  private YamlField getPipelineYamlField() {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE);
    } catch (IOException e) {
      throw new InvalidRequestException("Not valid yaml passed.");
    }
  }
}
