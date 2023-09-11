/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.GitSyncModule;
import io.harness.gitsync.common.dtos.ScmUpdateGitFileCacheRequestDTO;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class FetchFilesFromGitHelper {
  private ExecutorService executor;
  private Injector injector;

  @Inject
  public FetchFilesFromGitHelper(
      @Named(GitSyncModule.GITX_BACKGROUND_CACHE_UPDATE_EXECUTOR_NAME) ExecutorService executor, Injector injector) {
    this.executor = executor;
    this.injector = injector;
  }

  public void submitTask(String accountIdentifier, String repoName, String branch, ScmConnector scmConnector,
      String eventIdentifier, List<String> filesToBeFetched) {
    try {
      ScmUpdateGitFileCacheRequestDTO scmUpdateGitFileCacheRequestDTO = ScmUpdateGitFileCacheRequestDTO.builder()
                                                                            .accountIdentifier(accountIdentifier)
                                                                            .repoName(repoName)
                                                                            .branch(branch)
                                                                            .folderPaths(filesToBeFetched)
                                                                            .scmConnector(scmConnector)
                                                                            .eventIdentifier(eventIdentifier)
                                                                            .build();
      FetchFilesFromGitRunnable fetchFilesFromGitRunnable =
          new FetchFilesFromGitRunnable(scmUpdateGitFileCacheRequestDTO);
      injector.injectMembers(fetchFilesFromGitRunnable);
      executor.execute(fetchFilesFromGitRunnable);
    } catch (Exception exception) {
      log.error("", exception);
    }
  }
}
