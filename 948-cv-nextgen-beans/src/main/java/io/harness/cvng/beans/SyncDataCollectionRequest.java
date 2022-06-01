/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@JsonTypeName("SYNC_DATA_COLLECTION")
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CV)
public class SyncDataCollectionRequest extends DataCollectionRequest<ConnectorConfigDTO> {
  DataCollectionInfo dataCollectionInfo;
  Instant startTime;
  Instant endTime;

  public SyncDataCollectionRequest() {
    setType(DataCollectionRequestType.SYNC_DATA_COLLECTION);
  }

  @Override
  public String getDSL() {
    return dataCollectionInfo.getDataCollectionDsl();
  }

  @Override
  public String getBaseUrl() {
    return dataCollectionInfo.getBaseUrl(getConnectorConfigDTO());
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return dataCollectionInfo.collectionHeaders(getConnectorConfigDTO());
  }

  public Map<String, String> collectionParams() {
    return dataCollectionInfo.collectionParams(getConnectorConfigDTO());
  }

  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> dslVariables = dataCollectionInfo.getDslEnvVariables(getConnectorConfigDTO());
    dslVariables.put("collectHostData", dataCollectionInfo.isCollectHostData());
    dslVariables.put("startTimeMillis", startTime.toEpochMilli());
    dslVariables.put("endTimeMillis", endTime.toEpochMilli());
    return dslVariables;
  }
}
