/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.base;

import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.HARNESS_ENTITIES_IMPORT_AUTHOR_EMAIL;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.HARNESS_ENTITIES_IMPORT_COMMIT_MESSAGE;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.TMP_LOCATION_FOR_GIT_CLONE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.git.GitClientV2Impl;
import io.harness.git.UsernamePasswordAuthRequest;
import io.harness.git.model.ChangeType;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.GitFileChange;
import io.harness.git.model.GitRepositoryType;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public abstract class ConnectorProcessor {
  @Inject public ConnectorResourceClient connectorResourceClient;
  @Inject @Named("PRIVILEGED") public SecretManagerClientService ngSecretService;
  @Inject public GitClientV2Impl gitClientV2;

  public abstract String getInfraConnectorType(String accountIdentifier, String connectorIdentifier);

  protected ConnectorInfoDTO getConnectorInfo(String accountIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO =
        NGRestUtils.getResponse(connectorResourceClient.get(connectorIdentifier, accountIdentifier, null, null));
    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Connector not found for identifier: [%s], accountIdentifier: [%s]", connectorIdentifier, accountIdentifier));
    }
    return connectorDTO.get().getConnectorInfo();
  }

  public abstract Pair<ConnectorInfoDTO, List<EnvironmentSecret>> getConnectorAndSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier);

  public abstract void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, String remoteFolder, List<String> locationToPush);

  protected void performPushOperationInternal(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, String remoteFolder, List<String> filesToPush, String username, String password) {
    gitClientV2.cloneRepoAndCopyToDestDir(
        DownloadFilesRequest.builder()
            .repoUrl(catalogConnectorInfo.getRepo())
            .branch(catalogConnectorInfo.getBranch())
            .filePaths(Collections.singletonList(remoteFolder))
            .connectorId(catalogConnectorInfo.getSourceConnector().getIdentifier())
            .accountId(accountIdentifier)
            .recursive(true)
            .authRequest(
                UsernamePasswordAuthRequest.builder().username(username).password(password.toCharArray()).build())
            .repoType(GitRepositoryType.YAML)
            .destinationDirectory(TMP_LOCATION_FOR_GIT_CLONE + accountIdentifier)
            .build());
    log.info("Cloned repo locally for update.");

    List<GitFileChange> gitFileChanges = new ArrayList<>();
    filesToPush.forEach((String fileToPush) -> {
      GitFileChange gitFileChange;
      try {
        gitFileChange = GitFileChange.builder()
                            .filePath(fileToPush.replace(locationParentPath, ""))
                            .fileContent(Files.readString(Path.of(fileToPush)))
                            .changeType(ChangeType.ADD)
                            .accountId(accountIdentifier)
                            .build();
      } catch (IOException e) {
        log.error("Error while doing git add on files. Exception = {}", e.getMessage(), e);
        throw new UnexpectedException("Error in preparing git files for commit.");
      }
      gitFileChanges.add(gitFileChange);
    });
    log.info("Prepared git files for push");

    CommitAndPushRequest commitAndPushRequest =
        CommitAndPushRequest.builder()
            .repoUrl(catalogConnectorInfo.getRepo())
            .branch(catalogConnectorInfo.getBranch())
            .connectorId(catalogConnectorInfo.getSourceConnector().getIdentifier())
            .accountId(accountIdentifier)
            .authRequest(
                UsernamePasswordAuthRequest.builder().username(username).password(password.toCharArray()).build())
            .repoType(GitRepositoryType.YAML)
            .gitFileChanges(gitFileChanges)
            .authorName(username)
            .authorEmail(HARNESS_ENTITIES_IMPORT_AUTHOR_EMAIL)
            .commitMessage(HARNESS_ENTITIES_IMPORT_COMMIT_MESSAGE)
            .build();
    gitClientV2.commitAndPush(commitAndPushRequest);
    log.info("Git commit and push done for files");
  }
}
