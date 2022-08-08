/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitsync.common.helper.RepoProviderHelper.getRepoProviderType;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.UnexpectedException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.beans.GitSyncDirection;
import io.harness.gitsync.common.beans.GitToHarnessFileProcessingRequest;
import io.harness.gitsync.common.beans.GitToHarnessProcessingStepStatus;
import io.harness.gitsync.common.beans.GitToHarnessProcessingStepType;
import io.harness.gitsync.common.beans.GitToHarnessProgressStatus;
import io.harness.gitsync.common.beans.YamlChangeSetEventType;
import io.harness.gitsync.common.beans.YamlChangeSetStatus;
import io.harness.gitsync.common.dtos.GitDiffResultFileDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileListDTO;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.dtos.GitToHarnessGetFilesStepRequest;
import io.harness.gitsync.common.dtos.GitToHarnessGetFilesStepResponse;
import io.harness.gitsync.common.dtos.GitToHarnessProcessMsvcStepRequest;
import io.harness.gitsync.common.dtos.GitToHarnessProcessMsvcStepResponse;
import io.harness.gitsync.common.dtos.GitToHarnessProgressDTO;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.common.helper.GitConnectivityExceptionHelper;
import io.harness.gitsync.common.helper.GitToHarnessProgressHelper;
import io.harness.gitsync.common.helper.YamlGitConfigHelper;
import io.harness.gitsync.common.service.GitBranchSyncService;
import io.harness.gitsync.common.service.GitToHarnessProgressService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.common.service.gittoharness.GitToHarnessProcessorService;
import io.harness.gitsync.core.beans.GetFilesInDiffResponseDTO;
import io.harness.gitsync.core.beans.GitWebhookRequestAttributes;
import io.harness.gitsync.core.dtos.GitCommitDTO;
import io.harness.gitsync.core.dtos.YamlChangeSetDTO;
import io.harness.gitsync.core.service.GitCommitService;
import io.harness.gitsync.core.service.YamlChangeSetHandler;
import io.harness.gitsync.gitsyncerror.service.GitSyncErrorService;
import io.harness.gitsync.logger.GitProcessingLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.utils.FilePathUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class BranchPushEventYamlChangeSetHandler implements YamlChangeSetHandler {
  private final YamlGitConfigService yamlGitConfigService;
  private final ScmOrchestratorService scmOrchestratorService;
  private final GitCommitService gitCommitService;
  private final GitToHarnessProcessorService gitToHarnessProcessorService;
  private final GitToHarnessProgressService gitToHarnessProgressService;
  private final GitToHarnessProgressHelper gitToHarnessProgressHelper;
  private final GitBranchSyncService gitBranchSyncService;
  private final GitSyncErrorService gitSyncErrorService;

  @Override
  public YamlChangeSetStatus process(YamlChangeSetDTO yamlChangeSetDTO) {
    String repoURL = yamlChangeSetDTO.getRepoUrl();
    final GitWebhookRequestAttributes gitWebhookRequestAttributes = yamlChangeSetDTO.getGitWebhookRequestAttributes();
    try (AutoLogContext ignore1 = new GitProcessingLogContext(
             yamlChangeSetDTO.getAccountId(), gitWebhookRequestAttributes.getHeadCommitId(), OVERRIDE_ERROR)) {
      log.info("Processing the commitId = [{}] for the repo [{}] and branch [{}]",
          gitWebhookRequestAttributes.getHeadCommitId(), repoURL, yamlChangeSetDTO.getBranch());
      List<YamlGitConfigDTO> yamlGitConfigDTOList =
          yamlGitConfigService.getByAccountAndRepo(yamlChangeSetDTO.getAccountId(), repoURL);
      if (yamlGitConfigDTOList.isEmpty()) {
        log.info("Repo {} doesn't exist, ignoring the branch push change set event : {}", repoURL, yamlChangeSetDTO);
        return YamlChangeSetStatus.SKIPPED;
      }

      gitToHarnessProgressHelper.doPreRunChecks(yamlChangeSetDTO);

      YamlChangeSetStatus queueStatus =
          gitToHarnessProgressHelper.getQueueStatusIfEventInProgressOrAlreadyProcessed(yamlChangeSetDTO);
      if (queueStatus != null) {
        log.info("Ignoring event {} with queue status {} as event might be already completed or in process",
            yamlChangeSetDTO, queueStatus);
        return queueStatus;
      }

      boolean isCommitAlreadyProcessed = gitCommitService.isCommitAlreadyProcessed(yamlChangeSetDTO.getAccountId(),
          gitWebhookRequestAttributes.getHeadCommitId(), yamlChangeSetDTO.getRepoUrl(), yamlChangeSetDTO.getBranch());
      if (isCommitAlreadyProcessed) {
        log.info("CommitId {} already processed, ignoring the branch push change set event : {}",
            gitWebhookRequestAttributes.getHeadCommitId(), yamlChangeSetDTO);
        return YamlChangeSetStatus.SKIPPED;
      }

      // Init Progress Record for this event
      GitToHarnessProgressDTO gitToHarnessProgressRecord =
          gitToHarnessProgressService.initProgress(yamlChangeSetDTO, YamlChangeSetEventType.BRANCH_PUSH,
              GitToHarnessProcessingStepType.GET_FILES, gitWebhookRequestAttributes.getHeadCommitId());

      try {
        GitToHarnessProcessMsvcStepResponse gitToHarnessProcessMsvcStepResponse;

        Optional<GitCommitDTO> gitCommitDTO = gitCommitService.findLastGitCommit(
            yamlChangeSetDTO.getAccountId(), yamlChangeSetDTO.getRepoUrl(), yamlChangeSetDTO.getBranch());

        final GitToHarnessGetFilesStepRequest getFilesStepRequest =
            GitToHarnessGetFilesStepRequest.builder()
                .yamlChangeSetDTO(yamlChangeSetDTO)
                .yamlGitConfigDTOList(yamlGitConfigDTOList)
                .gitToHarnessProgress(gitToHarnessProgressRecord)
                .build();

        // if this branch has no commit record this means after git sync first push is from git. So getting all the
        // files from repo using branch sync.
        if (!gitCommitDTO.isPresent()) {
          log.info("We haven't performed the git sync for this branch, performing full sync");
          gitToHarnessProcessMsvcStepResponse = performBranchSync(getFilesStepRequest);
        } else {
          GitToHarnessGetFilesStepResponse gitToHarnessGetFilesStepResponse =
              performGetFilesStep(getFilesStepRequest, gitCommitDTO.get());

          gitToHarnessProcessMsvcStepResponse = performProcessFilesInMsvcStep(
              GitToHarnessProcessMsvcStepRequest.builder()
                  .yamlChangeSetDTO(yamlChangeSetDTO)
                  .yamlGitConfigDTO(yamlGitConfigDTOList.get(0))
                  .gitFileChangeDTOList(gitToHarnessGetFilesStepResponse.getGitFileChangeDTOList())
                  .gitDiffResultFileDTOList(gitToHarnessGetFilesStepResponse.getGitDiffResultFileDTOList())
                  .progressRecord(gitToHarnessGetFilesStepResponse.getProgressRecord())
                  .processingCommitId(gitToHarnessGetFilesStepResponse.getProcessingCommitId())
                  .commitMessage(gitToHarnessGetFilesStepResponse.getCommitMessage())
                  .build());
        }

        if (gitToHarnessProcessMsvcStepResponse.getGitToHarnessProgressStatus().isFailureStatus()) {
          log.error("G2H process files step failed with status : {}, marking branch push event as FAILED for retry",
              gitToHarnessProcessMsvcStepResponse.getGitToHarnessProgressStatus());
          return YamlChangeSetStatus.FAILED_WITH_RETRY;
        }

        return YamlChangeSetStatus.COMPLETED;
      } catch (Exception ex) {
        log.error("Error while processing branch push event {}", yamlChangeSetDTO, ex);
        String gitConnectivityErrorMessage = GitConnectivityExceptionHelper.getErrorMessage(ex);
        if (isNotEmpty(gitConnectivityErrorMessage)) {
          RepoProviders repoProvider = getRepoProviderType(yamlGitConfigDTOList);
          recordConnectivityErrors(yamlChangeSetDTO, gitConnectivityErrorMessage, repoProvider);
        }
        // Update the g2h status to ERROR
        gitToHarnessProgressService.updateProgressStatus(
            gitToHarnessProgressRecord.getUuid(), GitToHarnessProgressStatus.ERROR);
        return YamlChangeSetStatus.FAILED_WITH_RETRY;
      }
    }
  }

  // ---------------------------------- PRIVATE METHODS -------------------------------

  private GitToHarnessGetFilesStepResponse performGetFilesStep(
      GitToHarnessGetFilesStepRequest request, GitCommitDTO gitCommitDTO) {
    List<YamlGitConfigDTO> yamlGitConfigDTOList = request.getYamlGitConfigDTOList();
    YamlChangeSetDTO yamlChangeSetDTO = request.getYamlChangeSetDTO();

    // Mark step status in progress
    GitToHarnessProgressDTO gitToHarnessProgressRecord = gitToHarnessProgressService.updateStepStatus(
        request.getGitToHarnessProgress().getUuid(), GitToHarnessProcessingStepStatus.IN_PROGRESS);

    List<GitFileChangeDTO> gitFileChangeDTOList = null;
    List<GitDiffResultFileDTO> prFilesTobeProcessed = null;
    GetFilesInDiffResponseDTO filesFromDiffResponse;
    try {
      Set<String> rootFolderList = YamlGitConfigHelper.getRootFolderList(yamlGitConfigDTOList);
      filesFromDiffResponse = getFilesFromDiff(yamlGitConfigDTOList, yamlChangeSetDTO, rootFolderList, gitCommitDTO);
      gitFileChangeDTOList = filesFromDiffResponse.getGitFileChangeDTOList();
      prFilesTobeProcessed = filesFromDiffResponse.getPrFilesTobeProcessed();
      // TODO adding logs to debug an issue, remove after use
      StringBuilder gitFileChangeDTOListAsString = new StringBuilder("diff files :: ");
      gitFileChangeDTOList.forEach(
          gitFileChangeDTO -> gitFileChangeDTOListAsString.append(gitFileChangeDTO.toString()).append(" :::: "));
      log.info(gitFileChangeDTOListAsString.toString());
    } catch (Exception ex) {
      log.error("Error occurred while perform step : {}" + GitToHarnessProcessingStepType.GET_FILES, ex);
      // Mark step status error
      gitToHarnessProgressService.updateStepStatus(
          gitToHarnessProgressRecord.getUuid(), GitToHarnessProcessingStepStatus.ERROR);
      throw ex;
    }

    gitToHarnessProgressService.updateProcessingCommitId(
        gitToHarnessProgressRecord.getUuid(), filesFromDiffResponse.getProcessingCommitId());
    // Mark step status done
    gitToHarnessProgressRecord = gitToHarnessProgressService.updateStepStatus(
        gitToHarnessProgressRecord.getUuid(), GitToHarnessProcessingStepStatus.DONE);

    return GitToHarnessGetFilesStepResponse.builder()
        .gitFileChangeDTOList(gitFileChangeDTOList)
        .gitDiffResultFileDTOList(prFilesTobeProcessed)
        .progressRecord(gitToHarnessProgressRecord)
        .processingCommitId(filesFromDiffResponse.getProcessingCommitId())
        .commitMessage(filesFromDiffResponse.getCommitMessage())
        .build();
  }

  private void recordConnectivityErrors(
      YamlChangeSetDTO yamlChangeSetDTO, String errorMessage, RepoProviders repoProvider) {
    gitSyncErrorService.saveConnectivityError(
        yamlChangeSetDTO.getAccountId(), yamlChangeSetDTO.getRepoUrl(), errorMessage, repoProvider);
  }

  private GitToHarnessProcessMsvcStepResponse performBranchSync(GitToHarnessGetFilesStepRequest request) {
    return gitBranchSyncService.processBranchSyncEvent(request.getYamlGitConfigDTOList().get(0),
        request.getYamlChangeSetDTO().getBranch(), request.getYamlChangeSetDTO().getAccountId(), null,
        request.getYamlChangeSetDTO().getChangesetId(), request.getGitToHarnessProgress().getUuid());
  }

  private GetFilesInDiffResponseDTO getFilesFromDiff(List<YamlGitConfigDTO> yamlGitConfigDTOList,
      YamlChangeSetDTO yamlChangeSetDTO, Set<String> rootFolderList, GitCommitDTO gitCommit) {
    List<GitFileChangeDTO> fileChanges = new ArrayList<>();
    int yamlGitConfigsCount = yamlGitConfigDTOList.size();
    String initialGitCommit;
    final Optional<GitCommitDTO> lastGitCommitInG2hDirection =
        gitCommitService.findLastGitCommit(yamlChangeSetDTO.getAccountId(), yamlChangeSetDTO.getRepoUrl(),
            yamlChangeSetDTO.getBranch(), GitSyncDirection.GIT_TO_HARNESS);
    //    if (lastGitCommitInG2hDirection.isPresent()) {
    //      initialGitCommit = lastGitCommitInG2hDirection.get().getCommitId();
    //    } else {
    // todo(abhinav): as discussed with akash we have to revisit this later.
    // https://harness.atlassian.net/browse/CDNG-11297
    initialGitCommit = gitCommit.getCommitId();
    //    }

    for (int i = 0; i < yamlGitConfigsCount; i++) {
      YamlGitConfigDTO yamlGitConfigDTO = yamlGitConfigDTOList.get(i);
      try {
        final String finalCommitId = getCommitIdToBeProcessed(yamlGitConfigDTO, yamlChangeSetDTO.getBranch());
        log.info("Processing till commitId: [{}]", finalCommitId);
        log.info("Trying to get files using the yaml git config with the identifier {} in project {}",
            yamlGitConfigDTO.getIdentifier(), yamlGitConfigDTO.getProjectIdentifier());
        // Fetch files that have changed b/w push event commit id and the local commit id
        List<GitDiffResultFileDTO> prFiles =
            getDiffFilesUsingSCM(yamlChangeSetDTO, yamlGitConfigDTO, initialGitCommit, finalCommitId);
        // We need to process only those files which are in root folders
        List<GitDiffResultFileDTO> prFilesTobeProcessed = getFilePathsToBeProcessed(rootFolderList, prFiles);

        List<GitFileChangeDTO> gitFileChangeDTOList =
            getAllFileContent(yamlChangeSetDTO, yamlGitConfigDTO, prFilesTobeProcessed, finalCommitId);
        log.info("Completed get files using the yaml git config with the identifier {} in project {}",
            yamlGitConfigDTO.getIdentifier(), yamlGitConfigDTO.getProjectIdentifier());
        String commitMessage = getCommitMessage(yamlGitConfigDTO, finalCommitId);
        return GetFilesInDiffResponseDTO.builder()
            .gitFileChangeDTOList(gitFileChangeDTOList)
            .prFilesTobeProcessed(prFilesTobeProcessed)
            .processingCommitId(finalCommitId)
            .commitMessage(commitMessage)
            .build();
      } catch (Exception ex) {
        log.error("Error doing get files using the yaml git config with the identifier {} in project {}",
            yamlGitConfigDTO.getIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), ex);
        // If we are getting the exception for the last yaml git config too, then throw the exception
        if (i == yamlGitConfigsCount - 1) {
          throw ex;
        }
      }
    }
    throw new UnexpectedException("Could not get the diff between two files");
  }

  private GitToHarnessProcessMsvcStepResponse performProcessFilesInMsvcStep(
      GitToHarnessProcessMsvcStepRequest request) {
    List<GitToHarnessFileProcessingRequest> fileProcessingRequests =
        prepareFileProcessingRequests(request.getGitFileChangeDTOList(), request.getGitDiffResultFileDTOList());
    GitToHarnessProgressStatus gitToHarnessProgressStatus = gitToHarnessProcessorService.processFiles(
        request.getYamlChangeSetDTO().getAccountId(), fileProcessingRequests, request.getYamlChangeSetDTO().getBranch(),
        request.getYamlGitConfigDTO().getRepo(), request.getProcessingCommitId(), request.getProgressRecord().getUuid(),
        request.getYamlChangeSetDTO().getChangesetId(), request.getCommitMessage());
    return GitToHarnessProcessMsvcStepResponse.builder().gitToHarnessProgressStatus(gitToHarnessProgressStatus).build();
  }

  // Fetch list of files in the diff b/w last processed commit and new pushed commit, along with their change status
  private List<GitDiffResultFileDTO> getDiffFilesUsingSCM(YamlChangeSetDTO yamlChangeSetDTO,
      YamlGitConfigDTO yamlGitConfigDTO, String initialCommitId, String finalCommitId) {
    // Call to SCM api to find diff files in push event commit id and local commit id
    GitDiffResultFileListDTO gitDiffResultFileListDTO =
        scmOrchestratorService.processScmRequest(scmClientFacilitatorService
            -> scmClientFacilitatorService.listCommitsDiffFiles(yamlGitConfigDTO, initialCommitId, finalCommitId),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getAccountIdentifier());

    // TODO remove debug logs later on
    StringBuilder gitDiffResultFileList =
        new StringBuilder(String.format("Compare Commits Response from %s to %s :: ", initialCommitId, finalCommitId));
    gitDiffResultFileListDTO.getPrFileList().forEach(
        prFile -> gitDiffResultFileList.append(prFile.toString()).append(" :::: "));
    log.info(gitDiffResultFileList.toString());

    return gitDiffResultFileListDTO.getPrFileList();
  }

  // Get content for all files at the incoming webhook's commit id
  private List<GitFileChangeDTO> getAllFileContent(YamlChangeSetDTO yamlChangeSetDTO, YamlGitConfigDTO yamlGitConfigDTO,
      List<GitDiffResultFileDTO> prFile, String finalCommitId) {
    List<String> filePaths = new ArrayList<>();
    prFile.forEach(file -> filePaths.add(file.getPath()));

    return scmOrchestratorService.processScmRequest(scmClientFacilitatorService
        -> scmClientFacilitatorService.listFilesByCommitId(yamlGitConfigDTO, filePaths, finalCommitId),
        yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
        yamlGitConfigDTO.getAccountIdentifier());
  }

  private List<GitToHarnessFileProcessingRequest> prepareFileProcessingRequests(
      List<GitFileChangeDTO> gitFileChangeDTOList, List<GitDiffResultFileDTO> gitDiffResultFileDTOList) {
    List<GitToHarnessFileProcessingRequest> fileProcessingRequests = new ArrayList<>();
    Map<String, GitFileChangeDTO> filePathToFileChangeDTOMap = new HashMap<>();

    gitFileChangeDTOList.forEach(
        gitFileChangeDTO -> filePathToFileChangeDTOMap.put(gitFileChangeDTO.getPath(), gitFileChangeDTO));

    gitDiffResultFileDTOList.forEach(gitPRFileDTO -> {
      GitFileChangeDTO gitFileChangeDTO = filePathToFileChangeDTOMap.get(gitPRFileDTO.getPath());
      if (gitPRFileDTO.getChangeType() == ChangeType.RENAME) {
        gitFileChangeDTO.setPrevFilePath(gitPRFileDTO.getPrevFilePath());
      }
      fileProcessingRequests.add(GitToHarnessFileProcessingRequest.builder()
                                     .changeType(gitPRFileDTO.getChangeType())
                                     .fileDetails(gitFileChangeDTO)
                                     .build());
    });
    return fileProcessingRequests;
  }

  // Create list of files that are part of folders in the root folder list
  private List<GitDiffResultFileDTO> getFilePathsToBeProcessed(
      Set<String> rootFolderList, List<GitDiffResultFileDTO> prFiles) {
    List<GitDiffResultFileDTO> filesToBeProcessed = new ArrayList<>();
    for (GitDiffResultFileDTO fileDTO : prFiles) {
      if (isFileMemberOfHarnessFolders(rootFolderList, fileDTO.getPath())) {
        filesToBeProcessed.add(fileDTO);
      } else {
        log.info("Ignoring the file {} as it doesn't belong to the harness folder", fileDTO.getPath());
      }
    }
    return filesToBeProcessed;
  }

  private boolean isFileMemberOfHarnessFolders(Set<String> rootFolderList, String filePath) {
    for (String rootFolder : rootFolderList) {
      if (FilePathUtils.isFilePartOfFolder(rootFolder, filePath)) {
        return true;
      }
    }
    return false;
  }

  private String getCommitIdToBeProcessed(YamlGitConfigDTO yamlGitConfigDTO, String branch) {
    return scmOrchestratorService.processScmRequest(scmClient
        -> scmClient.getLatestCommit(yamlGitConfigDTO, branch).getSha(),
        yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
        yamlGitConfigDTO.getAccountIdentifier());
  }

  private String getCommitMessage(YamlGitConfigDTO yamlGitConfig, String commitId) {
    return scmOrchestratorService
        .processScmRequest(scmClientFacilitatorService
            -> scmClientFacilitatorService.findCommitById(yamlGitConfig, commitId),
            yamlGitConfig.getProjectIdentifier(), yamlGitConfig.getOrganizationIdentifier(),
            yamlGitConfig.getAccountIdentifier())
        .getMessage();
  }
}
