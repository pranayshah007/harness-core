/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.git.Constants.DEFAULT_FETCH_IDENTIFIER;
import static io.harness.logging.LogLevel.ERROR;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileContentBatchResponse;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.GitClientException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.git.GitFetchMetadataLocalThread;
import io.harness.git.model.CommitResult;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class ScmFetchFilesHelperNG {
  @Inject private ScmDelegateClient scmDelegateClient;
  @Inject private ScmServiceClient scmServiceClient;
  private static final List<String> ROOT_DIRECTORY_PATHS = Arrays.asList(".", "/");
  private static final Pattern regexStartSlash = Pattern.compile("^/+");

  public FetchFilesResult fetchFilesFromRepoWithScm(
      GitStoreDelegateConfig gitStoreDelegateConfig, List<String> filePathList) {
    return fetchFilesFromRepoWithScm(DEFAULT_FETCH_IDENTIFIER, gitStoreDelegateConfig, filePathList);
  }
  public FetchFilesResult fetchFilesFromRepoWithScm(
      String identifier, GitStoreDelegateConfig gitStoreDelegateConfig, List<String> filePathList) {
    boolean useBranch = gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH;
    List<GitFile> gitFiles = fetchFilesFromRepo(identifier, useBranch, gitStoreDelegateConfig.getBranch(),
        gitStoreDelegateConfig.getCommitId(), filePathList, gitStoreDelegateConfig.getGitConfigDTO());
    return FetchFilesResult.builder()
        .files(gitFiles)
        .commitResult(
            CommitResult.builder().commitId(useBranch ? "latest" : gitStoreDelegateConfig.getCommitId()).build())
        .build();
  }

  public FetchFilesResult fetchAnyFilesFromRepoWithScm(
      GitStoreDelegateConfig gitStoreDelegateConfig, List<String> filePathList) {
    boolean useBranch = gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH;
    List<GitFile> gitFiles = fetchAnyFilesFromRepo(useBranch, gitStoreDelegateConfig.getBranch(),
        gitStoreDelegateConfig.getCommitId(), filePathList, gitStoreDelegateConfig.getGitConfigDTO());
    return FetchFilesResult.builder()
        .files(gitFiles)
        .commitResult(
            CommitResult.builder().commitId(useBranch ? "latest" : gitStoreDelegateConfig.getCommitId()).build())
        .build();
  }

  public String downloadFilesUsingScm(
      String manifestFilesDirectory, GitStoreDelegateConfig gitStoreDelegateConfig, LogCallback executionLogCallback) {
    String directoryPath = Paths.get(manifestFilesDirectory).toString();
    Set<String> commitIds = new HashSet<>();
    gitStoreDelegateConfig.getPaths().forEach(filePath
        -> commitIds.add(downloadFilesForFilePath(gitStoreDelegateConfig,
            filePath.replaceAll(regexStartSlash.pattern(), ""), executionLogCallback, directoryPath)));

    if (commitIds.size() > 1) {
      log.warn("Found multiple commit ids: {}, expected only one", commitIds);
    }

    return commitIds.isEmpty() ? null : commitIds.iterator().next();
  }

  private List<GitFile> fetchFilesFromRepo(String identifier, boolean useBranch, String branch, String commitId,
      List<String> filePathList, ScmConnector scmConnector) {
    FileContentBatchResponse fileBatchContentResponse =
        fetchFilesByFilePaths(useBranch, branch, commitId, filePathList, scmConnector);
    String latestCommitSHA = fileBatchContentResponse.getCommitId();
    GitFetchMetadataLocalThread.putCommitId(identifier, latestCommitSHA);
    List<GitFile> gitFiles =
        fileBatchContentResponse.getFileBatchContentResponse()
            .getFileContentsList()
            .stream()
            .filter(fileContent -> {
              if (fileContent.getStatus() != 200 || isNotEmpty(fileContent.getError())) {
                throwFailedToFetchFileException(useBranch, branch, commitId, fileContent);
                return false;
              } else {
                return true;
              }
            })
            .map(fileContent
                -> GitFile.builder().fileContent(fileContent.getContent()).filePath(fileContent.getPath()).build())
            .collect(Collectors.toList());

    if (isNotEmpty(gitFiles)) {
      gitFiles.forEach(gitFile -> log.info("File fetched : " + gitFile.getFilePath()));
    }
    return gitFiles;
  }

  private List<GitFile> fetchAnyFilesFromRepo(
      boolean useBranch, String branch, String commitId, List<String> filePathList, ScmConnector scmConnector) {
    FileContentBatchResponse fileBatchContentResponse =
        fetchFilesByFilePaths(useBranch, branch, commitId, filePathList, scmConnector);

    List<GitFile> gitFiles =
        fileBatchContentResponse.getFileBatchContentResponse()
            .getFileContentsList()
            .stream()
            .filter(fileContent -> fileContent.getStatus() == 200)
            .map(fileContent
                -> GitFile.builder().fileContent(fileContent.getContent()).filePath(fileContent.getPath()).build())
            .collect(Collectors.toList());

    if (isNotEmpty(gitFiles)) {
      gitFiles.forEach(gitFile -> log.info("File fetched : " + gitFile.getFilePath()));
    }
    return gitFiles;
  }

  private FileContentBatchResponse fetchFilesByFilePaths(
      boolean useBranch, String branch, String commitId, List<String> filePathList, ScmConnector scmConnector) {
    FileContentBatchResponse fileBatchContentResponse;
    if (useBranch) {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(
          c -> scmServiceClient.listFilesByFilePaths(scmConnector, filePathList, branch, SCMGrpc.newBlockingStub(c)));
    } else {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(
          c -> scmServiceClient.listFilesByCommitId(scmConnector, filePathList, commitId, SCMGrpc.newBlockingStub(c)));
    }
    return fileBatchContentResponse;
  }

  private String downloadFilesForFilePath(GitStoreDelegateConfig gitStoreDelegateConfig, String filePath,
      LogCallback executionLogCallback, String directoryPath) {
    FileContentBatchResponse fileBatchContentResponse = getFileContentBatchResponseByFolder(
        gitStoreDelegateConfig, Collections.singleton(filePath), gitStoreDelegateConfig.getGitConfigDTO());
    boolean useBranch = gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH;
    boolean relativize = !ROOT_DIRECTORY_PATHS.contains(filePath);
    boolean useBase64 = true;
    if (isEmpty(fileBatchContentResponse.getFileBatchContentResponse().getFileContentsList())) {
      fileBatchContentResponse =
          fetchFilesByFilePaths(useBranch, gitStoreDelegateConfig.getBranch(), gitStoreDelegateConfig.getCommitId(),
              gitStoreDelegateConfig.getPaths(), gitStoreDelegateConfig.getGitConfigDTO());
      relativize = false;
      useBase64 = false;
    }

    List<FileContent> fileContents = fileBatchContentResponse.getFileBatchContentResponse()
                                         .getFileContentsList()
                                         .stream()
                                         .filter(fileContent -> fileContent.getStatus() == 200)
                                         .collect(toList());
    if (fileContents.isEmpty()) {
      throw new YamlException(
          new StringBuilder()
              .append("Failed while fetching files ")
              .append(useBranch ? "for Branch: " : "for CommitId: ")
              .append(useBranch ? gitStoreDelegateConfig.getBranch() : gitStoreDelegateConfig.getCommitId())
              .append(", FilePaths: ")
              .append(gitStoreDelegateConfig.getPaths())
              .append(". Reason: File not found")
              .toString(),
          USER);
    }

    try {
      for (FileContent fileContent : fileContents) {
        ScmFetcherUtils.writeFile(directoryPath, fileContent, filePath, relativize, useBase64);
      }
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(ex), ERROR, CommandExecutionStatus.FAILURE);
    }

    return fileBatchContentResponse.getCommitId();
  }

  private void throwFailedToFetchFileException(
      boolean useBranch, String branch, String commitId, FileContent fileContent) {
    throw new GitClientException(new StringBuilder("Unable to fetch files for filePath [")
                                     .append(fileContent.getPath())
                                     .append("]")
                                     .append(useBranch ? " for Branch: " : " for CommitId: ")
                                     .append(useBranch ? branch : commitId)
                                     .toString(),
        USER, new NoSuchFileException(fileContent.getPath()));
  }

  private FileContentBatchResponse getFileContentBatchResponseByFolder(
      GitStoreDelegateConfig gitStoreDelegateConfig, Set<String> filePaths, ScmConnector scmConnector) {
    FileContentBatchResponse fileBatchContentResponse;
    if (gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH) {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(c
          -> scmServiceClient.listFilesV2(
              scmConnector, filePaths, gitStoreDelegateConfig.getBranch(), SCMGrpc.newBlockingStub(c)));
    } else {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(c
          -> scmServiceClient.listFoldersFilesByCommitId(
              scmConnector, filePaths, gitStoreDelegateConfig.getCommitId(), SCMGrpc.newBlockingStub(c)));
    }
    return fileBatchContentResponse;
  }

  // GitOps methods
  public CreatePRResponse createPR(ScmConnector scmConnector, GitPRCreateRequest gitPRCreateRequest) {
    CreatePRResponse createPRResponse = scmDelegateClient.processScmRequest(
        c -> scmServiceClient.createPullRequest(scmConnector, gitPRCreateRequest, SCMGrpc.newBlockingStub(c)));
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          createPRResponse.getStatus(), createPRResponse.getError());
    } catch (WingsException e) {
      throw new ExplanationException(String.format("Could not create the pull request from %s to %s",
                                         gitPRCreateRequest.getSourceBranch(), gitPRCreateRequest.getTargetBranch()),
          e);
    }
    return createPRResponse;
  }

  public CreateBranchResponse createNewBranch(ScmConnector scmConnector, String branch, String baseBranch) {
    CreateBranchResponse createBranchResponse = scmDelegateClient.processScmRequest(
        c -> scmServiceClient.createNewBranch(scmConnector, branch, baseBranch, SCMGrpc.newBlockingStub(c)));
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          createBranchResponse.getStatus(), createBranchResponse.getError());
    } catch (WingsException e) {
      throw new ExplanationException(String.format("Could not create a new branch %s from %s", branch, baseBranch), e);
    }
    return createBranchResponse;
  }
}
