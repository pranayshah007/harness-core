/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.splunk;

import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.cvng.ConnectorValidationInfo;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class SplunkConnectorValidationInfo extends ConnectorValidationInfo<SplunkConnectorDTO> {
  private static final String DSL = readDSL("splunk-validation.datacollection", SplunkConnectorValidationInfo.class);
  @Override
  public String getConnectionValidationDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return connectorConfigDTO.getSplunkUrl();
  }

  @Override
  public Map<String, String> collectionHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", SplunkUtils.getAuthorizationHeader(connectorConfigDTO));
    return headers;
  }
}
