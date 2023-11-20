/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.service.git;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.git.model.AuthRequest;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.ListRemoteRequest;
import io.harness.git.model.ListRemoteResult;
import io.harness.git.model.RevertAndPushRequest;
import io.harness.git.model.RevertAndPushResult;
import io.harness.shell.SshSessionConfig;

import java.io.IOException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@OwnedBy(HarnessTeam.DX)
public interface NGGitService {
  void validate(GitConfigDTO gitConfig, String accountId, SshSessionConfig sshSessionConfig);

  void validateOrThrow(GitConfigDTO gitConfig, String accountId, SshSessionConfig sshSessionConfig);

  ListRemoteResult listRemote(GitConfigDTO gitConfig, ListRemoteRequest gitBaseRequest, String accountId,
      SshSessionConfig sshSessionConfig, boolean overrideFromGitConfig);

  CommitAndPushResult commitAndPush(GitConfigDTO gitConfig, CommitAndPushRequest commitAndPushRequest, String accountId,
      SshSessionConfig sshSessionConfig, boolean overrideFromGitConfig);

  FetchFilesResult fetchFilesByPath(GitStoreDelegateConfig gitStoreDelegateConfig, String accountId,
      SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException;

  FetchFilesResult fetchFilesByPath(String identifier, GitStoreDelegateConfig gitStoreDelegateConfig, String accountId,
      SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException;

  void downloadFiles(GitStoreDelegateConfig gitStoreDelegateConfig, String manifestFilesDirectory, String accountId,
      SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO, boolean mayHaveMultipleFolders) throws IOException;

  AuthRequest getAuthRequest(GitConfigDTO gitConfig, SshSessionConfig sshSessionConfig);

  RevertAndPushResult revertCommitAndPush(GitConfigDTO gitConfig, RevertAndPushRequest commitAndPushRequest,
      String accountId, SshSessionConfig sshSessionConfig, boolean overrideFromGitConfig);
}
