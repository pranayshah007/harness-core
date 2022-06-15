/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.scm.beans.ScmGitMetaDataContext;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.persistence.gitaware.GitAware;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DX)
public class GitAwareContextHelper {
  public GitEntityInfo getGitRequestParamsInfo() {
    final GitSyncBranchContext gitSyncBranchContext =
        GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
    if (gitSyncBranchContext == null) {
      return GitEntityInfo.builder().build();
    }
    return gitSyncBranchContext.getGitBranchInfo();
  }

  public void initDefaultScmGitMetaData() {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(
        ScmGitMetaDataContext.builder().scmGitMetaData(ScmGitMetaData.builder().build()).build());
  }

  public ScmGitMetaData getScmGitMetaData() {
    ScmGitMetaDataContext gitMetaDataContext = GlobalContextManager.get(ScmGitMetaDataContext.NG_GIT_SYNC_CONTEXT);
    if (gitMetaDataContext == null) {
      return ScmGitMetaData.builder().build();
    }
    return gitMetaDataContext.getScmGitMetaData();
  }

  public void updateScmGitMetaData(ScmGitMetaData scmGitMetaData) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(
        ScmGitMetaDataContext.builder().scmGitMetaData(scmGitMetaData).build());
  }

  public boolean isOldFlow() {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    return gitEntityInfo == null || gitEntityInfo.getStoreType() == null;
  }

  public EntityGitDetails getEntityGitDetailsFromScmGitMetadata() {
    ScmGitMetaData scmGitMetaData = getScmGitMetaData();
    if (scmGitMetaData == null) {
      return EntityGitDetails.builder().build();
    }
    return EntityGitDetails.builder()
        .objectId(scmGitMetaData.getBlobId())
        .branch(scmGitMetaData.getBranchName())
        .repoName(scmGitMetaData.getRepoName())
        .filePath(scmGitMetaData.getFilePath())
        .commitId(scmGitMetaData.getCommitId())
        .fileUrl(scmGitMetaData.getFileUrl())
        .build();
  }

  public EntityGitDetails getEntityGitDetails(GitAware gitAware) {
    return EntityGitDetails.builder().repoName(gitAware.getRepo()).filePath(gitAware.getFilePath()).build();
  }

  public String getBranchInRequest() {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    return gitEntityInfo == null ? "" : gitEntityInfo.getBranch();
  }

  public String getFilepathInRequest() {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    return gitEntityInfo == null ? "" : gitEntityInfo.getFilePath();
  }
}
