/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.cvng.utils.StackdriverUtils.Scope.LOG_SCOPE;

import io.harness.cvng.beans.stackdriver.StackdriverLogDefinition;
import io.harness.cvng.utils.StackdriverUtils;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class StackdriverLogDataCollectionInfo extends LogDataCollectionInfo<GcpConnectorDTO> {
  private static final String timestampFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  private static final String timestampFormatKey = "timestampFormat";
  private static final String BASE_URL = "https://logging.googleapis.com/v2/";

  StackdriverLogDefinition logDefinition;

  @Override
  public Map<String, Object> getDslEnvVariables(GcpConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = StackdriverUtils.getCommonEnvVariables(connectorConfigDTO, LOG_SCOPE);

    dslEnvVariables.put("query", logDefinition.getQuery());
    dslEnvVariables.put("messageField", logDefinition.getMessageIdentifier());
    dslEnvVariables.put("hostField", logDefinition.getServiceInstanceIdentifier());
    dslEnvVariables.put(timestampFormatKey, timestampFormat);

    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(GcpConnectorDTO connectorConfigDTO) {
    return BASE_URL;
  }

  @Override
  public Map<String, String> collectionHeaders(GcpConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, String> collectionParams(GcpConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
