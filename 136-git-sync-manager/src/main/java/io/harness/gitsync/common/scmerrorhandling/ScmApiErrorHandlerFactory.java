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
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.*;
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
import io.harness.gitsync.common.scmerrorhandling.handlers.github.*;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
@Slf4j
@OwnedBy(PL)
class ScmApiErrorHandlerFactory {
  // List Repository Handlers
  // Get File Handlers
  // List Repository Handlers
  // Get File Handlers
  private final Map<Pair<ScmApis, RepoProviders>, Class<? extends ScmApiErrorHandler>> scmApiErrorHandlerMap =
      ImmutableMap
          .<Pair<ScmApis, RepoProviders>, Class<? extends ScmApiErrorHandler>>builder()
          // List Repository Handlers
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.BITBUCKET), BitbucketListRepoScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.GITHUB), GithubListRepoScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerListRepoScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_REPOSITORIES, RepoProviders.AZURE), AdoListRepoScmApiErrorHandler.class)

          // Get File Handlers
          .put(Pair.of(ScmApis.GET_FILE, RepoProviders.BITBUCKET), BitbucketGetFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_FILE, RepoProviders.GITHUB), GithubGetFileScmApiErrorHandler.class)
          .put(
              Pair.of(ScmApis.GET_FILE, RepoProviders.BITBUCKET_SERVER), BitbucketServerGetFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_FILE, RepoProviders.AZURE), AdoGetFileScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.BITBUCKET),
              BitbucketCreatePullRequestScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.GITHUB),
              GithubCreatePullRequestScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerCreatePullRequestScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_PULL_REQUEST, RepoProviders.AZURE), AdoCreatePullRequestScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.BITBUCKET), BitbucketCreateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.GITHUB), GithubCreateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerCreateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_FILE, RepoProviders.AZURE), AdoCreateFileScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.BITBUCKET), BitbucketUpdateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.GITHUB), GithubUpdateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerUpdateFileScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPDATE_FILE, RepoProviders.AZURE), AdoUpdateFileScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.BITBUCKET), BitbucketCreateBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.GITHUB), GithubCreateBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerCreateBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.CREATE_BRANCH, RepoProviders.AZURE), AdoCreateBranchScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.BITBUCKET), BitbucketListBranchesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.GITHUB), GithubListBranchesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerListBranchesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_BRANCHES, RepoProviders.AZURE), AdoListBranchesScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.BITBUCKET),
              BitbucketGetDefaultBranchScmApiErrorHandler.class)
          .put(
              Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.GITHUB), GithubGetDefaultBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerGetDefaultBranchScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_DEFAULT_BRANCH, RepoProviders.AZURE), AdoGetDefaultBranchScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.BITBUCKET),
              BitbucketGetBranchHeadCommitScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.GITHUB),
              GithubGetBranchHeadCommitScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerGetBranchHeadCommitScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.GET_BRANCH_HEAD_COMMIT, RepoProviders.AZURE),
              AdoGetBranchHeadCommitScmApiErrorHandler.class)

          .put(Pair.of(ScmApis.LIST_FILES, RepoProviders.BITBUCKET), BitbucketListFilesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_FILES, RepoProviders.GITHUB), GithubListFilesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.LIST_FILES, RepoProviders.BITBUCKET_SERVER),
              BitbucketServerListFilesScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPSERT_WEBHOOK, RepoProviders.GITHUB), GithubUpsertWebhookScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPSERT_WEBHOOK, RepoProviders.BITBUCKET), BitbucketUpsertWebhookScmApiErrorHandler.class)
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