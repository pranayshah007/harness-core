/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.ScmApis;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.common.scmerrorhandling.handlers.DefaultScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoCreateBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoCreateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoCreatePullRequestScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoGetBranchHeadCommitScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoGetDefaultBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoGetFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoListBranchesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoListRepoScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ado.AdoUpdateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketCreateBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketCreateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketCreatePullRequestScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketGetBranchHeadCommitScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketGetDefaultBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketGetFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketListBranchesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketListFilesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketListRepoScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketUpdateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerCreateBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerCreateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerCreatePullRequestScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerGetBranchHeadCommitScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerGetDefaultBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerGetFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerListBranchesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerListFilesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerListRepoScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerUpdateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubCreateBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubCreateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubCreatePullRequestScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubGetBranchHeadCommitScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubGetDefaultBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubGetFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubListBranchesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubListFilesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubListRepoScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubUpdateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabCreateBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabCreateFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabCreatePullRequestScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabGetBranchHeadCommitScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabGetDefaultBranchScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabGetFileScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabListBranchesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabListFilesScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabListRepoScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.GitlabUpdateFileScmApiErrorHandler;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
@Slf4j
@OwnedBy(PL)
class ScmApiErrorHandlerFactory {
  private final Map<Pair<ScmApis, RepoProviders>, Class<? extends ScmApiErrorHandler>> scmApiErrorHandlerMap =
      ImmutableMap
          .<Pair<ScmApis, RepoProviders>, Class<? extends ScmApiErrorHandler>>builder()
          // List Repository Handlers
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.BITBUCKET), BitbucketListRepoScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.GITHUB), GithubListRepoScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerListRepoScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.AZURE), AdoListRepoScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.GITLAB), GitlabListRepoScmApiErrorHandler.class)

          // Get File Handlers
          .put(Pair.of(ScmApis.GET_FILE, RepoProviders.BITBUCKET), BitbucketGetFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_FILE, RepoProviders.GITHUB), GithubGetFileScmApiErrorHandler.class)
          .put(
              Pair.of(ScmApis.GET_FILE, RepoProviders.BITBUCKET_SERVER), BitbucketServerGetFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_FILE, RepoProviders.AZURE), AdoGetFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_FILE, RepoProviders.GITLAB), GitlabGetFileScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.BITBUCKET),
              BitbucketCreatePullRequestScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.GITHUB),
              GithubCreatePullRequestScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerCreatePullRequestScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.AZURE), AdoCreatePullRequestScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.GITLAB),
              GitlabCreatePullRequestScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.BITBUCKET), BitbucketCreateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.GITHUB), GithubCreateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerCreateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.AZURE), AdoCreateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.GITLAB), GitlabCreateFileScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.BITBUCKET), BitbucketUpdateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.GITHUB), GithubUpdateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerUpdateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.AZURE), AdoUpdateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.GITLAB), GitlabUpdateFileScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.BITBUCKET), BitbucketCreateBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.GITHUB), GithubCreateBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerCreateBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.AZURE), AdoCreateBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.GITLAB), GitlabCreateBranchScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.BITBUCKET), BitbucketListBranchesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.GITHUB), GithubListBranchesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerListBranchesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.AZURE), AdoListBranchesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.GITLAB), GitlabListBranchesScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.BITBUCKET),
              BitbucketGetDefaultBranchScmApiErrorHandler.class)
          .put(
              Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.GITHUB), GithubGetDefaultBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerGetDefaultBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.AZURE), AdoGetDefaultBranchScmApiErrorHandler.class)
          .put(
              Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.GITLAB), GitlabGetDefaultBranchScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.BITBUCKET),
              BitbucketGetBranchHeadCommitScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.GITHUB),
              GithubGetBranchHeadCommitScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerGetBranchHeadCommitScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.AZURE),
              AdoGetBranchHeadCommitScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.GITLAB),
              GitlabGetBranchHeadCommitScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.LIST_FILES, RepoProviders.BITBUCKET), BitbucketListFilesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_FILES, RepoProviders.GITHUB), GithubListFilesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_FILES, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerListFilesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_FILES, RepoProviders.GITLAB), GitlabListFilesScmApiErrorHandler.class)
          .build();

  public ScmApiErrorHandler getHandler(ScmApis scmApi, RepoProviders repoProvider) {
    try {
      return scmApiErrorHandlerMap.get(Pair.of(scmApi, repoProvider)).newInstance();
    } catch (Exception ex) {
      log.error(
          String.format("Error while getting handler for scmApi [%s] and repoProvider [%s]", scmApi, repoProvider), ex);
    }
    return new DefaultScmApiErrorHandler();
  }
}