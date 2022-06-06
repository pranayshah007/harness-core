/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.prometheus;

import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.cvng.ConnectorValidationInfo;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PrometheusConnectorValidationInfo extends ConnectorValidationInfo<PrometheusConnectorDTO> {
  private static final String DSL =
      readDSL("prometheus-validation.datacollection", PrometheusConnectorValidationInfo.class);
  @Override
  public String getConnectionValidationDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return Collections.emptyMap();
  }
}
