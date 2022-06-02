/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.customhealth;

import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.cvng.ConnectorValidationInfo;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class CustomHealthConnectorValidationInfo extends ConnectorValidationInfo<CustomHealthConnectorDTO> {
  private static final String DSL =
      readDSL("customhealth-validation.datacollection", CustomHealthConnectorValidationInfo.class);
  @Override
  public String getConnectionValidationDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return connectorConfigDTO.getBaseURL();
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getHeaders());
  }

  @Override
  public Map<String, String> collectionParams() {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getParams());
  }

  @Override
  public Map<String, Object> getDslEnvVariables() {
    return CustomHealthConnectorValidationInfoUtils.getCommonEnvVariables(connectorConfigDTO);
  }
}
