/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasGithubPackageRegistrySettingsProvider extends AbstractTasRegistrySettingsProvider {
  @Inject DecryptionHelper decryptionHelper;

  @Override
  public TasArtifactCreds getContainerSettings(
      TasContainerArtifactConfig artifactConfig, DecryptionHelper decryptionHelper) {
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) artifactConfig.getConnectorConfig();

    if (GitAuthType.HTTP != githubConnectorDTO.getAuthentication().getAuthType()) {
      throw new InvalidRequestException(format("Invalid credentials type, %s are not supported",
          githubConnectorDTO.getAuthentication().getAuthType().toString()));
    }
    decryptEntity(
        decryptionHelper, githubConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

    String registryUrl = getRegistryUrl(artifactConfig.getImage());
    String username = getUsername(githubConnectorDTO);
    String password = getPassword(githubConnectorDTO);

    validateSettings(artifactConfig, registryUrl, username, password);
    return populateDockerSettings(registryUrl, username, password);
  }

  private String getRegistryUrl(String imagePath) {
    int indexTagPrefix = imagePath.lastIndexOf(':');
    String imageUrlWithoutTag = imagePath.substring(0, indexTagPrefix);
    return imageUrlToRegistryUrl(imageUrlWithoutTag);
  }

  private String imageUrlToRegistryUrl(String imageUrl) {
    String fullImageUrl = "https://" + imageUrl + (imageUrl.endsWith("/") ? "" : "/");
    fullImageUrl = fullImageUrl.substring(0, fullImageUrl.length() - 1);
    int index = fullImageUrl.lastIndexOf('/');
    return fullImageUrl.substring(0, index + 1);
  }

  private String getUsername(GithubConnectorDTO githubConnectorDTO) {
    if (githubConnectorDTO.getAuthentication() != null
        && githubConnectorDTO.getAuthentication().getCredentials() != null) {
      if (githubConnectorDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
        GithubHttpCredentialsDTO httpDTO =
            (GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials();

        if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
          GithubUsernamePasswordDTO githubUsernamePasswordDTO =
              (GithubUsernamePasswordDTO) httpDTO.getHttpCredentialsSpec();

          return FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernamePasswordDTO.getUsername(), githubUsernamePasswordDTO.getUsernameRef());

        } else if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
          GithubUsernameTokenDTO githubUsernameTokenDTO = (GithubUsernameTokenDTO) httpDTO.getHttpCredentialsSpec();

          return FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernameTokenDTO.getUsername(), githubUsernameTokenDTO.getUsernameRef());
        }
      }
    }
    throw new InvalidRequestException("Invalid Github Connector selected");
  }

  private String getPassword(GithubConnectorDTO githubConnectorDTO) {
    GithubApiAccessDTO githubApiAccessDTO = githubConnectorDTO.getApiAccess();
    if (githubApiAccessDTO == null) {
      throw new InvalidRequestException("Please enable the API Access for the Github Connector");
    }

    GithubApiAccessType githubApiAccessType = githubApiAccessDTO.getType();

    if (githubApiAccessType == GithubApiAccessType.TOKEN) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();

      if (githubTokenSpecDTO.getTokenRef() != null) {
        return String.valueOf(githubTokenSpecDTO.getTokenRef().getDecryptedValue());
      } else {
        throw new InvalidRequestException("The token reference for the Github Connector is null");
      }
    } else {
      throw new InvalidRequestException("Please select the API Access auth type to Token");
    }
  }
}
