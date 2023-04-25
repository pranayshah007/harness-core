/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.common.dtos.gitAccess.AzureRepoAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAccessTokenDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAppAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitlabAccessDTO;
import io.harness.product.ci.scm.proto.AzureProvider;
import io.harness.product.ci.scm.proto.GithubProvider;
import io.harness.product.ci.scm.proto.GitlabProvider;
import io.harness.product.ci.scm.proto.Provider;

import com.google.inject.Inject;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.PIPELINE)
public class SCMGitAccessToProviderMapper {
  @Inject ScmGitProviderHelper scmGitProviderHelper;

  public Provider mapToSCMGitProvider(GitAccessDTO gitAccessDTO) {
    if (gitAccessDTO instanceof GithubAccessDTO) {
      return mapToGithubProvider((GithubAccessDTO) gitAccessDTO);
    } else if (gitAccessDTO instanceof GitlabAccessDTO) {
      return mapToGitLabProvider((GitlabAccessDTO) gitAccessDTO);
    } else if (gitAccessDTO instanceof AzureRepoAccessDTO) {
      return mapToAzureRepoProvider((AzureRepoAccessDTO) gitAccessDTO);
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", gitAccessDTO.getClass()));
    }
  }

  private Provider mapToGithubProvider(GithubAccessDTO githubAccessDTO) {
    return Provider.newBuilder().setGithub(createGithubProvider(githubAccessDTO)).build();
  }

  private GithubProvider createGithubProvider(GithubAccessDTO githubAccessDTO) {
    if (githubAccessDTO.isGithubApp()) {
      return GithubProvider.newBuilder()
          .setAccessToken(getAccessTokenFromGithubApp(githubAccessDTO))
          .setIsGithubApp(true)
          .build();
    } else {
      return GithubProvider.newBuilder()
          .setAccessToken(getToken(((GithubAccessTokenDTO) githubAccessDTO).getTokenRef()))
          .build();
    }
  }

  private String getAccessTokenFromGithubApp(GithubAccessDTO githubAccessDTO) {
    GithubAppAccessDTO githubAppAccessDTO = (GithubAppAccessDTO) githubAccessDTO;
    return scmGitProviderHelper.getAccessTokenFromGithubApp(githubAppAccessDTO.getApplicationId(),
        githubAppAccessDTO.getApplicationIdRef(), githubAppAccessDTO.getInstallationId(),
        githubAppAccessDTO.getInstallationIdRef(), githubAppAccessDTO.getPrivateKeyRef(), "https://api.github.com/");
  }

  private String getToken(SecretRefData tokenRef) {
    return scmGitProviderHelper.getToken(tokenRef);
  }

  private Provider mapToAzureRepoProvider(AzureRepoAccessDTO azureRepoAccessDTO) {
    String personalAccessToken = getToken(azureRepoAccessDTO.getTokenRef());
    AzureProvider azureProvider = AzureProvider.newBuilder().setPersonalAccessToken(personalAccessToken).build();
    return Provider.newBuilder().setAzure(azureProvider).build();
  }

  private Provider mapToGitLabProvider(GitlabAccessDTO gitlabAccessDTO) {
    return Provider.newBuilder().setGitlab(createGitLabProvider(gitlabAccessDTO)).build();
  }

  private GitlabProvider createGitLabProvider(GitlabAccessDTO gitlabAccessDTO) {
    return GitlabProvider.newBuilder().setAccessToken(getToken(gitlabAccessDTO.getTokenRef())).build();
  }
}
