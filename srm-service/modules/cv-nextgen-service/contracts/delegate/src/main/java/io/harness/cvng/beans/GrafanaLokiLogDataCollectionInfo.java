/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.cvng.customhealth.CustomHealthConnectorValidationInfoUtils;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class GrafanaLokiLogDataCollectionInfo extends LogDataCollectionInfo<CustomHealthConnectorDTO> {
  private static final long LOG_MAX_LIMIT = 5000;
  private static final String START_TIME_PLACEHOLDER = "START_TIME_PLACEHOLDER";
  private static final String END_TIME_PLACEHOLDER = "END_TIME_PLACEHOLDER";
  String urlEncodedQuery;
  String serviceInstanceIdentifier;

  @Override
  public String getBaseUrl(CustomHealthConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getBaseURL();
  }

  @Override
  public Map<String, String> collectionHeaders(CustomHealthConnectorDTO connectorConfigDTO) {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getHeaders());
  }

  @Override
  public Map<String, String> collectionParams(CustomHealthConnectorDTO connectorConfigDTO) {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getParams());
  }

  @Override
  public Map<String, Object> getDslEnvVariables(CustomHealthConnectorDTO connectorConfigDTO) {
    String completeUrl = String.format("%s%s%s%s%d%s%s%s%s", getBaseUrl(connectorConfigDTO),
        "loki/api/v1/query_range?query=", urlEncodedQuery, "&limit=", LOG_MAX_LIMIT,
        "&direction=forward&start=", START_TIME_PLACEHOLDER, "&end=", END_TIME_PLACEHOLDER);
    Map<String, Object> map = new HashMap<>();
    map.put("requestUrl", completeUrl);
    map.put("serviceInstanceIdentifierPath", String.format("%s%s", "$.stream.", serviceInstanceIdentifier));
    map.put("logsListPath", "$.values");
    map.put("logMessagePath", "$.[1]");
    map.put("timestampPath", "$.[0]");
    map.put("startTimePlaceholder", START_TIME_PLACEHOLDER);
    map.put("endTimePlaceholder", END_TIME_PLACEHOLDER);
    return map;
  }
}
