/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.customseceretmanager.CustomSecretManagerDTO;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerValidationParams;

import com.google.inject.Singleton;

@OwnedBy(PL)
@Singleton
public class CustomSecretManagerValidationParamProvider
    extends SecretManagerConnectorValidationParamsProvider implements ConnectorValidationParamsProvider {
  @Override
  public ConnectorValidationParams getConnectorValidationParams(ConnectorInfoDTO connectorInfoDTO, String connectorName,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return CustomSecretManagerValidationParams.builder()
        .customSecretManagerDTO((CustomSecretManagerDTO) connectorInfoDTO.getConnectorConfig())
        .connectorName(connectorName)
        .build();
  }
}
