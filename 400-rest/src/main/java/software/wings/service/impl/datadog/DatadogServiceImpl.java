/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.datadog;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.manifest.ObjectYamlUtils.encodeDot;

import io.harness.serializer.JsonUtils;

import software.wings.service.intfc.datadog.DatadogService;
import software.wings.sm.states.DatadogState;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DatadogServiceImpl implements DatadogService {
  private static final String COMMA_STR = ",";

  @Override
  public String getConcatenatedListOfMetricsForValidation(String defaultMetrics, Map<String, String> dockerMetrics,
      Map<String, String> kubernetesMetrics, Map<String, String> ecsMetrics) {
    String metricsString = defaultMetrics != null ? defaultMetrics : "";
    if (isNotEmpty(dockerMetrics)) {
      metricsString += String.join(COMMA_STR, dockerMetrics.values());
    }
    if (isNotEmpty(ecsMetrics)) {
      metricsString += String.join(COMMA_STR, ecsMetrics.values());
    }
    if (isNotEmpty(kubernetesMetrics)) {
      metricsString += String.join(COMMA_STR, kubernetesMetrics.values());
    }
    return metricsString;
  }

  /**
   * This method will validate if there are any setups with the same metricName in default metrics and custom metrics
   * @param customMetrics
   * @param metrics
   * @return
   */
  public static Map<String, String> validateNameClashInCustomMetrics(
      Map<String, Set<DatadogState.Metric>> customMetrics, String metrics) {
    Map<String, String> validateFields = new HashMap<>();
    if (isEmpty(customMetrics) || isEmpty(metrics)) {
      return new HashMap<>();
    }
    List<String> metricList = Arrays.asList(metrics.split(","));
    customMetrics.forEach((hostField, metricSet) -> {
      List<DatadogState.Metric> customMetricList = new ArrayList<>();
      for (Object metricObj : metricSet) {
        DatadogState.Metric metric = JsonUtils.asObject(JsonUtils.asJson(metricObj), DatadogState.Metric.class);
        customMetricList.add(metric);
      }
      customMetricList.forEach(customMetricDefinition -> {
        if (metricList.contains(customMetricDefinition.getMetricName())) {
          validateFields.put("Duplicated metric in custom metric definition for metric: "
                  + encodeDot(customMetricDefinition.getMetricName()),
              "Present in both custom definition and out-of-the-box metrics");
        }
      });
    });
    return validateFields;
  }
}
