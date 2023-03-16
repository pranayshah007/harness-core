/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.evaluators;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class CDYamlExpressionEvaluator extends EngineExpressionEvaluator {
  private final String yaml;
  private final String fqnPathToElement;
  private final List<YamlField> aliasYamlFields;

  public CDYamlExpressionEvaluator(String yaml, String fqnPathToElement, List<YamlField> aliasYamlFields) {
    super(null);
    this.yaml = yaml;
    this.fqnPathToElement = fqnPathToElement;
    this.aliasYamlFields = aliasYamlFields;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("__yamlExpression",
        CDYamlExpressionFunctor.builder()
            .rootYamlField(getPipelineYamlField())
            .fqnPathToElement(fqnPathToElement)
            .aliasYamlFields(aliasYamlFields)
            .build());
  }

  @Override
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    return listBuilder.add("__yamlExpression").addAll(super.fetchPrefixes()).build();
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
