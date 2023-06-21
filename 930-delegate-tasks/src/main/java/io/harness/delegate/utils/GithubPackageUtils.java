/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.ssh.artifact.GithubPackagesArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(CDP)
public class GithubPackageUtils {
  private static final String MAVEN_PACKAGE_TYPE = "maven";
  private static final String NUGET_PACKAGE_TYPE = "nuget";
  private static final String METADATA_URL = "url";

  public static GithubPackagesArtifactDelegateConfig getGithubPackagesArtifactDelegateConfig(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig) {
    if (!(artifactDelegateConfig instanceof GithubPackagesArtifactDelegateConfig)) {
      log.warn(
          "Wrong artifact delegate config submitted. Expecting GithubPackagesArtifactDelegateConfig, but provided: {}",
          artifactDelegateConfig.getClass());
      throw new InvalidRequestException("Invalid artifact delegate config submitted, expected GithubPackages config");
    }

    return (GithubPackagesArtifactDelegateConfig) artifactDelegateConfig;
  }

  public void decryptRequestDTOs(GithubConnectorDTO githubConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      SecretDecryptionService secretDecryptionService) {
    GithubApiAccessDTO githubApiAccessDTO = githubConnectorDTO.getApiAccess();

    if (githubApiAccessDTO != null) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();

      secretDecryptionService.decrypt(githubTokenSpecDTO, encryptedDataDetails);
    }
    GithubAuthenticationDTO githubAuthenticationDTO = githubConnectorDTO.getAuthentication();
    if (githubAuthenticationDTO != null && GitAuthType.HTTP.equals(githubAuthenticationDTO.getAuthType())) {
      GithubHttpCredentialsDTO githubHttpCredentialsDTO =
          (GithubHttpCredentialsDTO) githubAuthenticationDTO.getCredentials();
      if (githubHttpCredentialsDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        GithubUsernamePasswordDTO githubUsernamePasswordDTO =
            (GithubUsernamePasswordDTO) githubHttpCredentialsDTO.getHttpCredentialsSpec();
        secretDecryptionService.decrypt(githubUsernamePasswordDTO, encryptedDataDetails);
      } else if (githubHttpCredentialsDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
        GithubUsernameTokenDTO githubUsernameTokenDTO =
            (GithubUsernameTokenDTO) githubHttpCredentialsDTO.getHttpCredentialsSpec();
        secretDecryptionService.decrypt(githubUsernameTokenDTO, encryptedDataDetails);
      }
    }
  }

  public String getBasicAuthHeader(GithubPackagesInternalConfig githubPackagesInternalConfig) {
    // only API token is used
    if (EmptyPredicate.isEmpty(githubPackagesInternalConfig.getToken())) {
      throw new InvalidRequestException("Github Api access token can't be empty");
    }

    return ArtifactUtilities.getBasicAuthHeader(githubPackagesInternalConfig.hasCredentials(),
        githubPackagesInternalConfig.getUsername(), githubPackagesInternalConfig.getToken().toCharArray());
  }

  public String getArtifactFileName(String packageType, Map<String, String> metadata) {
    final String artifactUrl = metadata.get(METADATA_URL);
    switch (packageType) {
      case MAVEN_PACKAGE_TYPE:
        return ArtifactUtilities.getArtifactName(artifactUrl);
      default:
        throw new InvalidRequestException("Invalid packageType: %s for Github package artifact");
    }
  }
}
