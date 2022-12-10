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
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class TasDTOToEntity implements ConnectorDTOToEntityMapper<TasConnectorDTO, TasConfig> {
  @Override
  public TasConfig toConnectorEntity(TasConnectorDTO connectorDTO) {
    final TasCredentialDTO credential = connectorDTO.getCredential();
    final TasCredentialType credentialType = credential.getType();
    final TasConfig tasConfig;
    if (credentialType == TasCredentialType.MANUAL_CREDENTIALS) {
      tasConfig = buildManualCredential(credential);
    } else {
      throw new InvalidRequestException("Invalid Credential type.");
    }

    return tasConfig;
  }

  private TasConfig buildManualCredential(TasCredentialDTO tasCredentialDTO) {
    final TasManualDetailsDTO config = (TasManualDetailsDTO) tasCredentialDTO.getSpec();
    final String endpointUrl = config.getEndpointUrl();
    final String passwordRef = SecretRefHelper.getSecretConfigString(config.getPasswordRef());
    final String usernameRef = SecretRefHelper.getSecretConfigString(config.getUsernameRef());
    final String username = config.getUsername();
    TasManualCredential credential = TasManualCredential.builder()
                                         .endpointUrl(endpointUrl)
                                         .userName(username)
                                         .userNameRef(usernameRef)
                                         .passwordRef(passwordRef)
                                         .build();
    return TasConfig.builder().credentialType(TasCredentialType.MANUAL_CREDENTIALS).credential(credential).build();
  }
}
