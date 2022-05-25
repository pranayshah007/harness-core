/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.customhealth;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.delegate.beans.cvng.customhealth.CustomHealthConnectorValidationInfoUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("CUSTOM_HEALTH_SAMPLE_DATA")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class CustomHealthFetchSampleDataRequest extends DataCollectionRequest<CustomHealthConnectorDTO> {
  public static final String DSL = CustomHealthFetchSampleDataRequest.readDSL(
      "customhealth-sample-data.datacollection", CustomHealthFetchSampleDataRequest.class);

  String urlPath;
  TimestampInfo startTime;
  TimestampInfo endTime;
  CustomHealthMethod method;
  String body;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    CustomHealthConnectorDTO connectorDTO = (CustomHealthConnectorDTO) getConnectorInfoDTO().getConnectorConfig();
    return connectorDTO.getBaseURL();
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    return CustomHealthFetchSampleDataRequestUtils.getDSLEnvironmentVariables(
        urlPath, method, body, startTime, endTime);
  }

  @Override
  public Map<String, String> collectionHeaders() {
    CustomHealthConnectorDTO connectorDTO = (CustomHealthConnectorDTO) getConnectorInfoDTO().getConnectorConfig();
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorDTO.getHeaders());
  }

  @Override
  public Map<String, String> collectionParams() {
    CustomHealthConnectorDTO connectorDTO = (CustomHealthConnectorDTO) getConnectorInfoDTO().getConnectorConfig();
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorDTO.getParams());
  }
}
