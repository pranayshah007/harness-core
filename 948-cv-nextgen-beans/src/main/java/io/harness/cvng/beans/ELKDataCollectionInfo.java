/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.cvng.elk.elkUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class ELKDataCollectionInfo extends LogDataCollectionInfo<ELKConnectorDTO> {
  String query;
  String index;
  String serviceInstanceIdentifier;
  String timeStampIdentifier;
  String timeStampFormat;
  String messageIdentifier;
  public static final long LOG_MAX_LIMIT = 10;

  @Override
  public Map<String, Object> getDslEnvVariables(ELKConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("query", query);
    dslEnvVariables.put("index", index);
    dslEnvVariables.put("serviceInstanceIdentifier", serviceInstanceIdentifier);
    dslEnvVariables.put("timeStampIdentifier", timeStampIdentifier);
    dslEnvVariables.put("timeStampFormat", timeStampFormat);
    dslEnvVariables.put("messageIdentifier", messageIdentifier);
    dslEnvVariables.put("limit", LOG_MAX_LIMIT);
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(ELKConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(ELKConnectorDTO connectorConfigDTO) {
    return elkUtils.collectionHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(ELKConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
