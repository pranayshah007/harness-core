/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.docker;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.artifacts.docker.service.DockerRegistryServiceImpl;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.docker.DockerAuthCredentialsDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.utils.FieldWithPlainTextOrSecretValueHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class DockerArtifactTaskHandler {
  @Inject DockerRegistryServiceImpl dockerRegistryService;
  @Inject DecryptionHelper decryptionHelper;

  public void decryptRequestDTOs(DockerArtifactDelegateRequest dockerRequest) {
    if (isNotEmpty(dockerRequest.getEncryptedDataDetails())) {
      DecryptableEntity decryptedCredentials = decryptionHelper.decrypt(
          dockerRequest.getDockerConnectorDTO().getAuth().getCredentials(), dockerRequest.getEncryptedDataDetails());
      dockerRequest.getDockerConnectorDTO().getAuth().setCredentials((DockerAuthCredentialsDTO) decryptedCredentials);
    }
  }

  public ArtifactTaskExecutionResponse validateArtifactServer(DockerArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = dockerRegistryService.validateCredentials(toDockerInternalConfig(attributesRequest));
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  public DockerInternalConfig toDockerInternalConfig(DockerArtifactDelegateRequest request) {
    String password = "";
    String username = "";
    if (request.getDockerConnectorDTO().getAuth() != null
        && request.getDockerConnectorDTO().getAuth().getCredentials() != null) {
      DockerUserNamePasswordDTO credentials =
          (DockerUserNamePasswordDTO) request.getDockerConnectorDTO().getAuth().getCredentials();
      if (credentials.getPasswordRef() != null) {
        password = EmptyPredicate.isNotEmpty(credentials.getPasswordRef().getDecryptedValue())
            ? new String(credentials.getPasswordRef().getDecryptedValue())
            : null;
      }
      username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          credentials.getUsername(), credentials.getUsernameRef());
    }
    return DockerInternalConfig.builder()
        .providerType(request.getDockerConnectorDTO().getProviderType())
        .dockerRegistryUrl(request.getDockerConnectorDTO().getDockerRegistryUrl())
        .username(username)
        .password(password)
        .build();
  }
}
