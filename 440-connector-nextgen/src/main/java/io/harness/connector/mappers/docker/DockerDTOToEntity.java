/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.docker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.docker.DockerConnector;
import io.harness.connector.entities.embedded.docker.DockerConnector.DockerConnectorBuilder;
import io.harness.connector.entities.embedded.docker.DockerUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class DockerDTOToEntity implements ConnectorDTOToEntityMapper<DockerConnectorDTO, DockerConnector> {
  @Override
  public DockerConnector toConnectorEntity(DockerConnectorDTO configDTO) {
    DockerAuthType dockerAuthType = configDTO.getAuth().getAuthType();
    DockerConnectorBuilder dockerConnectorBuilder = DockerConnector.builder()
                                                        .url(StringUtils.trim(configDTO.getDockerRegistryUrl()))
                                                        .providerType(configDTO.getProviderType())
                                                        .authType(dockerAuthType);
    if (dockerAuthType == DockerAuthType.USER_PASSWORD) {
      DockerUserNamePasswordDTO dockerUserNamePasswordDTO =
          (DockerUserNamePasswordDTO) configDTO.getAuth().getCredentials();
      dockerConnectorBuilder.dockerAuthentication(createDockerAuthentication(dockerUserNamePasswordDTO));
    }

    DockerConnector dockerConnector = dockerConnectorBuilder.proxy(configDTO.getProxy()).build();
    dockerConnector.setType(ConnectorType.DOCKER);
    return dockerConnector;
  }

  private DockerUserNamePasswordAuthentication createDockerAuthentication(
      DockerUserNamePasswordDTO dockerUserNamePasswordDTO) {
    return DockerUserNamePasswordAuthentication.builder()
        .username(dockerUserNamePasswordDTO.getUsername())
        .usernameRef(SecretRefHelper.getSecretConfigString(dockerUserNamePasswordDTO.getUsernameRef()))
        .passwordRef(SecretRefHelper.getSecretConfigString(dockerUserNamePasswordDTO.getPasswordRef()))
        .build();
  }
}
