/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.prometheus;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;

import java.util.Collections;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
public abstract class PrometheusRequest extends DataCollectionRequest<PrometheusConnectorDTO> {
  @Override
  public String getBaseUrl() {
    return getConnectorConfigDTO().getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return Collections.emptyMap();
  }
}
