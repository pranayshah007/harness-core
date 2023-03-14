/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.service.git;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.git.model.AuthRequest;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.FetchFilesResult;
import io.harness.shell.SshSessionConfig;

import java.io.IOException;

@OwnedBy(HarnessTeam.DX)
public interface NGGitService {
  void validate(GitConfigDTO gitConfig, String accountId, SshSessionConfig sshSessionConfig);

  void validateOrThrow(GitConfigDTO gitConfig, String accountId, SshSessionConfig sshSessionConfig);

  CommitAndPushResult commitAndPush(GitConfigDTO gitConfig, CommitAndPushRequest commitAndPushRequest, String accountId,
      SshSessionConfig sshSessionConfig);

  FetchFilesResult fetchFilesByPath(GitStoreDelegateConfig gitStoreDelegateConfig, String accountId,
      SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException;

  FetchFilesResult fetchFilesByPath(String identifier, GitStoreDelegateConfig gitStoreDelegateConfig, String accountId,
      SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException;

  void downloadFiles(GitStoreDelegateConfig gitStoreDelegateConfig, String manifestFilesDirectory, String accountId,
      SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException;

  AuthRequest getAuthRequest(GitConfigDTO gitConfig, SshSessionConfig sshSessionConfig);
}
