/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.cvconnector;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsCapabilityHelper;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthCapabilityHelper;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.connector.datadogconnector.DatadogCapabilityHelper;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.delegate.beans.connector.dynatraceconnector.DynatraceCapabilityHelper;
import io.harness.delegate.beans.connector.elkconnector.ELKCapabilityHelper;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.connector.errortracking.ErrorTrackingConnectorDTO;
import io.harness.delegate.beans.connector.errortrackingconnector.ErrorTrackingCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.delegate.beans.connector.newrelicconnector.NewRelicCapabilityHelper;
import io.harness.delegate.beans.connector.pagerduty.PagerDutyCapabilityHelper;
import io.harness.delegate.beans.connector.pagerduty.PagerDutyConnectorDTO;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusCapabilityHelper;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXCapabilityHelper;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkCapabilityHelper;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.delegate.beans.connector.sumologicconnector.SumoLogicCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;

import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(CV)
public class CVConnectorCapabilitiesHelper extends ConnectorTaskParams {
  protected CVConnectorCapabilitiesHelper(ConnectorTaskParamsBuilder<?, ?> b) {
    super(b);
  }

  public static List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorDTO, ExpressionEvaluator maskingEvaluator) {
    if (connectorDTO instanceof KubernetesClusterConfigDTO) {
      return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(connectorDTO, maskingEvaluator);
    } else if (connectorDTO instanceof AppDynamicsConnectorDTO) {
      return AppDynamicsCapabilityHelper.fetchRequiredExecutionCapabilities(connectorDTO, maskingEvaluator);
    } else if (connectorDTO instanceof SplunkConnectorDTO) {
      return SplunkCapabilityHelper.fetchRequiredExecutionCapabilities(connectorDTO, maskingEvaluator);
    } else if (connectorDTO instanceof GcpConnectorDTO) {
      return GcpCapabilityHelper.fetchRequiredExecutionCapabilities(connectorDTO, maskingEvaluator);
    } else if (connectorDTO instanceof NewRelicConnectorDTO) {
      return NewRelicCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof PrometheusConnectorDTO) {
      return PrometheusCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof DatadogConnectorDTO) {
      return DatadogCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof SumoLogicConnectorDTO) {
      return SumoLogicCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof DynatraceConnectorDTO) {
      return DynatraceCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof PagerDutyConnectorDTO) {
      return PagerDutyCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof CustomHealthConnectorDTO) {
      return CustomHealthCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof ErrorTrackingConnectorDTO) {
      return ErrorTrackingCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof ELKConnectorDTO) {
      return ELKCapabilityHelper.fetchRequiredExecutionCapabilities(connectorDTO, maskingEvaluator);
    } else if (connectorDTO instanceof AwsConnectorDTO) {
      return AwsCapabilityHelper.fetchRequiredExecutionCapabilities(connectorDTO, maskingEvaluator);
    } else if (connectorDTO instanceof SignalFXConnectorDTO) {
      return SignalFXCapabilityHelper.fetchRequiredExecutionCapabilities(maskingEvaluator, connectorDTO);
    } else if (connectorDTO instanceof AzureConnectorDTO) {
      return AzureCapabilityHelper.fetchRequiredExecutionCapabilities(connectorDTO, maskingEvaluator);
    } else {
      throw new InvalidRequestException("Connector capability not found for " + connectorDTO);
    }
  }
}
