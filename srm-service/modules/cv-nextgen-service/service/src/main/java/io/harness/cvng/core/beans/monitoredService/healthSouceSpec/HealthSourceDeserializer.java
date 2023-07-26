/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.HealthSource.HealthSourceKeys;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.ws.rs.BadRequestException;

public class HealthSourceDeserializer extends JsonDeserializer<HealthSource> {
  static Map<MonitoredServiceDataSourceType, Class<?>> deserializationMapper = new HashMap<>();
  static {
    deserializationMapper.put(MonitoredServiceDataSourceType.APP_DYNAMICS, AppDynamicsHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.AWS_PROMETHEUS, AwsPrometheusHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.CUSTOM_HEALTH_LOG, CustomHealthSourceLogSpec.class);
    deserializationMapper.put(
        MonitoredServiceDataSourceType.CLOUDWATCH_METRICS, CloudWatchMetricsHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.CUSTOM_HEALTH_METRIC, CustomHealthSourceMetricSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.DATADOG_LOG, DatadogLogHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.DATADOG_METRICS, DatadogMetricHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.DYNATRACE, DynatraceHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.ELASTICSEARCH, ELKHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.ERROR_TRACKING, ErrorTrackingHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.NEW_RELIC, NewRelicHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.PROMETHEUS, PrometheusHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.SPLUNK, SplunkHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.SPLUNK_METRIC, SplunkMetricHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.STACKDRIVER, StackdriverMetricHealthSourceSpec.class);
    deserializationMapper.put(MonitoredServiceDataSourceType.STACKDRIVER_LOG, StackdriverLogHealthSourceSpec.class);
  }

  @Override
  public HealthSource deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode tree = jsonParser.readValueAsTree();
    MonitoredServiceDataSourceType type =
        JsonUtils.treeToValue(tree.get(HealthSourceKeys.type), MonitoredServiceDataSourceType.class);
    String name = tree.has(HealthSourceKeys.name) ? tree.get(HealthSourceKeys.name).asText() : null;
    String identifier = tree.has(HealthSourceKeys.identifier) ? tree.get(HealthSourceKeys.identifier).asText() : null;
    HealthSource healthSource = HealthSource.builder().name(name).identifier(identifier).type(type).build();
    JsonNode spec = tree.get(HealthSourceKeys.spec);
    if (spec == null) {
      throw new BadRequestException("Spec is not serializable.");
    }
    if (tree.has(HealthSourceKeys.version)) {
      HealthSourceVersion healthSourceVersion =
          JsonUtils.treeToValue(tree.get(HealthSourceKeys.version), HealthSourceVersion.class);
      if (healthSourceVersion == HealthSourceVersion.V2) {
        NextGenHealthSourceSpec nextGenHealthSourceSpec = JsonUtils.treeToValue(spec, NextGenHealthSourceSpec.class);
        DataSourceType dataSourceType = MonitoredServiceDataSourceType.getDataSourceType(type);
        if (dataSourceType.isNextGenSpec()) {
          nextGenHealthSourceSpec.setDataSourceType(dataSourceType);
        }
        healthSource.setVersion(healthSourceVersion);
        healthSource.setSpec(nextGenHealthSourceSpec);
        return healthSource;
      }
    } else if (Objects.nonNull(type) && type == MonitoredServiceDataSourceType.ELASTICSEARCH) {
      DataSourceType dataSourceType = MonitoredServiceDataSourceType.getDataSourceType(type);
      NextGenHealthSourceSpec nextGenHealthSourceSpec = JsonUtils.treeToValue(spec, NextGenHealthSourceSpec.class);
      if (dataSourceType.isNextGenSpec()) {
        nextGenHealthSourceSpec.setDataSourceType(dataSourceType);
      }
      healthSource.setVersion(HealthSourceVersion.V2);
      healthSource.setSpec(nextGenHealthSourceSpec);
      return healthSource;
    }
    Class<?> deserializationClass = deserializationMapper.get(type);
    if (deserializationClass == null) {
      throw new BadRequestException("Spec is not serializable, it doesn't match any schema.");
    }
    HealthSourceSpec healthSourceSpec = (HealthSourceSpec) JsonUtils.treeToValue(spec, deserializationClass);
    healthSource.setSpec(healthSourceSpec);
    return healthSource;
  }
}
