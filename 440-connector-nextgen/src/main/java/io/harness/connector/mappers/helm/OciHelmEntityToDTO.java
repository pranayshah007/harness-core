/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.helm;

import static io.harness.delegate.beans.connector.helm.OciHelmAuthType.ANONYMOUS;

import io.harness.connector.entities.embedded.helm.OciHelmConnector;
import io.harness.connector.entities.embedded.helm.OciHelmUsernamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;

@Singleton
public class OciHelmEntityToDTO implements ConnectorEntityToDTOMapper<OciHelmConnectorDTO, OciHelmConnector> {
  @Override
  public OciHelmConnectorDTO createConnectorDTO(OciHelmConnector connector) {
    return OciHelmConnectorDTO.builder().helmRepoUrl(connector.getUrl()).auth(createOciHelmAuthDTO(connector)).build();
  }

  private OciHelmAuthenticationDTO createOciHelmAuthDTO(OciHelmConnector connector) {
    if (connector.getOciHelmAuthentication() == null || connector.getAuthType() == ANONYMOUS) {
      return OciHelmAuthenticationDTO.builder().authType(ANONYMOUS).build();
    }

    OciHelmUsernamePasswordAuthentication ociHelmCredentials =
        (OciHelmUsernamePasswordAuthentication) connector.getOciHelmAuthentication();
    OciHelmUsernamePasswordDTO ociHelmUserNamePasswordDTO =
        OciHelmUsernamePasswordDTO.builder()
            .username(ociHelmCredentials.getUsername())
            .usernameRef(SecretRefHelper.createSecretRef(ociHelmCredentials.getUsernameRef()))
            .passwordRef(SecretRefHelper.createSecretRef(ociHelmCredentials.getPasswordRef()))
            .build();
    return OciHelmAuthenticationDTO.builder()
        .authType(connector.getAuthType())
        .credentials(ociHelmUserNamePasswordDTO)
        .build();
  }
}
