/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import io.harness.ScopeIdentifiers;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.AzureRepoAccessRequest;
import io.harness.gitsync.BitbucketAccessRequest;
import io.harness.gitsync.BitbucketOAuthAccessRequest;
import io.harness.gitsync.BitbucketUserNameTokenAccessRequest;
import io.harness.gitsync.GitAccessRequest;
import io.harness.gitsync.GithubAccessRequest;
import io.harness.gitsync.GithubAppAccessRequest;
import io.harness.gitsync.GithubTokenAccessRequest;
import io.harness.gitsync.GitlabAccessRequest;
import io.harness.gitsync.UserDetailsRequest;
import io.harness.gitsync.common.dtos.gitAccess.AzureRepoAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.BitbucketOAuthAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.BitbucketUsernameTokenAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAccessTokenDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAppAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitlabAccessDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GitAccessMapper {
  public GitAccessDTO convertToGitAccessDTO(UserDetailsRequest userDetailsRequest) {
    GitAccessRequest gitAccessRequest = userDetailsRequest.getGitAccessRequest();
    if (gitAccessRequest.hasGithub()) {
      GithubAccessRequest githubAccessRequest = gitAccessRequest.getGithub();
      if (githubAccessRequest.hasAppRequest()) {
        GithubAppAccessRequest githubAppAccessRequest = githubAccessRequest.getAppRequest();
        io.harness.gitsync.SecretRefData applicationIdRef = githubAppAccessRequest.getApplicationIdRef();
        ScopeIdentifiers scopeIdentifiers = applicationIdRef.getScope();
        return GithubAppAccessDTO.builder()
            .isGithubApp(true)
            .applicationId(githubAppAccessRequest.getApplicationId())
            .installationId(githubAppAccessRequest.getInstallationId())
            .applicationIdRef(prepareSecretRefData(applicationIdRef))
            .installationIdRef(prepareSecretRefData(githubAppAccessRequest.getInstallationIdRef()))
            .privateKeyRef(prepareSecretRefData(githubAppAccessRequest.getPrivateKeyRef()))
            .tokenScope(getScope(scopeIdentifiers.getAccountIdentifier(), scopeIdentifiers.getOrgIdentifier(),
                scopeIdentifiers.getProjectIdentifier()))
            .build();
      } else {
        GithubTokenAccessRequest githubTokenAccessRequest = githubAccessRequest.getTokenRequest();
        io.harness.gitsync.SecretRefData tokenRef = githubTokenAccessRequest.getTokenRef();
        ScopeIdentifiers scopeIdentifiers = tokenRef.getScope();
        return GithubAccessTokenDTO.builder()
            .isGithubApp(false)
            .tokenRef(prepareSecretRefData(tokenRef))
            .tokenScope(getScope(scopeIdentifiers.getAccountIdentifier(), scopeIdentifiers.getOrgIdentifier(),
                scopeIdentifiers.getProjectIdentifier()))
            .build();
      }
    } else if (gitAccessRequest.hasGitlab()) {
      GitlabAccessRequest gitlabAccessRequest = gitAccessRequest.getGitlab();
      io.harness.gitsync.SecretRefData tokenRef = gitlabAccessRequest.getTokenRef();
      ScopeIdentifiers scopeIdentifiers = tokenRef.getScope();
      return GitlabAccessDTO.builder()
          .tokenRef(prepareSecretRefData(tokenRef))
          .tokenScope(getScope(scopeIdentifiers.getAccountIdentifier(), scopeIdentifiers.getOrgIdentifier(),
              scopeIdentifiers.getProjectIdentifier()))
          .build();
    } else if (gitAccessRequest.hasAzureRepo()) {
      AzureRepoAccessRequest azureRepoAccessRequest = gitAccessRequest.getAzureRepo();
      io.harness.gitsync.SecretRefData tokenRef = azureRepoAccessRequest.getTokenRef();
      ScopeIdentifiers scopeIdentifiers = tokenRef.getScope();
      return AzureRepoAccessDTO.builder()
          .tokenRef(prepareSecretRefData(tokenRef))
          .tokenScope(getScope(scopeIdentifiers.getAccountIdentifier(), scopeIdentifiers.getOrgIdentifier(),
              scopeIdentifiers.getProjectIdentifier()))
          .build();
    } else {
      BitbucketAccessRequest bitbucketAccessRequest = gitAccessRequest.getBitbucket();
      if (bitbucketAccessRequest.hasBitbucketOAuthAccessRequest()) {
        BitbucketOAuthAccessRequest bitbucketOAuthAccessRequest =
            bitbucketAccessRequest.getBitbucketOAuthAccessRequest();
        io.harness.gitsync.SecretRefData tokenRef = bitbucketOAuthAccessRequest.getTokenRef();
        ScopeIdentifiers scopeIdentifiers = tokenRef.getScope();
        return BitbucketOAuthAccessDTO.builder()
            .tokenRef(prepareSecretRefData(tokenRef))
            .tokenScope(getScope(scopeIdentifiers.getAccountIdentifier(), scopeIdentifiers.getOrgIdentifier(),
                scopeIdentifiers.getProjectIdentifier()))
            .build();
      } else {
        BitbucketUserNameTokenAccessRequest bitbucketUserNameTokenAccessRequest =
            bitbucketAccessRequest.getBitbucketUserNameTokenAccessRequest();
        io.harness.gitsync.SecretRefData tokenRef = bitbucketUserNameTokenAccessRequest.getTokenRef();
        ScopeIdentifiers scopeIdentifiers = tokenRef.getScope();
        return BitbucketUsernameTokenAccessDTO.builder()
            .tokenRef(prepareSecretRefData(tokenRef))
            .tokenScope(getScope(scopeIdentifiers.getAccountIdentifier(), scopeIdentifiers.getOrgIdentifier(),
                scopeIdentifiers.getProjectIdentifier()))
            .usernameRef(prepareSecretRefData(bitbucketUserNameTokenAccessRequest.getUserNameRef()))
            .build();
      }
    }
  }

  private SecretRefData prepareSecretRefData(io.harness.gitsync.SecretRefData secretRefData) {
    return SecretRefData.builder()
        .scope(ScopeIdentifierMapper.getEncryptionScopeFromScopeIdentifiers(secretRefData.getScope()))
        .identifier(secretRefData.getIdentifier())
        .build();
  }

  private Scope getScope(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
