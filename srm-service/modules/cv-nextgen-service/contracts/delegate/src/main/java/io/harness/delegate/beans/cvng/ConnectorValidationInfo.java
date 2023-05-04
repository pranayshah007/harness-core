/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.connector.errortracking.ErrorTrackingConnectorDTO;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.delegate.beans.connector.pagerduty.PagerDutyConnectorDTO;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.delegate.beans.cvng.appd.AppDynamicsConnectorValidationInfo;
import io.harness.delegate.beans.cvng.customhealth.CustomHealthConnectorValidationInfo;
import io.harness.delegate.beans.cvng.datadog.DatadogConnectorValidationInfo;
import io.harness.delegate.beans.cvng.dynatrace.DynatraceConnectorValidationInfo;
import io.harness.delegate.beans.cvng.elk.ELKConnectorValidationInfo;
import io.harness.delegate.beans.cvng.errortracking.ErrorTrackingConnectorValidationInfo;
import io.harness.delegate.beans.cvng.newrelic.NewRelicConnectorValidationInfo;
import io.harness.delegate.beans.cvng.pagerduty.PagerDutyConnectorValidationInfo;
import io.harness.delegate.beans.cvng.prometheus.PrometheusConnectorValidationInfo;
import io.harness.delegate.beans.cvng.signalfx.SignalFXConnectorValidationInfo;
import io.harness.delegate.beans.cvng.splunk.SplunkConnectorValidationInfo;
import io.harness.delegate.beans.cvng.sumologic.SumoLogicConnectorValidationInfo;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import lombok.Data;

@Data
@OwnedBy(CV)
public abstract class ConnectorValidationInfo<T extends ConnectorConfigDTO> {
  protected T connectorConfigDTO;
  public static ConnectorValidationInfo getConnectorValidationInfo(ConnectorConfigDTO connectorConfigDTO) {
    ConnectorValidationInfo connectorValidationInfo;
    if (connectorConfigDTO instanceof AppDynamicsConnectorDTO) {
      connectorValidationInfo = AppDynamicsConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof SplunkConnectorDTO) {
      connectorValidationInfo = SplunkConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof NewRelicConnectorDTO) {
      connectorValidationInfo = NewRelicConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof PrometheusConnectorDTO) {
      connectorValidationInfo = PrometheusConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof DatadogConnectorDTO) {
      connectorValidationInfo = DatadogConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof SumoLogicConnectorDTO) {
      connectorValidationInfo = SumoLogicConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof DynatraceConnectorDTO) {
      connectorValidationInfo = DynatraceConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof PagerDutyConnectorDTO) {
      connectorValidationInfo = PagerDutyConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof CustomHealthConnectorDTO) {
      connectorValidationInfo = CustomHealthConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof ErrorTrackingConnectorDTO) {
      connectorValidationInfo = ErrorTrackingConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof ELKConnectorDTO) {
      connectorValidationInfo = ELKConnectorValidationInfo.builder().build();
    } else if (connectorConfigDTO instanceof SignalFXConnectorDTO) {
      connectorValidationInfo = SignalFXConnectorValidationInfo.builder().build();
    } else {
      throw new IllegalStateException(
          "Class: " + connectorConfigDTO.getClass().getSimpleName() + " does not have ValidationInfo object");
    }
    connectorValidationInfo.setConnectorConfigDTO(connectorConfigDTO);
    return connectorValidationInfo;
  }

  public abstract String getConnectionValidationDSL();

  public abstract String getBaseUrl();

  public abstract Map<String, String> collectionHeaders();

  public Map<String, String> collectionParams() {
    return Collections.emptyMap();
  }

  public Map<String, Object> getDslEnvVariables() {
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
}
