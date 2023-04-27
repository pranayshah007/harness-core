/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import io.harness.ScopeIdentifiers;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketOAuthDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.AzureRepoAccessRequest;
import io.harness.gitsync.BitbucketAccessRequest;
import io.harness.gitsync.BitbucketOAuthAccessRequest;
import io.harness.gitsync.BitbucketUserNameTokenAccessRequest;
import io.harness.gitsync.GitAccessRequest;
import io.harness.gitsync.GithubAccessRequest;
import io.harness.gitsync.GithubAppAccessRequest;
import io.harness.gitsync.GithubTokenAccessRequest;
import io.harness.gitsync.GitlabAccessRequest;
import io.harness.gitsync.common.dtos.AzureRepoSCMDTO;
import io.harness.gitsync.common.dtos.BitbucketSCMDTO;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.GitlabSCMDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GitAccessRequestMapper {
  public GitAccessRequest buildGitAccessRequest(UserSourceCodeManagerDTO userSourceCodeManagerDTO) {
    String accountIdentifier = userSourceCodeManagerDTO.getAccountIdentifier();
    switch (userSourceCodeManagerDTO.getType()) {
      case GITHUB:
        GithubSCMDTO githubSCMDTO = (GithubSCMDTO) userSourceCodeManagerDTO;
        GithubApiAccessDTO githubApiAccessDTO = githubSCMDTO.getApiAccess();
        switch (githubApiAccessDTO.getType()) {
          case OAUTH:
            return GitAccessRequest.newBuilder()
                .setGithub(GithubAccessRequest.newBuilder().setTokenRequest(
                    GithubTokenAccessRequest.newBuilder()
                        .setTokenRef(buildSecretRefData(
                            ((GithubOauthDTO) githubApiAccessDTO.getSpec()).getTokenRef().getIdentifier(),
                            accountIdentifier))
                        .build()))
                .build();
          case TOKEN:
            return GitAccessRequest.newBuilder()
                .setGithub(GithubAccessRequest.newBuilder().setTokenRequest(
                    GithubTokenAccessRequest.newBuilder()
                        .setTokenRef(buildSecretRefData(
                            ((GithubTokenSpecDTO) githubApiAccessDTO.getSpec()).getTokenRef().getIdentifier(),
                            accountIdentifier))
                        .build()))
                .build();
          case GITHUB_APP:
            GithubAppSpecDTO githubAppSpecDTO = (GithubAppSpecDTO) githubApiAccessDTO.getSpec();
            return GitAccessRequest.newBuilder()
                .setGithub(GithubAccessRequest.newBuilder().setAppRequest(
                    GithubAppAccessRequest.newBuilder()
                        .setApplicationId(githubAppSpecDTO.getApplicationId())
                        .setApplicationIdRef(buildSecretRefData(
                            githubAppSpecDTO.getApplicationIdRef().getIdentifier(), accountIdentifier))
                        .setInstallationIdRef(buildSecretRefData(
                            githubAppSpecDTO.getInstallationIdRef().getIdentifier(), accountIdentifier))
                        .setPrivateKeyRef(
                            buildSecretRefData(githubAppSpecDTO.getPrivateKeyRef().getIdentifier(), accountIdentifier))
                        .setApplicationId(githubAppSpecDTO.getInstallationId())
                        .build()))
                .build();
          default:
            throw new InvalidRequestException(
                String.format("ApiAccessType %s is not allowed for Github", githubApiAccessDTO.getType()));
        }
      case GITLAB:
        GitlabSCMDTO gitlabSCMDTO = (GitlabSCMDTO) userSourceCodeManagerDTO;
        GitlabApiAccessDTO gitlabApiAccessDTO = gitlabSCMDTO.getApiAccess();
        switch (gitlabApiAccessDTO.getType()) {
          case OAUTH:
            return GitAccessRequest.newBuilder()
                .setGitlab(GitlabAccessRequest.newBuilder().setTokenRef(buildSecretRefData(
                    ((GitlabOauthDTO) gitlabApiAccessDTO.getSpec()).getTokenRef().getIdentifier(), accountIdentifier)))
                .build();
          case TOKEN:
            return GitAccessRequest.newBuilder()
                .setGitlab(GitlabAccessRequest.newBuilder().setTokenRef(buildSecretRefData(
                    ((GitlabTokenSpecDTO) gitlabApiAccessDTO.getSpec()).getTokenRef().getIdentifier(),
                    accountIdentifier)))
                .build();
          default:
            throw new InvalidRequestException(
                String.format("ApiAccessType %s is not allowed for Gitlab", gitlabApiAccessDTO.getType()));
        }
      case AZURE_REPO:
        AzureRepoSCMDTO azureRepoSCMDTO = (AzureRepoSCMDTO) userSourceCodeManagerDTO;
        AzureRepoApiAccessDTO azureRepoApiAccessDTO = azureRepoSCMDTO.getApiAccess();
        switch (azureRepoApiAccessDTO.getType()) {
          case TOKEN:
            return GitAccessRequest.newBuilder()
                .setAzureRepo(
                    AzureRepoAccessRequest.newBuilder()
                        .setTokenRef(buildSecretRefData(
                            ((AzureRepoTokenSpecDTO) azureRepoApiAccessDTO.getSpec()).getTokenRef().getIdentifier(),
                            accountIdentifier))
                        .build())
                .build();
          default:
            throw new InvalidRequestException(
                String.format("ApiAccessType %s is not allowed for Azure Repo", azureRepoApiAccessDTO.getType()));
        }
      case BITBUCKET:
        BitbucketSCMDTO bitbucketSCMDTO = (BitbucketSCMDTO) userSourceCodeManagerDTO;
        BitbucketApiAccessDTO bitbucketApiAccessDTO = bitbucketSCMDTO.getApiAccess();
        switch (bitbucketApiAccessDTO.getType()) {
          case OAUTH:
            return GitAccessRequest.newBuilder()
                .setBitbucket(
                    BitbucketAccessRequest.newBuilder()
                        .setBitbucketOAuthAccessRequest(
                            BitbucketOAuthAccessRequest.newBuilder()
                                .setTokenRef(buildSecretRefData(
                                    ((BitbucketOAuthDTO) bitbucketApiAccessDTO.getSpec()).getTokenRef().getIdentifier(),
                                    accountIdentifier))
                                .build())
                        .build())
                .build();
          case USERNAME_AND_TOKEN:
            return GitAccessRequest.newBuilder()
                .setBitbucket(BitbucketAccessRequest.newBuilder()
                                  .setBitbucketUserNameTokenAccessRequest(
                                      BitbucketUserNameTokenAccessRequest.newBuilder()
                                          .setTokenRef(buildSecretRefData(
                                              ((BitbucketUsernameTokenApiAccessDTO) bitbucketApiAccessDTO.getSpec())
                                                  .getTokenRef()
                                                  .getIdentifier(),
                                              accountIdentifier))
                                          .setUserNameRef(buildSecretRefData(
                                              ((BitbucketUsernameTokenApiAccessDTO) bitbucketApiAccessDTO.getSpec())
                                                  .getUsernameRef()
                                                  .getIdentifier(),
                                              accountIdentifier))
                                          .build())
                                  .build())
                .build();
          default:
            throw new InvalidRequestException(
                String.format("ApiAccessType %s is not allowed for Bitbucket", bitbucketApiAccessDTO.getType()));
        }
      default:
        throw new InvalidRequestException(String.format("Invalid SCM Type", userSourceCodeManagerDTO.getType()));
    }
  }

  private io.harness.gitsync.SecretRefData buildSecretRefData(String identifier, String accountIdentifier) {
    return io.harness.gitsync.SecretRefData.newBuilder()
        .setIdentifier(identifier)
        .setScope(createScopeIdentifiers(accountIdentifier))
        .build();
  }
  private ScopeIdentifiers createScopeIdentifiers(String accountIdentifier) {
    return ScopeIdentifiers.newBuilder().setAccountIdentifier(accountIdentifier).build();
  }
}
