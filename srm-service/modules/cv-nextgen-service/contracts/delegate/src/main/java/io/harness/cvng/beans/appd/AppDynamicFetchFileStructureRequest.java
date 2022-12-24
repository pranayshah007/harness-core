/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.appd;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.delegate.beans.cvng.appd.AppDynamicsUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@JsonTypeName("APPDYNAMICS_FETCH_METRIC_STRUCTURE")
@Data
@SuperBuilder
@OwnedBy(CV)
public class AppDynamicFetchFileStructureRequest extends AppDynamicsDataCollectionRequest {
  public static final String DSL = AppDynamicsDataCollectionRequest.readDSL(
      "appd-fetch-file-structure.datacollection", AppDynamicFetchFileStructureRequest.class);

  public AppDynamicFetchFileStructureRequest() {
    setType(DataCollectionRequestType.APPDYNAMICS_FETCH_METRIC_STRUCTURE);
  }

  private String appName;
  private String path;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> envVariables = AppDynamicsUtils.getCommonEnvVariables(getConnectorConfigDTO());
    envVariables.put("appName", appName);
    envVariables.put("path", path);
    return envVariables;
  }
}
