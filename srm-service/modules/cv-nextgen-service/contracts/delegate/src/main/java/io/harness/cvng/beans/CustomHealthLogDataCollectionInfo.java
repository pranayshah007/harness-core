/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.beans.customhealthlog.CustomHealthLogInfo;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.cvng.customhealth.CustomHealthConnectorValidationInfoUtils;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomHealthLogDataCollectionInfo extends LogDataCollectionInfo<CustomHealthConnectorDTO> {
  CustomHealthLogInfo customHealthLogInfo;

  @Override
  public Map<String, Object> getDslEnvVariables(CustomHealthConnectorDTO connectorConfigDTO) {
    Map<String, Object> envVars = new HashMap<>();
    envVars.put("queryName", customHealthLogInfo.getQueryName());
    envVars.put("method", customHealthLogInfo.getMethod().toString());
    envVars.put("urlPath", customHealthLogInfo.getUrlPath());
    envVars.put("startTimePlaceholder", customHealthLogInfo.getStartTimeInfo().getPlaceholder());
    envVars.put("startTimeFormat", customHealthLogInfo.getStartTimeInfo().getTimestampFormat().toString());
    envVars.put("endTimePlaceholder", customHealthLogInfo.getEndTimeInfo().getPlaceholder());
    envVars.put("endTimeFormat", customHealthLogInfo.getEndTimeInfo().getTimestampFormat().toString());
    envVars.put("timestampValueJSONPath", customHealthLogInfo.getTimestampJsonPath());
    envVars.put("logMessageJSONPath", customHealthLogInfo.getLogMessageJsonPath());
    envVars.put("serviceInstanceJSONPath", customHealthLogInfo.getServiceInstanceJsonPath());
    String body = customHealthLogInfo.getBody();
    envVars.put("body", isEmpty(body) ? "" : body);
    return envVars;
  }

  @Override
  public String getBaseUrl(CustomHealthConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getBaseURL();
  }

  @Override
  public Map<String, String> collectionHeaders(CustomHealthConnectorDTO connectorConfigDTO) {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getHeaders());
  }

  @Override
  public Map<String, String> collectionParams(CustomHealthConnectorDTO connectorConfigDTO) {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getParams());
  }
}