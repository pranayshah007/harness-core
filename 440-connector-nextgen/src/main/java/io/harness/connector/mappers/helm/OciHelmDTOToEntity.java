/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.helm;

import static io.harness.delegate.beans.connector.ConnectorType.OCI_HELM_REPO;
import static io.harness.delegate.beans.connector.helm.OciHelmAuthType.USER_PASSWORD;

import io.harness.connector.entities.embedded.helm.OciHelmConnector;
import io.harness.connector.entities.embedded.helm.OciHelmUsernamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;

@Singleton
public class OciHelmDTOToEntity implements ConnectorDTOToEntityMapper<OciHelmConnectorDTO, OciHelmConnector> {
  @Override
  public OciHelmConnector toConnectorEntity(OciHelmConnectorDTO connectorDTO) {
    OciHelmConnector connectorEntity = OciHelmConnector.builder()
                                           .url(connectorDTO.getHelmRepoUrl())
                                           .authType(connectorDTO.getAuth().getAuthType())
                                           .ociHelmAuthentication(createOciHelmAuthentication(connectorDTO))
                                           .build();

    connectorEntity.setType(OCI_HELM_REPO);
    return connectorEntity;
  }

  private OciHelmUsernamePasswordAuthentication createOciHelmAuthentication(OciHelmConnectorDTO connectorDTO) {
    if (connectorDTO.getAuth().getAuthType() != USER_PASSWORD) {
      return null;
    }

    OciHelmUsernamePasswordDTO usernamePasswordDTO =
        (OciHelmUsernamePasswordDTO) connectorDTO.getAuth().getCredentials();
    return OciHelmUsernamePasswordAuthentication.builder()
        .username(usernamePasswordDTO.getUsername())
        .usernameRef(SecretRefHelper.getSecretConfigString(usernamePasswordDTO.getUsernameRef()))
        .passwordRef(SecretRefHelper.getSecretConfigString(usernamePasswordDTO.getPasswordRef()))
        .build();
  }
}
