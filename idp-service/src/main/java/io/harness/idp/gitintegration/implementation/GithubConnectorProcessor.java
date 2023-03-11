/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.implementation;

import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_PROXY;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.TMP_LOCATION_FOR_GIT_CLONE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientV2Impl;
import io.harness.git.UsernamePasswordAuthRequest;
import io.harness.git.model.ChangeType;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.GitFileChange;
import io.harness.git.model.GitRepositoryType;
import io.harness.idp.gitintegration.GitIntegrationUtil;
import io.harness.idp.gitintegration.baseclass.ConnectorProcessor;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.math3.util.Pair;

@OwnedBy(HarnessTeam.IDP)
public class GithubConnectorProcessor extends ConnectorProcessor {
  @Inject GitClientV2Impl gitClientV2;

  @Override
  public String getInfraConnectorType(String accountIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO =
        NGRestUtils.getResponse(connectorResourceClient.get(connectorIdentifier, accountIdentifier, null, null));
    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Connector not found for identifier: [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }

    ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    return config.getExecuteOnDelegate() ? CATALOG_INFRA_CONNECTOR_TYPE_PROXY : CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
  }

  public Pair<ConnectorInfoDTO, List<EnvironmentSecret>> getConnectorSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
        connectorResourceClient.get(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Github Connector not found for identifier : [%s] ", connectorIdentifier));
    }

    ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
    if (!connectorInfoDTO.getConnectorType().toString().equals(GitIntegrationConstants.GITHUB_CONNECTOR_TYPE)) {
      throw new InvalidRequestException(
          String.format("Connector with id - [%s] is not github connector ", connectorIdentifier));
    }

    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubApiAccessDTO apiAccess = config.getApiAccess();

    List<EnvironmentSecret> resultList = new ArrayList<>();

    if (apiAccess != null && apiAccess.getType().toString().equals(GitIntegrationConstants.GITHUB_APP_CONNECTOR_TYPE)) {
      GithubAppSpecDTO apiAccessSpec = (GithubAppSpecDTO) apiAccess.getSpec();

      EnvironmentSecret appIDEnvironmentSecret = new EnvironmentSecret();
      appIDEnvironmentSecret.setEnvName(GitIntegrationConstants.GITHUB_APP_ID);
      appIDEnvironmentSecret.setDecryptedValue(apiAccessSpec.getApplicationId());
      resultList.add(appIDEnvironmentSecret);

      String privateRefKeySecretIdentifier = apiAccessSpec.getPrivateKeyRef().getIdentifier();
      resultList.add(
          GitIntegrationUtil.getEnvironmentSecret(ngSecretService, accountIdentifier, orgIdentifier, projectIdentifier,
              privateRefKeySecretIdentifier, connectorIdentifier, GitIntegrationConstants.GITHUB_APP_PRIVATE_KEY_REF));
    }

    GithubHttpCredentialsOutcomeDTO outcome =
        (GithubHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    if (!outcome.getType().toString().equals(GitIntegrationConstants.USERNAME_TOKEN_AUTH_TYPE)) {
      throw new InvalidRequestException(String.format(
          " Authentication is not Username and Token for Github Connector with id - [%s] ", connectorIdentifier));
    }
    GithubUsernameTokenDTO spec = (GithubUsernameTokenDTO) outcome.getSpec();

    String tokenSecretIdentifier = spec.getTokenRef().getIdentifier();
    if (tokenSecretIdentifier.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Secret identifier not found for connector: [%s] ", connectorIdentifier));
    }

    resultList.add(GitIntegrationUtil.getEnvironmentSecret(ngSecretService, accountIdentifier, orgIdentifier,
        projectIdentifier, tokenSecretIdentifier, connectorIdentifier, GitIntegrationConstants.GITHUB_TOKEN));
    return new Pair<>(connectorInfoDTO, resultList);
  }

  public void performPushOperation(
      String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo, List<String> filesToPush) {
    Pair<ConnectorInfoDTO, List<EnvironmentSecret>> connectorSecretsInfo = getConnectorSecretsInfo(
        accountIdentifier, null, null, catalogConnectorInfo.getSourceConnector().getIdentifier());
    String githubConnectorSecret = connectorSecretsInfo.getSecond().get(0).getDecryptedValue();

    GithubConnectorDTO config = (GithubConnectorDTO) connectorSecretsInfo.getFirst().getConnectorConfig();
    GithubHttpCredentialsOutcomeDTO outcome =
        (GithubHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    GithubUsernameTokenDTO spec = (GithubUsernameTokenDTO) outcome.getSpec();

    gitClientV2.cloneRepoAndCopyToDestDir(DownloadFilesRequest.builder()
                                              .repoUrl(catalogConnectorInfo.getRepo())
                                              .branch(catalogConnectorInfo.getBranch())
                                              .filePaths(Collections.singletonList(".harness-idp-entities"))
                                              .connectorId(connectorSecretsInfo.getFirst().getIdentifier())
                                              .accountId(accountIdentifier)
                                              .recursive(true)
                                              .authRequest(UsernamePasswordAuthRequest.builder()
                                                               .username(spec.getUsername())
                                                               .password(githubConnectorSecret.toCharArray())
                                                               .build())
                                              .repoType(GitRepositoryType.YAML)
                                              .destinationDirectory(TMP_LOCATION_FOR_GIT_CLONE + accountIdentifier)
                                              .build());

    List<GitFileChange> gitFileChanges = new ArrayList<>();
    filesToPush.forEach(fileToPush -> {
      GitFileChange gitFileChange;
      try {
        gitFileChange = GitFileChange.builder()
                            .filePath(fileToPush.replace("/tmp/" + accountIdentifier, ""))
                            .fileContent(Files.readString(Path.of(fileToPush)))
                            .changeType(ChangeType.ADD)
                            .accountId(accountIdentifier)
                            .build();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      gitFileChanges.add(gitFileChange);
    });

    CommitAndPushRequest commitAndPushRequest = CommitAndPushRequest.builder()
                                                    .repoUrl(catalogConnectorInfo.getRepo())
                                                    .branch(catalogConnectorInfo.getBranch())
                                                    .connectorId(connectorSecretsInfo.getFirst().getIdentifier())
                                                    .accountId(accountIdentifier)
                                                    .authRequest(UsernamePasswordAuthRequest.builder()
                                                                     .username(spec.getUsername())
                                                                     .password(githubConnectorSecret.toCharArray())
                                                                     .build())
                                                    .repoType(GitRepositoryType.YAML)
                                                    .gitFileChanges(gitFileChanges)
                                                    .authorName(spec.getUsername())
                                                    .authorEmail("idp-harness@harness.io")
                                                    .commitMessage("Importing Harness Entities to IDP")
                                                    .build();
    gitClientV2.commitAndPush(commitAndPushRequest);
  }
}
