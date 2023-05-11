/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.signalfx;

import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.delegate.beans.cvng.ConnectorValidationInfo;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class SignalFXConnectorValidationInfo extends ConnectorValidationInfo<SignalFXConnectorDTO> {
  private static final String DSL =
      readDSL("signalFX-validation.datacollection", SignalFXConnectorValidationInfo.class);

  @Override
  public String getConnectionValidationDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return getConnectorConfigDTO().getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return SignalFXUtils.collectionHeaders(getConnectorConfigDTO());
  }
}
