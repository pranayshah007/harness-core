/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.services.DeeplinkURLService;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;

import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@Slf4j
public class DeeplinkURLServiceImpl implements DeeplinkURLService {
  private static final String STEP_INPUT_IN_SECONDS = "60";
  private static final String DATE_FORMAT_STRING = "yyyy-MM-dd HH:mm";
  private static final String GRAPH_PATH = "graph";
  private static final String GRAPH_TAB = "0";
  private static final String PARAM_STEP_INPUT = "g0.step_input";
  private static final String PARAM_EXPRESSION = "g0.expr";
  private static final String PARAM_RANGE_INPUT = "g0.range_input";
  private static final String PARAM_END_INPUT = "g0.end_input";
  private static final String PARAM_TAB = "g0.tab";
  @Inject private NextGenService nextGenService;

  public Optional<String> buildDeeplinkURLFromCVConfig(
      CVConfig cvConfig, String metricIdentifier, Instant startTimeInstant, Instant endTimeInstant) {
    Optional<String> deeplinkURL = Optional.empty();
    String baseUrl;
    if (cvConfig instanceof PrometheusCVConfig && !(cvConfig instanceof AwsPrometheusCVConfig)) {
      try {
        PrometheusCVConfig prometheusCVConfig = (PrometheusCVConfig) cvConfig;
        Optional<PrometheusCVConfig.MetricInfo> metricInfo =
            CollectionUtils.emptyIfNull(prometheusCVConfig.getMetricInfos())
                .stream()
                .filter(metricInfoInList -> metricIdentifier.equals(metricInfoInList.getIdentifier()))
                .findFirst();
        Optional<ConnectorInfoDTO> connectorInfoDTO = nextGenService.get(cvConfig.getAccountId(),
            cvConfig.getConnectorIdentifier(), cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier());
        if (connectorInfoDTO.isPresent() && metricInfo.isPresent()) {
          PrometheusConnectorDTO connectorConfigDTO =
              (PrometheusConnectorDTO) connectorInfoDTO.get().getConnectorConfig();
          baseUrl = connectorConfigDTO.getUrl();
          LocalDateTime endTime = endTimeInstant.atZone(ZoneOffset.UTC).toLocalDateTime();
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT_STRING);
          long diffMinutes = startTimeInstant.until(endTimeInstant, ChronoUnit.MINUTES); // this is a diff in min
          String rangeInput = Math.max(diffMinutes, 1) + "m";
          URIBuilder uriBuilder;
          uriBuilder = new URIBuilder(baseUrl + GRAPH_PATH);
          uriBuilder.addParameter(PARAM_STEP_INPUT, STEP_INPUT_IN_SECONDS);
          uriBuilder.addParameter(PARAM_EXPRESSION, metricInfo.get().getQuery());
          uriBuilder.addParameter(PARAM_RANGE_INPUT, rangeInput); // eg. 10s or 10m or 1h
          uriBuilder.addParameter(PARAM_END_INPUT, endTime.format(formatter)); // eg. YYYY-MM-DD HH:MM
          uriBuilder.addParameter(PARAM_TAB, GRAPH_TAB);
          deeplinkURL = Optional.ofNullable(uriBuilder.build().toString());
        }
      } catch (URISyntaxException ignored) {
        log.error("Cannot form deeplink for the given CVConfig", ignored);
      }
    }
    return deeplinkURL;
  }
}
