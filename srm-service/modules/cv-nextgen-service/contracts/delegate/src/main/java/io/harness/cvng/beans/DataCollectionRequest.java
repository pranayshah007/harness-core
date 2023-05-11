/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthCapabilityHelper;
import io.harness.delegate.beans.connector.datadogconnector.DatadogCapabilityHelper;
import io.harness.delegate.beans.connector.dynatraceconnector.DynatraceCapabilityHelper;
import io.harness.delegate.beans.connector.elkconnector.ELKCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.newrelicconnector.NewRelicCapabilityHelper;
import io.harness.delegate.beans.connector.pagerduty.PagerDutyCapabilityHelper;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusCapabilityHelper;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXCapabilityHelper;
import io.harness.delegate.beans.connector.splunkconnector.SplunkCapabilityHelper;
import io.harness.delegate.beans.connector.sumologicconnector.SumoLogicCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@OwnedBy(CV)
public abstract class DataCollectionRequest<T extends ConnectorConfigDTO> implements ExecutionCapabilityDemander {
  private ConnectorInfoDTO connectorInfoDTO;
  @JsonIgnore
  public T getConnectorConfigDTO() {
    return (T) connectorInfoDTO.getConnectorConfig();
  }
  private String tracingId;
  private DataCollectionRequestType type;
  public abstract String getDSL();
  public abstract String getBaseUrl();
  public abstract Map<String, String> collectionHeaders();
  public Map<String, String> collectionParams() {
    return Collections.emptyMap();
  }

  public Map<String, Object> fetchDslEnvVariables() {
    return Collections.emptyMap();
  }

  public Instant getEndTime(Instant currentTime) {
    return currentTime;
  }
  public Instant getStartTime(Instant currentTime) {
    return currentTime.minus(Duration.ofMinutes(1));
  }
  protected static String readDSL(String fileName, Class clazz) {
    try {
      return Resources.toString(clazz.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    // TODO: this is a stop gap fix, we will be refactoring it once DX team works on their proposal
    switch (connectorInfoDTO.getConnectorType()) {
      case KUBERNETES_CLUSTER:
        return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
            connectorInfoDTO.getConnectorConfig(), maskingEvaluator);
      case APP_DYNAMICS:
        return AppDynamicsCapabilityHelper.fetchRequiredExecutionCapabilities(
            connectorInfoDTO.getConnectorConfig(), maskingEvaluator);
      case SPLUNK:
        return SplunkCapabilityHelper.fetchRequiredExecutionCapabilities(
            connectorInfoDTO.getConnectorConfig(), maskingEvaluator);
      case GCP:
        return GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
            connectorInfoDTO.getConnectorConfig(), maskingEvaluator);
      case NEW_RELIC:
        return NewRelicCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      case PROMETHEUS:
        return PrometheusCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      case PAGER_DUTY:
        return PagerDutyCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      case DATADOG:
        return DatadogCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      case DYNATRACE:
        return DynatraceCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      case CUSTOM_HEALTH:
        return CustomHealthCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      case ELASTICSEARCH:
        return ELKCapabilityHelper.fetchRequiredExecutionCapabilities(
            connectorInfoDTO.getConnectorConfig(), maskingEvaluator);
      case AWS:
        return AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
            connectorInfoDTO.getConnectorConfig(), maskingEvaluator);
      case SUMOLOGIC:
        return SumoLogicCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      case SIGNALFX:
        return SignalFXCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, connectorInfoDTO.getConnectorConfig());
      default:
        throw new InvalidRequestException("Connector capability not found");
    }
  }
}
