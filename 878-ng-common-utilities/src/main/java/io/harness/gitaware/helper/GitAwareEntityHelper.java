/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.gitaware.dto.FetchRemoteEntityRequest;
import io.harness.gitaware.dto.GetFileGitContextRequestParams;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitsync.common.beans.GitOperation;
import io.harness.gitsync.common.helper.GitSyncLogContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmCreateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.scm.beans.ScmGetBatchFileRequest;
import io.harness.gitsync.scm.beans.ScmGetBatchFilesResponse;
import io.harness.gitsync.scm.beans.ScmGetFileRequest;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitResponse;
import io.harness.persistence.gitaware.GitAware;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import groovy.lang.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PL)
@Log4j
@Singleton
public class GitAwareEntityHelper {
  @Inject SCMGitSyncHelper scmGitSyncHelper;
  public static final String DEFAULT = "__default__";
  public static final String HARNESS_FOLDER_EXTENSION_WITH_SEPARATOR = ".harness/";
  public static final String FILE_PATH_INVALID_HINT = "Please check if the requested filepath is valid.";
  public static final String FILE_PATH_INVALID_EXTENSION_EXPLANATION =
      "Harness File should have [.yaml] or [.yml] extension.";
  public static final String READ_ME_FILE_PATH_INVALID_EXTENSION_EXPLANATION = "Harness File should have [.md].";

  public static final String FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT = "FilePath [%s] doesn't have right extension.";

  public GitAware fetchEntityFromRemote(
      GitAware entity, Scope scope, GitContextRequestParams gitContextRequestParams, Map<String, String> contextMap) {
    String repoName = gitContextRequestParams.getRepoName();
    // if branch is empty, then git sdk will figure out the default branch for the repo by itself
    String branch =
        isNullOrDefault(gitContextRequestParams.getBranchName()) ? "" : gitContextRequestParams.getBranchName();
    String commitId =
        isNullOrDefault(gitContextRequestParams.getCommitId()) ? "" : gitContextRequestParams.getCommitId();

    GitAwareContextHelper.setIsDefaultBranchInGitEntityInfoWithParameter(branch);

    String filePath = gitContextRequestParams.getFilePath();
    if (isNullOrDefault(filePath)) {
      throw new InvalidRequestException("No file path provided.");
    }
    validateFilePathHasCorrectExtension(filePath);
    String connectorRef = gitContextRequestParams.getConnectorRef();
    boolean loadFromCache = gitContextRequestParams.isLoadFromCache();
    EntityType entityType = gitContextRequestParams.getEntityType();
    boolean getFileContentOnly = gitContextRequestParams.isGetOnlyFileContent();

    log.info(String.format("Fetching Remote Entity : %s , %s , %s , %s", entityType, repoName, branch, filePath));
    ScmGetFileResponse scmGetFileResponse =
        scmGitSyncHelper.getFileByBranch(Scope.builder()
                                             .accountIdentifier(scope.getAccountIdentifier())
                                             .orgIdentifier(scope.getOrgIdentifier())
                                             .projectIdentifier(scope.getProjectIdentifier())
                                             .build(),
            repoName, branch, commitId, filePath, connectorRef, loadFromCache, entityType, contextMap,
            getFileContentOnly, gitContextRequestParams.isApplyRepoAllowListFilter());
    entity.setData(scmGetFileResponse.getFileContent());
    GitAwareContextHelper.updateScmGitMetaData(scmGetFileResponse.getGitMetaData());
    return entity;
  }

  // todo: make pipeline import call this method too
  public String fetchYAMLFromRemote(
      String accountId, String orgIdentifier, String projectIdentifier, boolean applyRepoAllowListFilter) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = Scope.of(accountId, orgIdentifier, projectIdentifier);
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .applyRepoAllowListFilter(applyRepoAllowListFilter)
                                                          .build();
    return fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public String fetchYAMLFromRemote(
      Scope scope, GitContextRequestParams gitContextRequestParams, Map<String, String> contextMap) {
    String repoName = gitContextRequestParams.getRepoName();
    if (isNullOrDefault(repoName)) {
      throw new InvalidRequestException("No Repo Name provided.");
    }
    // if branch is empty, then git sdk will figure out the default branch for the repo by itself
    String branch =
        isNullOrDefault(gitContextRequestParams.getBranchName()) ? "" : gitContextRequestParams.getBranchName();
    String commitId =
        isNullOrDefault(gitContextRequestParams.getCommitId()) ? "" : gitContextRequestParams.getCommitId();
    String filePath = gitContextRequestParams.getFilePath();
    if (isNullOrDefault(filePath)) {
      throw new InvalidRequestException("No file path provided.");
    }
    String connectorRef = gitContextRequestParams.getConnectorRef();
    if (isNullOrDefault(connectorRef)) {
      throw new InvalidRequestException("No Connector reference provided.");
    }
    boolean loadFromCache = gitContextRequestParams.isLoadFromCache();
    EntityType entityType = gitContextRequestParams.getEntityType();
    ScmGetFileResponse scmGetFileResponse =
        scmGitSyncHelper.getFileByBranch(Scope.builder()
                                             .accountIdentifier(scope.getAccountIdentifier())
                                             .orgIdentifier(scope.getOrgIdentifier())
                                             .projectIdentifier(scope.getProjectIdentifier())
                                             .build(),
            repoName, branch, commitId, filePath, connectorRef, loadFromCache, entityType, contextMap, false,
            gitContextRequestParams.isApplyRepoAllowListFilter());
    GitAwareContextHelper.updateScmGitMetaData(scmGetFileResponse.getGitMetaData());
    return scmGetFileResponse.getFileContent();
  }

  public ScmCreateFileGitResponse createEntityOnGit(GitAware gitAwareEntity, String yaml, Scope scope) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoName = gitAwareEntity.getRepo();
    if (isNullOrDefault(repoName)) {
      throw new InvalidRequestException("No repo name provided.");
    }
    String filePath = gitAwareEntity.getFilePath();
    if (isNullOrDefault(filePath)) {
      throw new InvalidRequestException("No file path provided.");
    }
    String connectorRef = gitAwareEntity.getConnectorRef();
    if (isNullOrDefault(connectorRef)) {
      throw new InvalidRequestException("No Connector reference provided.");
    }
    String baseBranch = gitEntityInfo.getBaseBranch();
    if (gitEntityInfo.isNewBranch() && isNullOrDefault(baseBranch)) {
      throw new InvalidRequestException("No base branch provided for committing to new branch");
    }
    validateFilePathHasCorrectExtension(filePath);
    // if branch is empty, then git sdk will figure out the default branch for the repo by itself
    String branch = isNullOrDefault(gitEntityInfo.getBranch()) ? "" : gitEntityInfo.getBranch();
    // if commitMsg is empty, then git sdk will use some default Commit Message
    String commitMsg = isNullOrDefault(gitEntityInfo.getCommitMsg()) ? "" : gitEntityInfo.getCommitMsg();
    ScmCreateFileGitRequest scmCreateFileGitRequest = ScmCreateFileGitRequest.builder()
                                                          .repoName(repoName)
                                                          .branchName(branch)
                                                          .fileContent(yaml)
                                                          .filePath(filePath)
                                                          .connectorRef(connectorRef)
                                                          .isCommitToNewBranch(gitEntityInfo.isNewBranch())
                                                          .commitMessage(commitMsg)
                                                          .baseBranch(baseBranch)
                                                          .build();

    ScmCreateFileGitResponse scmCreateFileGitResponse =
        scmGitSyncHelper.createFile(scope, scmCreateFileGitRequest, Collections.emptyMap());
    GitAwareContextHelper.updateScmGitMetaData(scmCreateFileGitResponse.getGitMetaData());
    return scmCreateFileGitResponse;
  }

  public ScmUpdateFileGitResponse updateEntityOnGit(GitAware gitAwareEntity, String yaml, Scope scope) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    return updateEntityOnGit(
        gitAwareEntity, yaml, scope, gitEntityInfo.getLastObjectId(), gitEntityInfo.getLastCommitId());
  }

  public ScmUpdateFileGitResponse updateFileImportedFromGit(GitAware gitAwareEntity, String yaml, Scope scope) {
    ScmGitMetaData scmGitMetaData = GitAwareContextHelper.getScmGitMetaData();
    return updateEntityOnGit(gitAwareEntity, yaml, scope, scmGitMetaData.getBlobId(), scmGitMetaData.getCommitId());
  }

  ScmUpdateFileGitResponse updateEntityOnGit(
      GitAware gitAwareEntity, String yaml, Scope scope, String oldFileSHA, String oldCommitID) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoName = gitAwareEntity.getRepo();
    if (isNullOrDefault(repoName)) {
      throw new InvalidRequestException("No repo name provided.");
    }
    String filePath = gitAwareEntity.getFilePath();
    if (isNullOrDefault(filePath)) {
      throw new InvalidRequestException("No file path provided.");
    }
    String connectorRef = gitAwareEntity.getConnectorRef();
    if (isNullOrDefault(connectorRef)) {
      throw new InvalidRequestException("No Connector reference provided.");
    }
    String baseBranch = gitEntityInfo.getBaseBranch();
    if (gitEntityInfo.isNewBranch() && isNullOrDefault(baseBranch)) {
      throw new InvalidRequestException("No base branch provided for committing to new branch");
    }
    // if branch is empty, then git sdk will figure out the default branch for the repo by itself
    String branch = gitEntityInfo.getBranch();
    if (isNullOrDefault(branch)) {
      throw new InvalidRequestException("No branch provided for updating the file.");
    }
    validateFilePathHasCorrectExtension(filePath);
    // if commitMsg is empty, then git sdk will use some default Commit Message
    String commitMsg = isNullOrDefault(gitEntityInfo.getCommitMsg()) ? "" : gitEntityInfo.getCommitMsg();
    ScmUpdateFileGitRequest scmUpdateFileGitRequest = ScmUpdateFileGitRequest.builder()
                                                          .repoName(repoName)
                                                          .branchName(branch)
                                                          .fileContent(yaml)
                                                          .filePath(filePath)
                                                          .connectorRef(connectorRef)
                                                          .isCommitToNewBranch(gitEntityInfo.isNewBranch())
                                                          .commitMessage(commitMsg)
                                                          .baseBranch(baseBranch)
                                                          .oldFileSha(oldFileSHA)
                                                          .oldCommitId(oldCommitID)
                                                          .build();

    ScmUpdateFileGitResponse scmUpdateFileGitResponse =
        scmGitSyncHelper.updateFile(scope, scmUpdateFileGitRequest, Collections.emptyMap());
    GitAwareContextHelper.updateScmGitMetaData(scmUpdateFileGitResponse.getGitMetaData());
    return scmUpdateFileGitResponse;
  }

  public Map<String, GitAware> fetchEntitiesFromRemote(
      String accountIdentifier, Map<String, FetchRemoteEntityRequest> remoteTemplatesList) {
    ScmGetBatchFileRequest scmGetBatchFileRequest = prepareScmGetBatchFilesRequest(remoteTemplatesList, false);
    ScmGetBatchFilesResponse scmGetBatchFilesResponse =
        scmGitSyncHelper.getBatchFilesByBranch(accountIdentifier, scmGetBatchFileRequest);
    return processScmGetBatchFiles(scmGetBatchFilesResponse.getBatchFilesResponse(), remoteTemplatesList);
  }

  public ScmGetBatchFilesResponse fetchEntitiesFromRemoteIncludingReadMeFile(
      String accountIdentifier, Map<String, FetchRemoteEntityRequest> remoteTemplatesList) {
    ScmGetBatchFileRequest scmGetBatchFileRequest = prepareScmGetBatchFilesRequest(remoteTemplatesList, true);
    return scmGitSyncHelper.getBatchFilesByBranch(accountIdentifier, scmGetBatchFileRequest);
  }

  private ScmGetBatchFileRequest prepareScmGetBatchFilesRequest(
      Map<String, FetchRemoteEntityRequest> remoteTemplatesList, boolean fetchReadMeFile) {
    Map<String, ScmGetFileRequest> scmGetBatchFilesRequestMap = new HashMap<>();

    for (Map.Entry<String, FetchRemoteEntityRequest> remoteTemplateRequestEntry : remoteTemplatesList.entrySet()) {
      GetFileGitContextRequestParams getFileGitContextRequestParams =
          remoteTemplateRequestEntry.getValue().getGetFileGitContextRequestParams();
      Scope scope = remoteTemplateRequestEntry.getValue().getScope();
      Map<String, String> contextMap = remoteTemplateRequestEntry.getValue().getContextMap();
      String repoName = getFileGitContextRequestParams.getRepoName();

      String branchName = isNullOrDefault(getFileGitContextRequestParams.getBranchName())
          ? ""
          : getFileGitContextRequestParams.getBranchName();

      String commitId = isNullOrDefault(getFileGitContextRequestParams.getCommitId())
          ? ""
          : getFileGitContextRequestParams.getCommitId();
      String filePath = getFileGitContextRequestParams.getFilePath();
      if (isNullOrDefault(filePath)) {
        throw new InvalidRequestException("No file path provided.");
      }
      if (!fetchReadMeFile) {
        validateFilePathHasCorrectExtension(filePath);
      } else {
        validateFilePathHasCorrectExtensionWithReadMe(filePath);
      }
      String connectorRef = getFileGitContextRequestParams.getConnectorRef();
      boolean loadFromCache = getFileGitContextRequestParams.isLoadFromCache();
      EntityType entityType = getFileGitContextRequestParams.getEntityType();
      boolean getOnlyFileContent = getFileGitContextRequestParams.isGetOnlyFileContent();
      contextMap = GitSyncLogContextHelper.setContextMap(
          scope, repoName, branchName, commitId, filePath, GitOperation.GET_FILE, contextMap);

      ScmGetFileRequest scmGetFileRequest = ScmGetFileRequest.builder()
                                                .scope(scope)
                                                .repoName(repoName)
                                                .branchName(branchName)
                                                .filePath(filePath)
                                                .connectorRef(connectorRef)
                                                .loadFromCache(loadFromCache)
                                                .entityType(entityType)
                                                .contextMap(contextMap)
                                                .getOnlyFileContent(getOnlyFileContent)
                                                .build();

      scmGetBatchFilesRequestMap.put(remoteTemplateRequestEntry.getKey(), scmGetFileRequest);
    }
    return ScmGetBatchFileRequest.builder().scmGetBatchFilesRequestMap(scmGetBatchFilesRequestMap).build();
  }

  private Map<String, GitAware> processScmGetBatchFiles(Map<String, ScmGetFileResponse> getBatchFilesResponse,
      Map<String, FetchRemoteEntityRequest> remoteTemplatesList) {
    Map<String, GitAware> batchFilesResponse = new HashMap<>();

    getBatchFilesResponse.forEach((identifier, scmGetFileResponse) -> {
      GitAware gitAwareEntity = remoteTemplatesList.get(identifier).getEntity();
      gitAwareEntity.setData(scmGetFileResponse.getFileContent());
      batchFilesResponse.put(identifier, gitAwareEntity);
    });
    return batchFilesResponse;
  }
  private boolean isNullOrDefault(String val) {
    return isEmpty(val) || val.equals(DEFAULT);
  }

  public String getRepoUrl(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    return scmGitSyncHelper
        .getRepoUrl(scope, gitEntityInfo.getRepoName(), gitEntityInfo.getConnectorRef(), Collections.emptyMap())
        .getRepoUrl();
  }

  @VisibleForTesting
  void validateFilePathHasCorrectExtension(String filePath) {
    if (!filePath.endsWith(".yaml") && !filePath.endsWith(".yml")) {
      throw NestedExceptionUtils.hintWithExplanationException(FILE_PATH_INVALID_HINT,
          FILE_PATH_INVALID_EXTENSION_EXPLANATION,
          new InvalidRequestException(String.format(FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT, filePath)));
    }
  }

  // Allowing README.md file
  @VisibleForTesting
  void validateFilePathHasCorrectExtensionWithReadMe(String filePath) {
    if (!filePath.endsWith(".yaml") && !filePath.endsWith(".yml") && !filePath.endsWith(".md")) {
      throw NestedExceptionUtils.hintWithExplanationException(FILE_PATH_INVALID_HINT,
          FILE_PATH_INVALID_EXTENSION_EXPLANATION,
          new InvalidRequestException(String.format(FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT, filePath)));
    }
  }

  public String getWorkingBranch(String repoName) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    // If there's static branch present for any template, then it takes highest precedence
    if (GitAwareContextHelper.isTransientBranchSet()) {
      return gitEntityInfo.getTransientBranch();
    }
    String branchName = gitEntityInfo.getBranch();
    if (gitEntityInfo.isNewBranch()) {
      branchName = gitEntityInfo.getBaseBranch();
    }
    if (isNullOrDefault(gitEntityInfo.getParentEntityRepoName())) {
      return branchName;
    }
    return gitEntityInfo.getParentEntityRepoName().equals(repoName) ? branchName : "";
  }

  public void validateRepo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repo) {
    scmGitSyncHelper.validateRepo(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repo);
  }
}
