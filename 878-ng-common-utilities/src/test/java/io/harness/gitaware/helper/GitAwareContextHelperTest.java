/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitAwareContextHelperTest extends CategoryTest {
  private static final String CommitId = "commitId";
  private static final String FilePath = "filePath";
  private static final String BranchName = "branch";
  private static final String RepoName = "repo";
  private static final String ObjectId = "objID";

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testScmGitMetaDataUpdateAndGet() {
    ScmGitMetaData scmGitMetaData = ScmGitMetaData.builder().commitId(CommitId).filePath(FilePath).build();
    GitAwareContextHelper.updateScmGitMetaData(scmGitMetaData);
    ScmGitMetaData scmGitMetaDataFetched = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataFetched).isNotNull();
    assertThat(scmGitMetaDataFetched.getFilePath()).isEqualTo(FilePath);
    assertThat(scmGitMetaDataFetched.getCommitId()).isEqualTo(CommitId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testScmGitMetaDataNotFound() {
    ScmGitMetaData scmGitMetaData = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaData.getRepoName()).isNull();
    assertThat(scmGitMetaData.getBranchName()).isNull();
    assertThat(scmGitMetaData.getCommitId()).isNull();
    assertThat(scmGitMetaData.getFilePath()).isNull();
    assertThat(scmGitMetaData.getBlobId()).isNull();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void initDefaultScmGitMetaDataAndRequestParams() {
    GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
    ScmGitMetaData scmGitMetaDataFetched = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataFetched).isNotNull();
    assertThat(scmGitMetaDataFetched.getFilePath()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getCommitId()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getBlobId()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getBranchName()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getRepoName()).isEqualTo(null);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitEntityInfo).isNotNull();
    assertThat(gitEntityInfo.isNull()).isEqualTo(true);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testInitScmGitMetaData() {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    ScmGitMetaData scmGitMetaDataFetched = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataFetched).isNotNull();
    assertThat(scmGitMetaDataFetched.getFilePath()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getCommitId()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getBlobId()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getBranchName()).isEqualTo(null);
    assertThat(scmGitMetaDataFetched.getRepoName()).isEqualTo(null);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityGitDetailsFromScmGitMetadata() {
    ScmGitMetaData scmGitMetaData = ScmGitMetaData.builder()
                                        .branchName(BranchName)
                                        .repoName(RepoName)
                                        .commitId(CommitId)
                                        .filePath(FilePath)
                                        .blobId(ObjectId)
                                        .build();
    GitAwareContextHelper.updateScmGitMetaData(scmGitMetaData);

    EntityGitDetails entityGitDetails = GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata();
    assertThat(entityGitDetails.getRepoName()).isEqualTo(RepoName);
    assertThat(entityGitDetails.getBranch()).isEqualTo(BranchName);
    assertThat(entityGitDetails.getFilePath()).isEqualTo(FilePath);
    assertThat(entityGitDetails.getCommitId()).isEqualTo(CommitId);
    assertThat(entityGitDetails.getObjectId()).isEqualTo(ObjectId);
    assertThat(entityGitDetails.getRepoIdentifier()).isNull();
    assertThat(entityGitDetails.getRootFolder()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetBranchInRequest() {
    assertThat(GitAwareContextHelper.getBranchInRequest()).isNull();
    GitEntityInfo branchInfo = GitEntityInfo.builder().branch(BranchName).build();
    setupGitContext(branchInfo);
    assertThat(GitAwareContextHelper.getBranchInRequest()).isEqualTo(BranchName);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFilepathInRequest() {
    assertThat(GitAwareContextHelper.getFilepathInRequest()).isNull();
    GitEntityInfo branchInfo = GitEntityInfo.builder().filePath(FilePath).build();
    setupGitContext(branchInfo);
    assertThat(GitAwareContextHelper.getFilepathInRequest()).isEqualTo(FilePath);
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}