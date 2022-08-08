/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.beans.datadog.DatadogLogDefinition;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.cvng.datadog.DatadogUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class DatadogLogDataCollectionInfo extends LogDataCollectionInfo<DatadogConnectorDTO> {
  public static final long LOG_MAX_LIMIT = 500;
  DatadogLogDefinition logDefinition;

  @Override
  public Map<String, Object> getDslEnvVariables(DatadogConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("query", logDefinition.getQuery());
    dslEnvVariables.put("indexes", logDefinition.getIndexes());
    dslEnvVariables.put("limit", LOG_MAX_LIMIT);
    dslEnvVariables.put("serviceInstanceIdentifier", logDefinition.getServiceInstanceIdentifier());
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(DatadogConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(DatadogConnectorDTO connectorConfigDTO) {
    return DatadogUtils.collectionHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(DatadogConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
