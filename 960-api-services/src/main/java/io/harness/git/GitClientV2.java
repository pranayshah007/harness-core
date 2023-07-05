/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git;

import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.DiffRequest;
import io.harness.git.model.DiffResult;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.FetchFilesBwCommitsRequest;
import io.harness.git.model.FetchFilesByPathRequest;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.ListRemoteRequest;
import io.harness.git.model.ListRemoteResult;
import io.harness.git.model.RevertAndPushRequest;
import io.harness.git.model.RevertAndPushResult;

import java.io.IOException;
import javax.annotation.Nullable;

public interface GitClientV2 {
  void ensureRepoLocallyClonedAndUpdated(GitBaseRequest request);

  String validate(GitBaseRequest request);

  void validateOrThrow(GitBaseRequest request);

  FetchFilesResult fetchFilesByPath(FetchFilesByPathRequest request) throws IOException;

  FetchFilesResult fetchFilesByPath(String identifier, FetchFilesByPathRequest request) throws IOException;

  DiffResult diff(DiffRequest request);

  CommitAndPushResult commitAndPush(CommitAndPushRequest request);
  RevertAndPushResult revertAndPush(RevertAndPushRequest request);

  FetchFilesResult fetchFilesBetweenCommits(FetchFilesBwCommitsRequest request);

  /**
   * @return git reference
   */
  @Nullable String downloadFiles(DownloadFilesRequest request) throws IOException;

  /**
   * This method wrap 2 other methods ensureRepoLocallyClonedAndUpdated & copy files to output directory but is doing
   * this in a sync way. It's similar to downloadFiles, but downloadFiles doesn't support downloading git modules. Once
   * this issue is solved for downloadFiles then this method can be replaced without any expected issues
   * @return git reference
   */
  @Nullable String cloneRepoAndCopyToDestDir(DownloadFilesRequest request);

  ListRemoteResult listRemote(ListRemoteRequest request);
}
