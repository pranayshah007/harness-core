/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.healthsource;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.DatadogMetricHealthDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.DatadogMetricHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.data.structure.CollectionUtils;
import io.harness.ngmigration.monitoredservice.utils.HealthSourceFieldMapper;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;
import software.wings.sm.states.DatadogState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataDogMetricHealthSourceGenerator extends HealthSourceGenerator {
  @Override
  public HealthSourceSpec generateHealthSourceSpec(GraphNode graphNode) {
    Map<String, Object> properties = CollectionUtils.emptyIfNull(graphNode.getProperties());
    DatadogState state = new DatadogState(graphNode.getName());
    state.parseProperties(properties);
    return getSpec(state, graphNode);
  }

  @Override
  public MonitoredServiceDataSourceType getDataSourceType(GraphNode graphNode) {
    return MonitoredServiceDataSourceType.DATADOG_METRICS;
  }

  public DatadogMetricHealthSourceSpec getSpec(DatadogState state, GraphNode graphNode) {
    return DatadogMetricHealthSourceSpec.builder()
        .connectorRef(MigratorUtility.RUNTIME_INPUT.getValue())
        .feature("Datadog Cloud Metrics")
        .metricDefinitions(getMetricDefinitions(state, graphNode))
        .build();
  }

  private List<DatadogMetricHealthDefinition> getMetricDefinitions(DatadogState state, GraphNode graphNode) {
    List<DatadogMetricHealthDefinition> result = new ArrayList<>();

    if (isNotEmpty(graphNode.getProperties()) && null != (graphNode.getProperties().get("customMetrics"))) {
      result.addAll(((Map<String, List<Map<String, String>>>) (graphNode.getProperties().get("customMetrics")))
                        .entrySet()
                        .stream()
                        .flatMap(entry
                            -> entry.getValue().stream().map(cgMetric
                                -> DatadogMetricHealthDefinition.builder()
                                       .isManualQuery(true)
                                       .isCustomCreatedMetric(true)
                                       .identifier(MigratorUtility.generateIdentifier(
                                           cgMetric.get("displayName"), CaseFormat.LOWER_CASE))
                                       .metricName(cgMetric.get("metricName"))
                                       .query("avg:" + cgMetric.get("metricName") + "{*}.rollup(avg, 60)")
                                       .groupingQuery("avg:" + cgMetric.get("metricName") + "{*} by {" + entry.getKey()
                                           + "} .rollup(avg, 60)")
                                       .dashboardName(cgMetric.get("txnName"))
                                       .analysis(HealthSourceFieldMapper.getAnalysisDTO(
                                           cgMetric.get("mlMetricType"), entry.getKey()))
                                       .build()))
                        .collect(Collectors.toList()));
    }

    if (isNotEmpty(state.getMetrics())) {
      result.addAll(Arrays.asList(state.getMetrics().split(","))
                        .stream()
                        .map(metricName
                            -> DatadogMetricHealthDefinition.builder()
                                   .isManualQuery(true)
                                   .isCustomCreatedMetric(true)
                                   .metric(metricName)
                                   .identifier(MigratorUtility.generateIdentifier(metricName, CaseFormat.LOWER_CASE))
                                   .metricName(metricName)
                                   .query("avg:" + metricName + "{*}.rollup(avg, 60)")
                                   .groupingQuery("avg:" + metricName + "{*} by {pod_name}.rollup(avg, 60)")
                                   .dashboardName("Infrastructure")
                                   .analysis(HealthSourceFieldMapper.getAnalysisDTO("INFRA", "pod_name"))
                                   .build())
                        .collect(Collectors.toList()));
    }

    return result;
  }
}
