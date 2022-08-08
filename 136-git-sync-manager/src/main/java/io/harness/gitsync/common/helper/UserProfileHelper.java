/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.userprofile.commons.AzureRepoSCMDTO;
import io.harness.ng.userprofile.commons.BitbucketSCMDTO;
import io.harness.ng.userprofile.commons.GithubSCMDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.ng.userprofile.commons.SourceCodeManagerDTO;
import io.harness.ng.userprofile.services.api.SourceCodeManagerService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.UserPrincipal;
import io.harness.security.dto.PrincipalType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(DX)
@Slf4j
public class UserProfileHelper {
  private final SourceCodeManagerService sourceCodeManagerService;
  private static final String ERROR_MSG_USER_PRINCIPAL_NOT_SET = "User not set for push event.";

  @Inject
  public UserProfileHelper(SourceCodeManagerService sourceCodeManagerService) {
    this.sourceCodeManagerService = sourceCodeManagerService;
  }

  public void setConnectorDetailsFromUserProfile(YamlGitConfigDTO yamlGitConfig, ConnectorResponseDTO connector) {
    ScmConnector scmConnector = (ScmConnector) connector.getConnector().getConnectorConfig();
    scmConnector.setUrl(yamlGitConfig.getRepo());

    SCMType scmType = SCMType.fromConnectorType(scmConnector.getConnectorType());
    SourceCodeManagerDTO userScmProfile =
        getUserScmProfile(yamlGitConfig.getAccountIdentifier(), getUserPrincipal(), scmType);
    switch (scmType) {
      case GITHUB:
        GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) scmConnector;

        SecretRefData tokenRef = ((GithubUsernameTokenDTO) ((GithubHttpCredentialsDTO) ((GithubSCMDTO) userScmProfile)
                                                                .getAuthentication()
                                                                .getCredentials())
                                      .getHttpCredentialsSpec())
                                     .getTokenRef();
        githubConnectorDTO.setApiAccess(GithubApiAccessDTO.builder()
                                            .type(GithubApiAccessType.TOKEN)
                                            .spec(GithubTokenSpecDTO.builder().tokenRef(tokenRef).build())
                                            .build());
        break;

      case BITBUCKET:
        BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) scmConnector;

        BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
            (BitbucketUsernamePasswordDTO) ((BitbucketHttpCredentialsDTO) ((BitbucketSCMDTO) userScmProfile)
                                                .getAuthentication()
                                                .getCredentials())
                .getHttpCredentialsSpec();
        bitbucketConnectorDTO.setApiAccess(BitbucketApiAccessDTO.builder()
                                               .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                                               .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                                         .username(bitbucketUsernamePasswordDTO.getUsername())
                                                         .tokenRef(bitbucketUsernamePasswordDTO.getPasswordRef())
                                                         .build())
                                               .build());
        break;
      case AZURE_REPO:
        AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) scmConnector;

        AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO =
            (AzureRepoUsernameTokenDTO) ((AzureRepoHttpCredentialsDTO) ((AzureRepoSCMDTO) userScmProfile)
                                             .getAuthentication()
                                             .getCredentials())
                .getHttpCredentialsSpec();
        azureRepoConnectorDTO.setApiAccess(
            AzureRepoApiAccessDTO.builder()
                .type(AzureRepoApiAccessType.TOKEN)
                .spec(AzureRepoTokenSpecDTO.builder().tokenRef(azureRepoUsernameTokenDTO.getTokenRef()).build())
                .build());
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + scmConnector.getConnectorType());
    }
  }

  public UserPrincipal getUserPrincipal() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      io.harness.security.dto.UserPrincipal userPrincipal =
          (io.harness.security.dto.UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
      return UserPrincipalMapper.toProto(userPrincipal);
    }
    log.error(ERROR_MSG_USER_PRINCIPAL_NOT_SET);
    throw new InvalidRequestException(ERROR_MSG_USER_PRINCIPAL_NOT_SET);
  }

  public String getScmUserName(String accountIdentifier, SCMType scmType) {
    SourceCodeManagerDTO userScmProfile = getUserScmProfile(accountIdentifier, getUserPrincipal(), scmType);
    switch (scmType) {
      case BITBUCKET:
        BitbucketSCMDTO bitBucketSCM = (BitbucketSCMDTO) userScmProfile;
        return ((BitbucketUsernamePasswordDTO) ((BitbucketHttpCredentialsDTO) bitBucketSCM.getAuthentication()
                                                    .getCredentials())
                    .getHttpCredentialsSpec())
            .getUsername();
      case GITHUB:
        GithubSCMDTO githubSCM = (GithubSCMDTO) userScmProfile;
        return ((GithubUsernameTokenDTO) ((GithubHttpCredentialsDTO) githubSCM.getAuthentication().getCredentials())
                    .getHttpCredentialsSpec())
            .getUsername();
      case AZURE_REPO:
        AzureRepoSCMDTO azureRepoSCMDTO = (AzureRepoSCMDTO) userScmProfile;
        return ((AzureRepoUsernameTokenDTO) ((AzureRepoHttpCredentialsDTO) azureRepoSCMDTO.getAuthentication()
                                                 .getCredentials())
                    .getHttpCredentialsSpec())
            .getUsername();
      default:
        throw new InvalidRequestException(String.format("Unsupported Source Code Manager : %s", scmType));
    }
  }

  private SourceCodeManagerDTO getUserScmProfile(String accountId, UserPrincipal userPrincipal, SCMType scmType) {
    if (userPrincipal == null) {
      log.error(ERROR_MSG_USER_PRINCIPAL_NOT_SET);
      throw new InvalidRequestException(ERROR_MSG_USER_PRINCIPAL_NOT_SET);
    }
    final List<SourceCodeManagerDTO> sourceCodeManager =
        sourceCodeManagerService.get(userPrincipal.getUserId().getValue(), accountId);
    final Optional<SourceCodeManagerDTO> sourceCodeManagerDTO =
        sourceCodeManager.stream().filter(scm -> scm.getType().equals(scmType)).findFirst();
    if (!sourceCodeManagerDTO.isPresent()) {
      log.error("User profile doesn't contain {} source code manager details", scmType);
      throw new InvalidRequestException(
          String.format("User profile doesn't contain %s source code manager details", scmType));
    }
    return sourceCodeManagerDTO.get();
  }

  public boolean validateIfScmUserProfileIsSet(String accountIdentifier) {
    List<SourceCodeManagerDTO> sourceCodeManagerDTOS = sourceCodeManagerService.get(accountIdentifier);
    if (sourceCodeManagerDTOS.isEmpty()) {
      throw new InvalidRequestException("We don’t have your git credentials for the selected folder."
          + " Please update the credentials in user profile.");
    }
    return true;
  }
}
