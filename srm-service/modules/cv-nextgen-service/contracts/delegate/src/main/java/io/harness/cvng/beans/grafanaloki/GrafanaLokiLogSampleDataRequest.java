/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.grafanaloki;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.cvng.customhealth.CustomHealthConnectorValidationInfoUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(CV)
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("GRAFANA_LOKI_LOG_SAMPLE_DATA")
public class GrafanaLokiLogSampleDataRequest extends DataCollectionRequest<CustomHealthConnectorDTO> {
  String dsl;
  String query;
  Long startTimeInSeconds;
  Long endTimeInSeconds;

  public String getDSL() {
    return dsl;
  }

  public String getBaseUrl() {
    return getConnectorConfigDTO().getBaseURL();
  }

  public Map<String, String> collectionHeaders() {
    CustomHealthConnectorDTO connectorDTO = (CustomHealthConnectorDTO) getConnectorInfoDTO().getConnectorConfig();
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorDTO.getHeaders());
  }

  @Override
  public Map<String, String> collectionParams() {
    CustomHealthConnectorDTO connectorDTO = (CustomHealthConnectorDTO) getConnectorInfoDTO().getConnectorConfig();
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorDTO.getParams());
  }
  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    String completeUrl = String.format("%s%s%s%s%d%s%d", getBaseUrl(), "loki/api/v1/query_range?query=", query,
        "&limit=5000&direction=forward&start", startTimeInSeconds, "&end", endTimeInSeconds);
    Map<String, Object> map = new HashMap<>();
    map.put("requestUrl", completeUrl);
    return map;
  }
}
