/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.artifact.container;

import static io.harness.delegate.beans.connector.docker.DockerAuthType.USER_PASSWORD;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefHelper;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.DockerConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.persistence.artifact.Artifact;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;

import java.util.Optional;

public final class DockerArtifactConnectorMapper extends ArtifactConnectorMapper {
  private static final String PUBLIC_DOCKER_REGISTER_URL = "https://index.docker.io";

  public DockerArtifactConnectorMapper(Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    super(artifact, artifactStreamAttributes);
  }

  @Override
  public ConnectorConfigDTO getConnectorDTO() {
    DockerConfig dockerConfig = (DockerConfig) artifactStreamAttributes.getServerSetting().getValue();
    String dockerUserName = dockerConfig.getUsername();
    String passwordSecretRef = dockerConfig.getEncryptedPassword();

    DockerUserNamePasswordDTO dockerUserNamePasswordDTO =
        DockerUserNamePasswordDTO.builder()
            .username(dockerUserName)
            .passwordRef(SecretRefHelper.createSecretRef(passwordSecretRef, Scope.ACCOUNT, null))
            .build();
    DockerAuthenticationDTO dockerAuthenticationDTO =
        DockerAuthenticationDTO.builder().authType(USER_PASSWORD).credentials(dockerUserNamePasswordDTO).build();
    return DockerConnectorDTO.builder()
        .dockerRegistryUrl(PUBLIC_DOCKER_REGISTER_URL)
        .auth(dockerAuthenticationDTO)
        .build();
  }

  @Override
  public AzureRegistryType getAzureRegistryType() {
    DockerConfig dockerConfig = (DockerConfig) artifactStreamAttributes.getServerSetting().getValue();
    String dockerUserName = dockerConfig.getUsername();
    String passwordSecretRef = dockerConfig.getEncryptedPassword();
    if (isNotBlank(dockerUserName) && isNotBlank(passwordSecretRef)) {
      return AzureRegistryType.DOCKER_HUB_PRIVATE;
    } else {
      return AzureRegistryType.DOCKER_HUB_PUBLIC;
    }
  }

  @Override
  public boolean isDockerArtifactType() {
    return true;
  }

  @Override
  public Optional<DecryptableEntity> getConnectorDTOAuthCredentials(ConnectorConfigDTO connectorConfigDTO) {
    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorConfigDTO;
    return Optional.ofNullable(dockerConnectorDTO.getAuth().getCredentials());
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.ofNullable((DockerConfig) artifactStreamAttributes.getServerSetting().getValue());
  }

  @Override
  public String getFullImageName() {
    return artifact.getArtifactSourceName();
  }
}
