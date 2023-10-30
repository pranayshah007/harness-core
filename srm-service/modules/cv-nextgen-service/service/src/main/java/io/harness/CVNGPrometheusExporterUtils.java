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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class CVNGPrometheusExporterUtils {
  private static final String SERVICE_NAME_VALUE = "cv-nextgen";

  private static final String SERVICE_NAME_LABEL = "serviceName";

  public final Map<String, String> contextLabels = new HashMap<>();
  private static final String RESOURCE_LABEL = "resource";
  private static final String METHOD_LABEL = "method";
  private static final String STATUS_CODE_LABEL = "statusCode";

  public static final String MUTABLE_SERVLET_CONTEXT_HANDLER = "io.dropwizard.jetty.MutableServletContextHandler";
  private static final String METRIC_NAME_FOR_RESOURCES = "io.harness.cvng.resources";
  public static final String METRIC_PREFIX_IO_HARNESS_CVNG = "io.harness.cvng.";

  static {
    if (StringUtils.isNotEmpty(SERVICE_NAME_VALUE)) {
      contextLabels.put(SERVICE_NAME_LABEL, SERVICE_NAME_VALUE);
    }
  }

  public static void registerPrometheusExporterForResource(
      String modulePackagePath, String resourceName, MetricRegistry metricRegistry) {
    Map<String, String> metricFilterPathNaming = new HashMap<>();
    metricFilterPathNaming.put(".*.*.request.filtering", ".request.filtering");
    metricFilterPathNaming.put(".*.*.response.filtering", ".response.filtering");
    metricFilterPathNaming.put(".*.*.total", ".total");
    metricFilterPathNaming.put(".*.*.exceptions", ".exceptions");
    metricFilterPathNaming.put(".*.*", "");
    List<MapperConfig> mapperConfigList = new ArrayList<>(getMapperConfigForStatusCode(modulePackagePath));
    for (Map.Entry<String, String> entry : metricFilterPathNaming.entrySet()) {
      mapperConfigList.add(getMapperConfigForResource(modulePackagePath, entry.getKey(), entry.getValue()));
    }
    SampleBuilder sampleBuilder = new CustomMappingSampleBuilder(mapperConfigList);
    new DropwizardExports(
        metricRegistry, MetricFilter.startsWith(modulePackagePath + "." + resourceName), sampleBuilder)
        .register();
  }

  public static void registerJVMMetrics(MetricRegistry metricRegistry) {
    new DropwizardExports(metricRegistry, MetricFilter.startsWith("jvm"), new HarnessCustomSampleBuilder()).register();
  }

  public static void registerWebServerMetrics(MetricRegistry metricRegistry) {
    List<MapperConfig> mapperConfigList = new ArrayList<>();
    addWebServerMetricsMapperConfig(mapperConfigList);
    MapperConfig requestConfig = new MapperConfig();
    mapperConfigList.add(requestConfig);
    requestConfig.setMatch(MUTABLE_SERVLET_CONTEXT_HANDLER + ".requests");
    requestConfig.setName(METRIC_PREFIX_IO_HARNESS_CVNG + MUTABLE_SERVLET_CONTEXT_HANDLER + ".requests");
    Map<String, String> labels = new HashMap<>();
    addCommonLabels(labels);
    requestConfig.setLabels(labels);
    SampleBuilder sampleBuilder = new CustomMappingSampleBuilder(mapperConfigList);
    new DropwizardExports(metricRegistry, MetricFilter.startsWith(MUTABLE_SERVLET_CONTEXT_HANDLER), sampleBuilder)
        .register();
  }

  private static void addWebServerMetricsMapperConfig(List<MapperConfig> mapperConfigList) {
    addRequestsForHTTPMethods(mapperConfigList);
    List<String> codes = List.of("1xx", "2xx", "3xx", "4xx", "5xx");
    List<String> durations = List.of("1m", "5m", "15m");
    // The match field in MapperConfig is a simplified glob expression that only allows * wildcard.
    for (String code : codes) {
      MapperConfig requestConfig = new MapperConfig();
      requestConfig.setMatch(MUTABLE_SERVLET_CONTEXT_HANDLER + "." + code + "-responses");
      // The new Sample's template name.
      requestConfig.setName(METRIC_PREFIX_IO_HARNESS_CVNG + MUTABLE_SERVLET_CONTEXT_HANDLER + ".responses");
      Map<String, String> labels = new HashMap<>();
      addCommonLabels(labels);
      labels.put(STATUS_CODE_LABEL, code);
      requestConfig.setLabels(labels);
      mapperConfigList.add(requestConfig);
      for (String duration : durations) {
        addPercentageErrorForStatusCode(mapperConfigList, code, duration);
      }
    }
  }

  private static void addPercentageErrorForStatusCode(
      List<MapperConfig> mapperConfigList, String code, String duration) {
    MapperConfig requestConfigForDuration = new MapperConfig();
    requestConfigForDuration.setMatch(MUTABLE_SERVLET_CONTEXT_HANDLER + ".percent-" + code + "-" + duration);
    requestConfigForDuration.setName(METRIC_PREFIX_IO_HARNESS_CVNG + MUTABLE_SERVLET_CONTEXT_HANDLER + ".percent");
    Map<String, String> labelsForDuration = new HashMap<>();
    addCommonLabels(labelsForDuration);
    labelsForDuration.put(STATUS_CODE_LABEL, code);
    labelsForDuration.put("duration", duration);
    requestConfigForDuration.setLabels(labelsForDuration);
    mapperConfigList.add(requestConfigForDuration);
  }

  private static void addRequestsForHTTPMethods(List<MapperConfig> mapperConfigList) {
    List<String> methodList = new ArrayList<>(
        List.of("put", "get", "post", "delete", "head", "other", "options", "trace", "move", "connect"));
    for (String method : methodList) {
      MapperConfig mapperConfig = new MapperConfig();
      mapperConfig.setMatch(MUTABLE_SERVLET_CONTEXT_HANDLER + "." + method + "-requests");
      mapperConfig.setName(METRIC_PREFIX_IO_HARNESS_CVNG + MUTABLE_SERVLET_CONTEXT_HANDLER + "."
          + "requests");
      mapperConfigList.add(mapperConfig);
      Map<String, String> labels = new HashMap<>();
      addCommonLabels(labels);
      labels.put(METHOD_LABEL, method);
      mapperConfig.setLabels(labels);
    }
  }

  private static MapperConfig getMapperConfigForResource(
      String modulePackagePath, String metricFilterPath, String metricName) {
    MapperConfig requestConfig = new MapperConfig();
    // The match field in MapperConfig is a simplified glob expression that only allows * wildcard.
    requestConfig.setMatch(modulePackagePath + metricFilterPath);
    // The new Sample's template name.
    requestConfig.setName(METRIC_NAME_FOR_RESOURCES + metricName);
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
      requestConfig.setName(METRIC_NAME_FOR_RESOURCES + ".responses");
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
