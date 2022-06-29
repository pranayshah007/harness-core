/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.adapter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class AzureRepoToGitMapper {
  public static final String GIT = "/_git/";
  public static GitConfigDTO mapToGitConfigDTO(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    final GitAuthType authType = azureRepoConnectorDTO.getAuthentication().getAuthType();
    final GitConnectionType connectionType = azureRepoConnectorDTO.getConnectionType();
    final String url = azureRepoConnectorDTO.getUrl();
    final String validationProject = azureRepoConnectorDTO.getValidationProject();
    final String validationRepo = azureRepoConnectorDTO.getValidationRepo();
    if (authType == null) {
      throw new InvalidRequestException("Azure Repo DTO Auth Type not found");
    }
    switch (authType) {
      case HTTP:
        return mapToGitHTTP(azureRepoConnectorDTO, connectionType, url, validationProject, validationRepo);
      case SSH:
        return mapToGitSSH(azureRepoConnectorDTO, connectionType, url, validationProject, validationRepo);
      default:
        throw new InvalidRequestException("Unknown auth type: " + authType);
    }
  }

  public GitConfigDTO mapToGitHTTP(AzureRepoConnectorDTO azureRepoConnectorDTO, GitConnectionType connectionType,
      String url, String validationProject, String validationRepo) {
    final AzureRepoHttpCredentialsDTO credentials =
        (AzureRepoHttpCredentialsDTO) azureRepoConnectorDTO.getAuthentication().getCredentials();
    String username, validationProjAndRepo;
    SecretRefData usernameRef, tokenRef;
    final AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO =
        (AzureRepoUsernameTokenDTO) credentials.getHttpCredentialsSpec();
    username = azureRepoUsernameTokenDTO.getUsername();
    validationProjAndRepo = createValidationProjectRepo(
        validationProject, validationRepo, azureRepoConnectorDTO.getAuthentication().getAuthType());
    usernameRef = azureRepoUsernameTokenDTO.getUsernameRef();
    tokenRef = azureRepoUsernameTokenDTO.getTokenRef();
    GitConfigDTO gitConfigForHttp = GitConfigCreater.getGitConfigForHttp(connectionType, url, validationProjAndRepo,
        username, usernameRef, tokenRef, azureRepoConnectorDTO.getDelegateSelectors());
    gitConfigForHttp.setExecuteOnDelegate(true);
    return gitConfigForHttp;
  }

  public GitConfigDTO mapToGitSSH(AzureRepoConnectorDTO azureRepoConnectorDTO, GitConnectionType connectionType,
      String url, String validationProject, String validationRepo) {
    String validationProjAndRepo = createValidationProjectRepo(
        validationProject, validationRepo, azureRepoConnectorDTO.getAuthentication().getAuthType());
    final AzureRepoSshCredentialsDTO credentials =
        (AzureRepoSshCredentialsDTO) azureRepoConnectorDTO.getAuthentication().getCredentials();
    final SecretRefData sshKeyRef = credentials.getSshKeyRef();
    GitConfigDTO gitConfigForSsh = GitConfigCreater.getGitConfigForSsh(
        connectionType, url, validationProjAndRepo, sshKeyRef, azureRepoConnectorDTO.getDelegateSelectors());
    gitConfigForSsh.setExecuteOnDelegate(true);
    return gitConfigForSsh;
  }

  public String createValidationProjectRepo(String validationProject, String validationRepo, GitAuthType authType) {
    if (authType == GitAuthType.HTTP) {
      return validationProject + GIT + validationRepo;
    }
    return validationProject + "/" + validationRepo;
  }
}