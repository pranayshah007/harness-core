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
public class MonitoredServiceYamlExpressionEvaluator extends EngineExpressionEvaluator {
  private MonitoredServiceYamlExpressionFunctor monitoredServiceYamlExpressionFunctor;

  @Builder
  public MonitoredServiceYamlExpressionEvaluator(String yaml) {
    super(null);
    monitoredServiceYamlExpressionFunctor =
        MonitoredServiceYamlExpressionFunctor.builder().rootYamlField(getMonitoredServiceYamlField(yaml)).build();
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("monitoredService", monitoredServiceYamlExpressionFunctor);
  }

  @Override
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    return listBuilder.add("monitoredService").addAll(super.fetchPrefixes()).build();
  }

  private YamlField getMonitoredServiceYamlField(String yaml) {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getField("monitoredService");
    } catch (IOException e) {
      throw new InvalidRequestException("Not valid yaml passed.");
    }
  }
}
