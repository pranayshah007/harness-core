/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import io.harness.serializer.YamlUtils;

import software.wings.metrics.TimeSeriesMetricDefinition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;

public class InstanaUtils {
  private static final String INSTANA_METRICS_YAML_PATH = "/apm/instana_metrics.yml";
  private static final Map<String, InstanaMetricTemplate> INSTANA_METRIC_TEMPLATE_MAP_INFRA;
  private static final Map<String, InstanaMetricTemplate> INSTANA_METRIC_TEMPLATE_MAP_APPLICATION;
  private InstanaUtils() {}
  static {
    INSTANA_METRIC_TEMPLATE_MAP_INFRA = getTemplateMap("infraMetrics");
    INSTANA_METRIC_TEMPLATE_MAP_APPLICATION = getTemplateMap("applicationMetrics");
  }

  private static Map<String, InstanaMetricTemplate> getTemplateMap(String type) {
    Map<String, InstanaMetricTemplate> metricTemplateMap = new HashMap<>();
    Map<String, List<InstanaMetricTemplate>> metricTypeToTempleteMap = null;
    try {
      metricTypeToTempleteMap = getMetricTypeToTempleteMap();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    metricTypeToTempleteMap.get(type).forEach(
        instanaMetricTemplate -> metricTemplateMap.put(instanaMetricTemplate.getMetricName(), instanaMetricTemplate));

    return Collections.unmodifiableMap(metricTemplateMap);
  }

  private static Map<String, List<InstanaMetricTemplate>> getMetricTypeToTempleteMap() throws IOException {
    String yaml = IOUtils.toString(
        InstanaUtils.class.getResourceAsStream(INSTANA_METRICS_YAML_PATH), StandardCharsets.UTF_8.name());
    return new YamlUtils().read(yaml, new TypeReference<Map<String, List<InstanaMetricTemplate>>>() {});
  }

  public static Map<String, InstanaMetricTemplate> getInfraMetricTemplateMap() {
    return INSTANA_METRIC_TEMPLATE_MAP_INFRA;
  }

  public static Map<String, InstanaMetricTemplate> getApplicationMetricTemplateMap() {
    return INSTANA_METRIC_TEMPLATE_MAP_APPLICATION;
  }

  public static Map<String, TimeSeriesMetricDefinition> createMetricTemplates(
      @Nullable InstanaInfraParams infraParams) {
    Map<String, TimeSeriesMetricDefinition> metricDefinitionMap = new HashMap<>();
    Map<String, InstanaMetricTemplate> infraMetricTemplateMap = getInfraMetricTemplateMap();
    if (infraParams != null) {
      infraParams.getMetrics().forEach(metric -> {
        InstanaMetricTemplate instanaMetricTemplate = infraMetricTemplateMap.get(metric);
        Preconditions.checkNotNull(instanaMetricTemplate, "instanaMetricTemplate can not be null");
        metricDefinitionMap.put(instanaMetricTemplate.getDisplayName(),
            TimeSeriesMetricDefinition.builder()
                .metricName(instanaMetricTemplate.getDisplayName())
                .metricType(instanaMetricTemplate.getMetricType())
                .build());
      });
    }

    metricDefinitionMap.putAll(createApplicationMetricsTemplate());
    return metricDefinitionMap;
  }

  public static Map<String, TimeSeriesMetricDefinition> createApplicationMetricsTemplate() {
    Map<String, TimeSeriesMetricDefinition> metricDefinitionMap = new HashMap<>();
    getApplicationMetricTemplateMap().forEach((metricName, instanaMetricTemplate)
                                                  -> metricDefinitionMap.put(instanaMetricTemplate.getDisplayName(),
                                                      TimeSeriesMetricDefinition.builder()
                                                          .metricName(instanaMetricTemplate.getDisplayName())
                                                          .metricType(instanaMetricTemplate.getMetricType())
                                                          .build()));
    return metricDefinitionMap;
  }
}
