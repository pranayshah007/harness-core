/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileListDTO;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.helper.GitRepoHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.service.ScmOrchestratorService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class GitXWebhookEventHelper {
  private final ScmOrchestratorService scmOrchestratorService;

  private final GitSyncConnectorHelper gitSyncConnectorHelper;

  private final GitRepoHelper gitRepoHelper;

  public List<GitDiffResultFileDTO> getDiffFilesUsingSCM(
      String accountIdentifier, String connectorRef, String repoName, String initialCommitId, String finalCommitId) {
    // TODO: add global try
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnector(accountIdentifier, "", "", connectorRef);
    scmConnector.setUrl(gitRepoHelper.getRepoUrl(scmConnector, repoName));
    GitDiffResultFileListDTO gitDiffResultFileListDTO =
        scmOrchestratorService.processScmRequest(scmClientFacilitatorService
            -> scmClientFacilitatorService.listCommitsDiffFiles(
                Scope.of(accountIdentifier, "", ""), scmConnector, initialCommitId, finalCommitId),
            "", "", accountIdentifier);

    StringBuilder gitDiffResultFileList =
        new StringBuilder(String.format("Compare Commits Response from %s to %s :: ", initialCommitId, finalCommitId));
    gitDiffResultFileListDTO.getPrFileList().forEach(
        prFile -> gitDiffResultFileList.append(prFile.toString()).append(" :::: "));
    log.info(gitDiffResultFileList.toString());

    return gitDiffResultFileListDTO.getPrFileList();
  }

  private List<GitFileChangeDTO> getAllFileContent(
      YamlGitConfigDTO yamlGitConfigDTO, List<GitDiffResultFileDTO> prFile, String finalCommitId) {
    List<String> filePaths = new ArrayList<>();
    prFile.forEach(file -> filePaths.add(file.getPath()));

    return scmOrchestratorService.processScmRequest(scmClientFacilitatorService
        -> scmClientFacilitatorService.listFilesByCommitId(yamlGitConfigDTO, filePaths, finalCommitId),
        yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
        yamlGitConfigDTO.getAccountIdentifier());
  }
}
