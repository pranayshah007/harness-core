/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.cvng.elk.ElkUtils;

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
  public static final long LOG_MAX_LIMIT = 10000;

  @Override
  public Map<String, Object> getDslEnvVariables(ELKConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("query", query);
    dslEnvVariables.put("index", index);
    dslEnvVariables.put("serviceInstanceIdentifier", serviceInstanceIdentifier);
    dslEnvVariables.put("timeStampIdentifier", timeStampIdentifier);
    dslEnvVariables.put("timeStampFormat", timeStampFormat);
    dslEnvVariables.put("messageIdentifier", messageIdentifier);
    dslEnvVariables.put("timeStampField", getTimeStampField(timeStampIdentifier));
    dslEnvVariables.put("limit", LOG_MAX_LIMIT);
    return dslEnvVariables;
  }

  private String getTimeStampField(String timeStampIdentifier) {
    String[] parts = timeStampIdentifier.split("[.]");
    String timeStampField = parts[parts.length - 1];
    if (timeStampField.startsWith("['")) {
      return timeStampField.substring(2, timeStampField.length() - 2);
    }
    return timeStampField;
  }

  @Override
  public String getBaseUrl(ELKConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(ELKConnectorDTO connectorConfigDTO) {
    return ElkUtils.collectionHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(ELKConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
