/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.bamboo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.bamboo.BambooConnector;
import io.harness.connector.entities.embedded.bamboo.BambooUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.bamboo.BambooAuthType;
import io.harness.delegate.beans.connector.bamboo.BambooAuthenticationDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class BambooEntityToDTO implements ConnectorEntityToDTOMapper<BambooConnectorDTO, BambooConnector> {
  @Override
  public BambooConnectorDTO createConnectorDTO(BambooConnector bambooConnector) {
    BambooAuthenticationDTO bambooAuthenticationDTO = BambooAuthenticationDTO.builder().build();
    if (bambooConnector.getAuthType() == BambooAuthType.USER_PASSWORD) {
      BambooUserNamePasswordAuthentication bambooCredentials =
          (BambooUserNamePasswordAuthentication) bambooConnector.getBambooAuthentication();
      BambooUserNamePasswordDTO bambooUserNamePasswordDTO =
          BambooUserNamePasswordDTO.builder()
              .username(bambooCredentials.getUsername())
              .usernameRef(SecretRefHelper.createSecretRef(bambooCredentials.getUsernameRef()))
              .passwordRef(SecretRefHelper.createSecretRef(bambooCredentials.getPasswordRef()))
              .build();
      bambooAuthenticationDTO = bambooAuthenticationDTO.builder()
                                    .authType(bambooConnector.getAuthType())
                                    .credentials(bambooUserNamePasswordDTO)
                                    .build();
    } else {
      bambooAuthenticationDTO = BambooAuthenticationDTO.builder().authType(BambooAuthType.ANONYMOUS).build();
    }
    return BambooConnectorDTO.builder().bambooUrl(bambooConnector.getUrl()).auth(bambooAuthenticationDTO).build();
  }
}
