/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmCreateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitResponse;
import io.harness.persistence.gitaware.GitAware;

import com.google.inject.Inject;
import groovy.lang.Singleton;
import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class GitAwareEntityHelper {
  @Inject SCMGitSyncHelper scmGitSyncHelper;
  private static final String DEFAULT = "__default__";

  public GitAware fetchEntityFromRemote(
      GitAware entity, Scope scope, GitContextRequestParams gitContextRequestParams, Map<String, String> contextMap) {
    String repoName = gitContextRequestParams.getRepoName();
    // if branch is empty, then git sdk will figure out the default branch for the repo by itself
    String branch =
        isNullOrDefault(gitContextRequestParams.getBranchName()) ? "" : gitContextRequestParams.getBranchName();
    String filePath = gitContextRequestParams.getFilePath();
    String connectorRef = gitContextRequestParams.getConnectorRef();
    ScmGetFileResponse scmGetFileResponse =
        scmGitSyncHelper.getFileByBranch(Scope.builder()
                                             .accountIdentifier(scope.getAccountIdentifier())
                                             .orgIdentifier(scope.getOrgIdentifier())
                                             .projectIdentifier(scope.getProjectIdentifier())
                                             .build(),
            repoName, branch, filePath, connectorRef, contextMap);
    entity.setData(scmGetFileResponse.getFileContent());
    GitAwareContextHelper.updateScmGitMetaData(scmGetFileResponse.getGitMetaData());
    return entity;
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
    String filePath = gitContextRequestParams.getFilePath();
    if (isNullOrDefault(filePath)) {
      throw new InvalidRequestException("No file path provided.");
    }
    String connectorRef = gitContextRequestParams.getConnectorRef();
    if (isNullOrDefault(connectorRef)) {
      throw new InvalidRequestException("No Connector reference provided.");
    }
    ScmGetFileResponse scmGetFileResponse =
        scmGitSyncHelper.getFileByBranch(Scope.builder()
                                             .accountIdentifier(scope.getAccountIdentifier())
                                             .orgIdentifier(scope.getOrgIdentifier())
                                             .projectIdentifier(scope.getProjectIdentifier())
                                             .build(),
            repoName, branch, filePath, connectorRef, contextMap);
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
    String branch = isNullOrDefault(gitEntityInfo.getBranch()) ? "" : gitEntityInfo.getBranch();
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

  private boolean isNullOrDefault(String val) {
    return EmptyPredicate.isEmpty(val) || val.equals(DEFAULT);
  }
}
