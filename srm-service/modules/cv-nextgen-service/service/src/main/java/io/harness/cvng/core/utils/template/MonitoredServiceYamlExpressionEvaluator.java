/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.template;

import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MonitoredServiceYamlExpressionEvaluator extends EngineExpressionEvaluator {
  protected final String yaml;

  public MonitoredServiceYamlExpressionEvaluator(String yaml) {
    super(null);
    this.yaml = yaml;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("__yamlExpression",
        MonitoredServiceYamlExpressionFunctor.builder().rootYamlField(getMonitoredServiceYamlField()).build());
  }

  @Override
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    return listBuilder.add("__yamlExpression").addAll(super.fetchPrefixes()).build();
  }

  private YamlField getMonitoredServiceYamlField() {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getField("monitoredService");
    } catch (IOException e) {
      throw new InvalidRequestException("Not valid yaml passed.");
    }
  }
}
