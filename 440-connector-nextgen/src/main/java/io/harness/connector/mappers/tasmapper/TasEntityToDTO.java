/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.connector.mappers.tasmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.tasconnector.TasConfig;
import io.harness.connector.entities.embedded.tasconnector.TasManualCredential;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class TasEntityToDTO implements ConnectorEntityToDTOMapper<TasConnectorDTO, TasConfig> {
  @Override
  public TasConnectorDTO createConnectorDTO(TasConfig connector) {
    final TasCredentialType credentialType = connector.getCredentialType();
    if (credentialType == TasCredentialType.MANUAL_CREDENTIALS) {
      return buildManualCredential(connector);
    }
    throw new InvalidRequestException("Invalid Credential type.");
  }

  private TasConnectorDTO buildManualCredential(TasConfig connector) {
    TasManualCredential tasCredential = (TasManualCredential) connector.getCredential();
    TasCredentialDTO tasCredentialDTO =
        TasCredentialDTO.builder()
            .type(TasCredentialType.MANUAL_CREDENTIALS)
            .spec(TasManualDetailsDTO.builder()
                      .endpointUrl(tasCredential.getEndpointUrl())
                      .username(tasCredential.getUserName())
                      .usernameRef(SecretRefHelper.createSecretRef(tasCredential.getUserNameRef()))
                      .passwordRef(SecretRefHelper.createSecretRef(tasCredential.getPasswordRef()))
                      .build())
            .build();
    return TasConnectorDTO.builder().credential(tasCredentialDTO).build();
  }
}
