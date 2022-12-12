/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.delegate.beans.cvng.sumologic.SumoLogicUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class SumologicLogDataCollectionInfo extends LogDataCollectionInfo<SumoLogicConnectorDTO> {
  public static final long LOG_MAX_LIMIT = 100;
  String query;
  String serviceInstanceIdentifier;
  @Override
  public Map<String, Object> getDslEnvVariables(SumoLogicConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("query", query);
    dslEnvVariables.put("serviceInstanceIdentifier", serviceInstanceIdentifier);
    dslEnvVariables.put("limit", LOG_MAX_LIMIT);
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(SumoLogicConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(SumoLogicConnectorDTO connectorConfigDTO) {
    return SumoLogicUtils.collectionHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(SumoLogicConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
