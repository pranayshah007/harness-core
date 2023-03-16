/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.REPO;
import static io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType.USERNAME_AND_TOKEN;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.TOKEN;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.GitConfig.ProviderType.BITBUCKET;
import static software.wings.beans.GitConfig.ProviderType.GITHUB;
import static software.wings.beans.GitConfig.ProviderType.GITLAB;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileContentBatchResponse;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.task.git.ScmFetcherUtils;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GitClientException;
import io.harness.exception.YamlException;
import io.harness.git.GitFetchMetadataLocalThread;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.service.ScmServiceClient;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.yaml.GitCommitResult;
import software.wings.beans.yaml.GitFetchFilesResult;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Slf4j
public class ScmFetchFilesHelper {
  @Inject private ScmDelegateClient scmDelegateClient;
  @Inject private ScmServiceClient scmServiceClient;
  private static final List<String> ROOT_DIRECTORY_PATHS = Arrays.asList(".", "/");
  private static final String DEFAULT_FETCH_IDENTIFIER = "--default";
  public GitFetchFilesResult fetchFilesFromRepoWithScm(
      GitFileConfig gitFileConfig, GitConfig gitConfig, List<String> filePathList) {
    return fetchFilesFromRepoWithScm(DEFAULT_FETCH_IDENTIFIER, gitFileConfig, gitConfig, filePathList);
  }
  public GitFetchFilesResult fetchFilesFromRepoWithScm(
      String identifier, GitFileConfig gitFileConfig, GitConfig gitConfig, List<String> filePathList) {
    ScmConnector scmConnector = getScmConnector(gitConfig);
    FileContentBatchResponse fileBatchContentResponse;

    fileBatchContentResponse = fetchFilesByFilePaths(gitFileConfig, filePathList, scmConnector);
    GitFetchMetadataLocalThread.putCommitId(identifier, fileBatchContentResponse.getCommitId());
    List<GitFile> gitFiles =
        fileBatchContentResponse.getFileBatchContentResponse()
            .getFileContentsList()
            .stream()
            .filter(fileContent -> {
              if (fileContent.getStatus() != 200 || isNotEmpty(fileContent.getError())) {
                throwFailedToFetchFileException(gitFileConfig, fileContent);
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
    return GitFetchFilesResult.builder()
        .files(gitFiles)
        .gitCommitResult(GitCommitResult.builder()
                             .commitId(gitFileConfig.isUseBranch() ? "latest" : fileBatchContentResponse.getCommitId())
                             .build())
        .build();
  }

  private FileContentBatchResponse fetchFilesByFilePaths(
      GitFileConfig gitFileConfig, List<String> filePathList, ScmConnector scmConnector) {
    FileContentBatchResponse fileBatchContentResponse;
    if (gitFileConfig.isUseBranch()) {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(c
          -> scmServiceClient.listFilesByFilePaths(
              scmConnector, filePathList, gitFileConfig.getBranch(), SCMGrpc.newBlockingStub(c)));
    } else {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(c
          -> scmServiceClient.listFilesByCommitId(
              scmConnector, filePathList, gitFileConfig.getCommitId(), SCMGrpc.newBlockingStub(c)));
    }
    return fileBatchContentResponse;
  }

  public void downloadFilesUsingScm(String manifestFilesDirectory, GitFileConfig gitFileConfig, GitConfig gitConfig,
      LogCallback executionLogCallback) {
    String directoryPath = Paths.get(manifestFilesDirectory).toString();
    ScmConnector scmConnector = getScmConnector(gitConfig);

    FileContentBatchResponse fileBatchContentResponse =
        getFileContentBatchResponseByFolder(gitFileConfig, scmConnector);

    boolean relativize = !ROOT_DIRECTORY_PATHS.contains(gitFileConfig.getFilePath());
    boolean useBase64 = true;
    if (isEmpty(fileBatchContentResponse.getFileBatchContentResponse().getFileContentsList())) {
      fileBatchContentResponse = getFileContentBatchResponseByFilePath(gitFileConfig, scmConnector);
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
              .append(gitFileConfig.isUseBranch() ? "for Branch: " : "for CommitId: ")
              .append(gitFileConfig.isUseBranch() ? gitFileConfig.getBranch() : gitFileConfig.getCommitId())
              .append(", FilePaths: ")
              .append(gitFileConfig.getFilePath())
              .append(". Reason: File not found")
              .toString(),
          USER);
    }

    try {
      for (FileContent fileContent : fileContents) {
        ScmFetcherUtils.writeFile(directoryPath, fileContent, gitFileConfig.getFilePath(), relativize, useBase64);
      }
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(ex), ERROR, CommandExecutionStatus.FAILURE);
    }
  }

  public boolean shouldUseScm(boolean isOptimizedFilesFetch, GitConfig gitConfig) {
    return isOptimizedFilesFetch && gitConfig.getSshSettingAttribute() == null
        && Arrays.asList(GITHUB, GITLAB, BITBUCKET).contains(gitConfig.getProviderType());
  }

  public ScmConnector getScmConnector(GitConfig gitConfig) {
    switch (gitConfig.getProviderType()) {
      case GITHUB:
        return getGitHubConnector(gitConfig);
      case GITLAB:
        return getGitLabConnector(gitConfig);
      case BITBUCKET:
        return getBitbucketConnector(gitConfig);
      default:
        return null;
    }
  }

  private GithubConnectorDTO getGitHubConnector(GitConfig gitConfig) {
    return GithubConnectorDTO.builder()
        .url(gitConfig.getRepoUrl())
        .connectionType(REPO)
        .apiAccess(GithubApiAccessDTO.builder()
                       .type(TOKEN)
                       .spec(GithubTokenSpecDTO.builder()
                                 .tokenRef(SecretRefData.builder().decryptedValue(gitConfig.getPassword()).build())
                                 .build())
                       .build())
        .build();
  }

  private GitlabConnectorDTO getGitLabConnector(GitConfig gitConfig) {
    return GitlabConnectorDTO.builder()
        .url(gitConfig.getRepoUrl())
        .connectionType(REPO)
        .apiAccess(GitlabApiAccessDTO.builder()
                       .type(GitlabApiAccessType.TOKEN)
                       .spec(GitlabTokenSpecDTO.builder()
                                 .tokenRef(SecretRefData.builder().decryptedValue(gitConfig.getPassword()).build())
                                 .build())
                       .build())
        .build();
  }

  private BitbucketConnectorDTO getBitbucketConnector(GitConfig gitConfig) {
    return BitbucketConnectorDTO.builder()
        .url(gitConfig.getRepoUrl())
        .connectionType(REPO)
        .apiAccess(
            BitbucketApiAccessDTO.builder()
                .type(USERNAME_AND_TOKEN)
                .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                          .usernameRef(
                              SecretRefData.builder().decryptedValue((gitConfig.getUsername()).toCharArray()).build())
                          .tokenRef(SecretRefData.builder().decryptedValue(gitConfig.getPassword()).build())
                          .build())
                .build())
        .build();
  }

  private void throwFailedToFetchFileException(GitFileConfig gitFileConfig, FileContent fileContent) {
    throw new GitClientException(
        new StringBuilder("Unable to fetch files for filePath [")
            .append(fileContent.getPath())
            .append("]")
            .append(gitFileConfig.isUseBranch() ? " for Branch: " : " for CommitId: ")
            .append(gitFileConfig.isUseBranch() ? gitFileConfig.getBranch() : gitFileConfig.getCommitId())
            .toString(),
        USER, new NoSuchFileException(fileContent.getPath()));
  }

  private FileContentBatchResponse getFileContentBatchResponseByFilePath(
      GitFileConfig gitFileConfig, ScmConnector scmConnector) {
    return fetchFilesByFilePaths(gitFileConfig, Collections.singletonList(gitFileConfig.getFilePath()), scmConnector);
  }

  private FileContentBatchResponse getFileContentBatchResponseByFolder(
      GitFileConfig gitFileConfig, ScmConnector scmConnector) {
    FileContentBatchResponse fileBatchContentResponse;
    if (gitFileConfig.isUseBranch()) {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(c
          -> scmServiceClient.listFilesV2(scmConnector, Collections.singleton(gitFileConfig.getFilePath()),
              gitFileConfig.getBranch(), SCMGrpc.newBlockingStub(c)));
    } else {
      fileBatchContentResponse = scmDelegateClient.processScmRequest(c
          -> scmServiceClient.listFoldersFilesByCommitId(scmConnector,
              Collections.singleton(gitFileConfig.getFilePath()), gitFileConfig.getCommitId(),
              SCMGrpc.newBlockingStub(c)));
    }
    return fileBatchContentResponse;
  }
}
