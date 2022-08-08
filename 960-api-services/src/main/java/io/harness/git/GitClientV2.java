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

import java.io.IOException;

public interface GitClientV2 {
  void ensureRepoLocallyClonedAndUpdated(GitBaseRequest request);

  String validate(GitBaseRequest request);

  void validateOrThrow(GitBaseRequest request);

  FetchFilesResult fetchFilesByPath(FetchFilesByPathRequest request) throws IOException;

  DiffResult diff(DiffRequest request);

  CommitAndPushResult commitAndPush(CommitAndPushRequest request);

  FetchFilesResult fetchFilesBetweenCommits(FetchFilesBwCommitsRequest request);

  void downloadFiles(DownloadFilesRequest request) throws IOException;
}
