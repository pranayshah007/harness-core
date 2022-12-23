/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.stackdriver;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.utils.StackdriverUtils.Scope.METRIC_SCOPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.utils.StackdriverUtils;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@OwnedBy(CV)
public abstract class StackdriverRequest extends DataCollectionRequest<GcpConnectorDTO> {
  @Override
  public String getBaseUrl() {
    return "https://monitoring.googleapis.com/v1/projects/";
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    return StackdriverUtils.getCommonEnvVariables(getConnectorConfigDTO(), METRIC_SCOPE);
  }
}
