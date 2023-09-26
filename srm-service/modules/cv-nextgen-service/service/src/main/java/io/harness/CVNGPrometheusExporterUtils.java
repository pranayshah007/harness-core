/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.dropwizard.samplebuilder.CustomMappingSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.MapperConfig;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class CVNGPrometheusExporterUtils {
  private static final String NAMESPACE_VALUE = System.getenv("NAMESPACE");
  private static final String CONTAINER_NAME_VALUE = System.getenv("CONTAINER_NAME");
  private static final String SERVICE_NAME_VALUE = "cv-nextgen";
  private static final String NAMESPACE_LABEL = "namespace";
  private static final String CONTAINER_NAME_LABEL = "containerName";
  private static final String SERVICE_NAME_LABEL = "serviceName";
  public final Map<String, String> contextLabels = new HashMap<>();
  private static final String RESOURCE_LABEL = "resource";
  private static final String METHOD_LABEL = "method";
  private static final String STATUS_CODE_LABEL = "statusCode";

  static {
    if (StringUtils.isNotEmpty(NAMESPACE_VALUE)) {
      contextLabels.put(NAMESPACE_LABEL, NAMESPACE_VALUE);
    }
    if (StringUtils.isNotEmpty(CONTAINER_NAME_VALUE)) {
      contextLabels.put(CONTAINER_NAME_LABEL, CONTAINER_NAME_VALUE);
    }
    if (StringUtils.isNotEmpty(SERVICE_NAME_VALUE)) {
      contextLabels.put(SERVICE_NAME_LABEL, SERVICE_NAME_VALUE);
    }
  }

  public static void registerPrometheusExporter(
      String modulePackagePath, String resourceName, MetricRegistry metricRegistry) {
    MapperConfig mapperConfig = getMapperConfig(modulePackagePath, ".*.*", "");
    MapperConfig mapperConfigForRequest =
        getMapperConfig(modulePackagePath, ".*.*.request.filtering", ".request.filtering");
    MapperConfig mapperConfigForResponse =
        getMapperConfig(modulePackagePath, ".*.*.response.filtering", ".response.filtering");
    MapperConfig mapperConfigForTotal = getMapperConfig(modulePackagePath, ".*.*.total", ".total");
    MapperConfig mapperConfigForExceptions = getMapperConfig(modulePackagePath, ".*.*.exceptions", ".exceptions");
    List<MapperConfig> mapperConfigList = new ArrayList<>(Arrays.asList(mapperConfigForRequest, mapperConfigForResponse,
        mapperConfigForTotal, mapperConfigForExceptions, mapperConfig));
    mapperConfigList.addAll(getMapperConfigForStatusCode(modulePackagePath));
    SampleBuilder sampleBuilder = new CustomMappingSampleBuilder(mapperConfigList);
    new DropwizardExports(
        metricRegistry, MetricFilter.startsWith(modulePackagePath + "." + resourceName), sampleBuilder)
        .register();
  }

  public static void registerJVMMetrics(MetricRegistry metricRegistry) {
    new DropwizardExports(metricRegistry, MetricFilter.startsWith("jvm"), new HarnessCustomSampleBuilder()).register();
  }
  private static MapperConfig getMapperConfig(String modulePackagePath, String metricFilterPath, String metricName) {
    MapperConfig requestConfig = new MapperConfig();
    // The match field in MapperConfig is a simplified glob expression that only allows * wildcard.
    requestConfig.setMatch(modulePackagePath + metricFilterPath);
    // The new Sample's template name.
    requestConfig.setName(modulePackagePath + metricName);
    Map<String, String> labels = getRESTMetricLabels();
    requestConfig.setLabels(labels);
    return requestConfig;
  }

  private static void addCommonLabels(Map<String, String> labels) {
    for (Map.Entry<String, String> entry : contextLabels.entrySet()) {
      if (StringUtils.isNotBlank(entry.getValue())) {
        labels.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private static List<MapperConfig> getMapperConfigForStatusCode(String modulePackagePath) {
    List<MapperConfig> mapperConfigList = new ArrayList<>();
    List<String> codes = List.of("1xx", "2xx", "3xx", "4xx", "5xx");
    // The match field in MapperConfig is a simplified glob expression that only allows * wildcard.
    for (String code : codes) {
      MapperConfig requestConfig = new MapperConfig();
      requestConfig.setMatch(modulePackagePath + ".*.*." + code + "-responses");
      // The new Sample's template name.
      requestConfig.setName(modulePackagePath + ".responses");
      Map<String, String> labels = getRESTMetricLabels();
      labels.put(STATUS_CODE_LABEL, code);
      requestConfig.setLabels(labels);
      mapperConfigList.add(requestConfig);
    }

    return mapperConfigList;
  }

  private static Map<String, String> getRESTMetricLabels() {
    Map<String, String> labels = new HashMap<>();
    // ... more configs
    // Labels to be extracted from the metric. Key=label name. Value=label template
    addCommonLabels(labels);
    labels.put(RESOURCE_LABEL, "${0}");
    labels.put(METHOD_LABEL, "${1}");
    return labels;
  }
}
