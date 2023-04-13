/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.healthsource;

import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AwsPrometheusHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.PrometheusHealthSourceSpec;
import io.harness.data.structure.CollectionUtils;
import io.harness.ngmigration.monitoredservice.utils.HealthSourceFieldMapper;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;
import software.wings.sm.states.PrometheusState;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class PrometheusHealthSourceGenerator extends HealthSourceGenerator {
  private static final String CG_HOSTNAME_PLACEHOLDER = "$hostName";

  @Override
  public HealthSourceSpec generateHealthSourceSpec(GraphNode graphNode) {
    Map<String, Object> properties = CollectionUtils.emptyIfNull(graphNode.getProperties());
    PrometheusState state = new PrometheusState(graphNode.getName());
    state.parseProperties(properties);
    if (state.isAwsPrometheus()) {
      return getAwsSpec(state);
    } else {
      return getSpec(state);
    }
  }

  @Override
  public MonitoredServiceDataSourceType getDataSourceType(GraphNode graphNode) {
    Map<String, Object> properties = CollectionUtils.emptyIfNull(graphNode.getProperties());
    PrometheusState state = new PrometheusState(graphNode.getName());
    state.parseProperties(properties);
    if (state.isAwsPrometheus()) {
      return MonitoredServiceDataSourceType.AWS_PROMETHEUS;
    } else {
      return MonitoredServiceDataSourceType.PROMETHEUS;
    }
  }

  @Override
  public HealthSource generateHealthSource(GraphNode graphNode) {
    Map<String, Object> properties = CollectionUtils.emptyIfNull(graphNode.getProperties());
    PrometheusState state = new PrometheusState(graphNode.getName());
    state.parseProperties(properties);

    if (state.isAwsPrometheus()) {
      return HealthSource.builder()
          .name(graphNode.getName())
          .identifier(MigratorUtility.generateIdentifier(graphNode.getName(), CaseFormat.LOWER_CASE))
          .type(MonitoredServiceDataSourceType.AWS_PROMETHEUS)
          .spec(getSpec(state))
          .build();
    } else {
      return HealthSource.builder()
          .name(graphNode.getName())
          .identifier(MigratorUtility.generateIdentifier(graphNode.getName(), CaseFormat.LOWER_CASE))
          .type(MonitoredServiceDataSourceType.PROMETHEUS)
          .spec(getSpec(state))
          .build();
    }
  }
  public AwsPrometheusHealthSourceSpec getAwsSpec(PrometheusState state) {
    List<PrometheusMetricDefinition> prometheusMetricDefinitions = getPrometheusMetricDefenitions(state);
    Pattern lastPathElementInURLPattern = Pattern.compile("/([^/]+)/?$");
    String awsWorkSpaceId = lastPathElementInURLPattern.matcher(state.getAwsPrometheusUrl()).group();
    return AwsPrometheusHealthSourceSpec.builder()
        .connectorRef(MigratorUtility.RUNTIME_INPUT.getValue())
        .metricDefinitions(prometheusMetricDefinitions)
        .region(state.getAwsPrometheusRegion())
        .workspaceId(awsWorkSpaceId)
        .build();
  }

  public PrometheusHealthSourceSpec getSpec(PrometheusState state) {
    List<PrometheusMetricDefinition> prometheusMetricDefinitions = getPrometheusMetricDefenitions(state);

    return PrometheusHealthSourceSpec.builder()
        .connectorRef(MigratorUtility.RUNTIME_INPUT.getValue())
        .metricDefinitions(prometheusMetricDefinitions)
        .build();
  }

  @NotNull
  private List<PrometheusMetricDefinition> getPrometheusMetricDefenitions(PrometheusState state) {
    return state.getTimeSeriesToAnalyze()
        .stream()
        .map(timeSeries
            -> PrometheusMetricDefinition.builder()
                   .query(getNGQuery(timeSeries.getUrl()))
                   .identifier(MigratorUtility.generateIdentifier(timeSeries.getMetricName(), CaseFormat.LOWER_CASE))
                   .metricName(timeSeries.getMetricName())
                   .serviceInstanceFieldName(getNGServiceInstanceIdentifier(timeSeries.getUrl()))
                   .groupName(timeSeries.getTxnName())
                   .isManualQuery(true)
                   .analysis(HealthSourceFieldMapper.getAnalysisDTO(
                       timeSeries.getMetricType(), getNGServiceInstanceIdentifier(timeSeries.getUrl())))
                   .build())
        .collect(Collectors.toList());
  }

  public String getNGQuery(String cgQuery) {
    // remove all empty characters
    String noSpaceQuery = cgQuery.replaceAll("[\n\t ]", "");
    if (!noSpaceQuery.contains(CG_HOSTNAME_PLACEHOLDER)) {
      return noSpaceQuery;
    }
    int startIndex = noSpaceQuery.indexOf(CG_HOSTNAME_PLACEHOLDER);
    int endIndex = startIndex + CG_HOSTNAME_PLACEHOLDER.length();
    if (noSpaceQuery.charAt(endIndex) == '"') {
      endIndex++;
    }
    for (; startIndex > 0; startIndex--) {
      if (noSpaceQuery.charAt(startIndex) == ',') {
        break;
      }
      if (noSpaceQuery.charAt(startIndex) == '{') {
        startIndex++;
        break;
      }
    }

    if (startIndex <= 0) {
      return noSpaceQuery;
    }
    return StringUtils.substring(noSpaceQuery, 0, startIndex) + StringUtils.substring(noSpaceQuery, endIndex);
  }

  public String getNGServiceInstanceIdentifier(String cgQuery) {
    // remove all empty characters
    String noSpaceQuery = cgQuery.replaceAll("[\n\t ]", "");
    if (!noSpaceQuery.contains(CG_HOSTNAME_PLACEHOLDER)) {
      return "FIX_ME";
    }
    int endIndex = noSpaceQuery.indexOf(CG_HOSTNAME_PLACEHOLDER);
    endIndex = endIndex - 2;
    if (noSpaceQuery.charAt(endIndex) != '=') {
      return "FIX_ME";
    }
    int startIndex = endIndex - 1;
    for (; startIndex > 0; startIndex--) {
      if (noSpaceQuery.charAt(startIndex) == ',') {
        break;
      }
      if (noSpaceQuery.charAt(startIndex) == '{') {
        break;
      }
    }
    if (startIndex <= 0) {
      return "FIX_ME";
    }
    return StringUtils.substring(noSpaceQuery, startIndex + 1, endIndex);
  }
}
