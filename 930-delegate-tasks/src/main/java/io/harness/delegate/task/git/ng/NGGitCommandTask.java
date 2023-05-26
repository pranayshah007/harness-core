/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.git.Constants.GIT_YAML_LOG_PREFIX;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitCommandTaskHandler;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.delegate.beans.git.GitCommandExecutionResponse.GitCommandStatus;
import io.harness.delegate.beans.git.GitCommandParams;
import io.harness.delegate.beans.git.GitCommandType;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.GitBaseRequest;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class NGGitCommandTask extends AbstractDelegateRunnableTask {
  @Inject private NGGitService gitService;
  @Inject private GitCommandTaskHandler gitCommandTaskHandler;
  @Inject private GitDecryptionHelper gitDecryptionHelper;

  public NGGitCommandTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    GitCommandParams gitCommandParams = (GitCommandParams) parameters;
    GitConfigDTO gitConfig = ScmConnectorMapper.toGitConfigDTO(gitCommandParams.getGitConfig());
    gitDecryptionHelper.decryptGitConfig(gitConfig, gitCommandParams.getEncryptionDetails());
    SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
        gitCommandParams.getSshKeySpecDTO(), gitCommandParams.getEncryptionDetails());
    GitCommandType gitCommandType = gitCommandParams.getGitCommandType();
    GitBaseRequest gitCommandRequest = gitCommandParams.getGitCommandRequest();
    ScmConnector scmConnector = gitCommandParams.getScmConnector();
    gitDecryptionHelper.decryptApiAccessConfig(scmConnector, gitCommandParams.getEncryptionDetails());

    switch (gitCommandType) {
      case VALIDATE:
        GitCommandExecutionResponse delegateResponseData =
            (GitCommandExecutionResponse) gitCommandTaskHandler.handleValidateTask(
                gitConfig, scmConnector, getAccountId(), sshSessionConfig);
        delegateResponseData.setDelegateMetaInfo(DelegateMetaInfo.builder().id(getDelegateId()).build());
        return delegateResponseData;
      case COMMIT_AND_PUSH:
        return handleCommitAndPush(gitCommandParams, gitConfig, sshSessionConfig);
      default:
        return GitCommandExecutionResponse.builder()
            .gitCommandStatus(GitCommandStatus.FAILURE)
            .gitCommandRequest(gitCommandRequest)
            .errorMessage(GIT_YAML_LOG_PREFIX + "Git Operation not supported")
            .build();
    }
  }

  private DelegateResponseData handleCommitAndPush(
      GitCommandParams gitCommandParams, GitConfigDTO gitConfig, SshSessionConfig sshSessionConfig) {
    CommitAndPushRequest gitCommitRequest = (CommitAndPushRequest) gitCommandParams.getGitCommandRequest();
    log.info(GIT_YAML_LOG_PREFIX + "COMMIT_AND_PUSH: [{}]", gitCommitRequest);
    CommitAndPushResult gitCommitAndPushResult =
        gitService.commitAndPush(gitConfig, gitCommitRequest, getAccountId(), sshSessionConfig, false);

    return GitCommandExecutionResponse.builder()
        .gitCommandRequest(gitCommitRequest)
        .gitCommandResult(gitCommitAndPushResult)
        .gitCommandStatus(GitCommandStatus.SUCCESS)
        .build();
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
