/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.models.VerificationType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DataCollectionInfo<T extends ConnectorConfigDTO> {
  private String dataCollectionDsl;
  private boolean collectHostData;
  private List<String> serviceInstances;
  private List<String> validServiceInstanceRegExPatterns;

  public abstract VerificationType getVerificationType();
  public abstract Map<String, Object> getDslEnvVariables(T connectorConfigDTO);
  public abstract String getBaseUrl(T connectorConfigDTO);
  public abstract Map<String, String> collectionHeaders(T connectorConfigDTO);
  public abstract Map<String, String> collectionParams(T connectorConfigDTO);
}