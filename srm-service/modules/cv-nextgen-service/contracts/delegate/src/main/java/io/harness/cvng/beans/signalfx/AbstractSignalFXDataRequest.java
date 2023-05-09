/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.signalfx;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.delegate.beans.cvng.signalfx.SignalFXUtils;

import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(CV)
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractSignalFXDataRequest extends DataCollectionRequest<SignalFXConnectorDTO> {
  @Override
  public String getBaseUrl() {
    return getConnectorConfigDTO().getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return SignalFXUtils.collectionHeaders(getConnectorConfigDTO());
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    return new HashMap<>();
  }
}
