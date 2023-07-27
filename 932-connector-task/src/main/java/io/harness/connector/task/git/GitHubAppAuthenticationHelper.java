/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.git;

import static io.harness.delegate.beans.connector.scm.github.GithubConnectorConstants.GITHUB_APP;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class GitHubAppAuthenticationHelper {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private GithubService githubService;

  public SecretRefData getGithubAppSecretFromConnector(
      GithubConnectorDTO githubConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    if (githubConnectorDTO == null) {
      throw new InvalidRequestException("Github connector can not be null for fetching token for github app");
    }
    GithubHttpCredentialsDTO githubHttpCredentialsDTO =
        gitDecryptionHelper.decryptGitHubAppAuthenticationConfig(githubConnectorDTO, encryptedDataDetails);
    GithubAppDTO githubAppDTO = (GithubAppDTO) githubHttpCredentialsDTO.getHttpCredentialsSpec();
    String token =
        githubService.getToken(GithubAppConfig.builder()
                                   .appId(getSecretAsStringFromPlainTextOrSecretRef(
                                       githubAppDTO.getApplicationId(), githubAppDTO.getApplicationIdRef()))
                                   .installationId(getSecretAsStringFromPlainTextOrSecretRef(
                                       githubAppDTO.getInstallationId(), githubAppDTO.getInstallationIdRef()))
                                   .privateKey(String.valueOf(githubAppDTO.getPrivateKeyRef().getDecryptedValue()))
                                   .githubUrl(GitClientHelper.getGithubApiURL(githubConnectorDTO.getUrl()))
                                   .build());
    return SecretRefData.builder().decryptedValue(token.toCharArray()).identifier(GITHUB_APP).build();
  }
}
