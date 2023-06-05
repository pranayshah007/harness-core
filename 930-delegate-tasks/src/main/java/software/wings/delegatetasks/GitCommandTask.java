/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_YAML_ERROR;
import static io.harness.eraro.ErrorCode.GIT_CONNECTION_ERROR;
import static io.harness.eraro.ErrorCode.GIT_DIFF_COMMIT_NOT_IN_ORDER;
import static io.harness.eraro.ErrorCode.GIT_UNSEEN_REMOTE_HEAD_COMMIT;
import static io.harness.exception.WingsException.USER_ADMIN;

import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GitConnectionDelegateException;
import io.harness.exception.WingsException;
import io.harness.git.ExceptionSanitizer;
import io.harness.git.model.GitRepositoryType;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ssh.exception.JschClientException;

import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.yaml.GitCommand.GitCommandType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandExecutionResponseBuilder;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitCommitAndPushResult;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitDiffRequest;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Created by anubhaw on 10/26/17.
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class GitCommandTask extends AbstractDelegateRunnableTask {
  @Inject private GitClient gitClient;
  @Inject private EncryptionService encryptionService;
  @Inject private GitService gitService;

  public GitCommandTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public GitCommandExecutionResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public GitCommandExecutionResponse run(Object[] parameters) {
    GitCommandType gitCommandType = (GitCommandType) parameters[0];
    GitConfig gitConfig = (GitConfig) parameters[1];
    gitConfig.setGitRepoType(GitRepositoryType.YAML); // TODO:: find better place. possibly manager can set this
    Optional<Set<String>> secretValuesOptional = getSecretValues(gitConfig);

    try {
      secretValuesOptional.ifPresent(SecretSanitizerThreadLocal::set);
      List<EncryptedDataDetail> encryptionDetails = (List<EncryptedDataDetail>) parameters[2];

      // Decrypt git config
      decryptGitConfig(gitConfig, encryptionDetails);

      String gitConnectorId = null;
      GitOperationContext gitOperationContext = null;

      switch (gitCommandType) {
        case COMMIT_AND_PUSH:
          GitCommitRequest gitCommitRequest = (GitCommitRequest) parameters[3];
          log.info(GIT_YAML_LOG_PREFIX + "COMMIT_AND_PUSH: [{}]", gitCommitRequest);

          gitConnectorId = gitCommitRequest.getYamlGitConfig().getGitConnectorId();
          if (isBlank(gitConnectorId)) {
            gitConnectorId = generateUuid();
          }

          gitOperationContext = GitOperationContext.builder()
                                    .gitConnectorId(gitConnectorId)
                                    .gitConfig(gitConfig)
                                    .gitCommitRequest(gitCommitRequest)
                                    .build();

          GitCommitAndPushResult gitCommitAndPushResult = gitService.commitAndPush(gitOperationContext);

          gitCommitAndPushResult.setYamlGitConfig(gitCommitRequest.getYamlGitConfig());

          return GitCommandExecutionResponse.builder()
              .gitCommandRequest(gitCommitRequest)
              .gitCommandResult(gitCommitAndPushResult)
              .gitCommandStatus(GitCommandStatus.SUCCESS)
              .build();
        case DIFF:
          GitDiffRequest gitDiffRequest = (GitDiffRequest) parameters[3];
          log.info(GIT_YAML_LOG_PREFIX + "DIFF: [{}]", gitDiffRequest);
          boolean excludeFilesOutsideSetupFolder = false;

          try {
            excludeFilesOutsideSetupFolder = (boolean) parameters[4];
          } catch (Exception e) {
            log.error("Boolean for excluding external files not found. Set to false by default.");
          }

          gitConnectorId = gitDiffRequest.getYamlGitConfig().getGitConnectorId();
          if (isBlank(gitConnectorId)) {
            gitConnectorId = generateUuid();
          }

          gitOperationContext = GitOperationContext.builder()
                                    .gitConnectorId(gitConnectorId)
                                    .gitConfig(gitConfig)
                                    .gitDiffRequest(gitDiffRequest)
                                    .build();

          GitDiffResult gitDiffResult = gitClient.diff(gitOperationContext, excludeFilesOutsideSetupFolder);
          gitDiffResult.setYamlGitConfig(gitDiffRequest.getYamlGitConfig());

          return GitCommandExecutionResponse.builder()
              .gitCommandRequest(gitDiffRequest)
              .gitCommandResult(gitDiffResult)
              .gitCommandStatus(GitCommandStatus.SUCCESS)
              .build();
        case VALIDATE:
          log.info(GIT_YAML_LOG_PREFIX + " Processing Git command: VALIDATE");
          String errorMessage = gitClient.validate(gitConfig);
          if (errorMessage == null) {
            return GitCommandExecutionResponse.builder().gitCommandStatus(GitCommandStatus.SUCCESS).build();
          } else {
            return GitCommandExecutionResponse.builder()
                .gitCommandStatus(GitCommandStatus.FAILURE)
                .errorMessage(errorMessage)
                .build();
          }
        case FETCH_FILES:
          GitFetchFilesRequest gitFetchFilesRequest = (GitFetchFilesRequest) parameters[3];
          return getFilesFromGitUsingPath(gitFetchFilesRequest, gitConfig);
        default:
          return GitCommandExecutionResponse.builder()
              .gitCommandStatus(GitCommandStatus.FAILURE)
              .errorMessage(GIT_YAML_LOG_PREFIX + "Git Operation not supported")
              .build();
      }
    } catch (JschClientException ex) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception in processing GitTask {}",
          ExceptionSanitizer.sanitizeTheMessage(ex.getMessage()));
      GitCommandExecutionResponseBuilder builder =
          GitCommandExecutionResponse.builder()
              .gitCommandStatus(GitCommandStatus.FAILURE)
              .errorMessage(ExceptionSanitizer.sanitizeTheMessage(ex.getMessage()))
              .errorCode(getErrorCode(ex));
      return builder.build();
    } catch (Exception ex) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception in processing GitTask {}", ExceptionSanitizer.sanitizeForLogging(ex));
      GitCommandExecutionResponseBuilder builder =
          GitCommandExecutionResponse.builder()
              .gitCommandStatus(GitCommandStatus.FAILURE)
              .errorMessage(ExceptionSanitizer.sanitizeTheMessage(ex.getMessage()))
              .errorCode(getErrorCode(ex));
      return builder.build();
    } finally {
      secretValuesOptional.ifPresent(secrets -> SecretSanitizerThreadLocal.unset());
    }
  }

  private Optional<Set<String>> getSecretValues(GitConfig gitConfig) {
    if (gitConfig == null || gitConfig.getPassword() == null) {
      return Optional.empty();
    }
    return Optional.of(new HashSet<>(Collections.singletonList(String.valueOf(gitConfig.getPassword()))));
  }

  private ErrorCode getErrorCode(Exception ex) {
    if (ex instanceof WingsException) {
      final WingsException we = (WingsException) ex;
      if (GIT_CONNECTION_ERROR == we.getCode()) {
        return GIT_CONNECTION_ERROR;
      } else if (GIT_DIFF_COMMIT_NOT_IN_ORDER == we.getCode()) {
        return GIT_DIFF_COMMIT_NOT_IN_ORDER;
      } else if (GIT_UNSEEN_REMOTE_HEAD_COMMIT == we.getCode()) {
        return GIT_UNSEEN_REMOTE_HEAD_COMMIT;
      } else if (GENERAL_YAML_ERROR == we.getCode()) {
        return GENERAL_YAML_ERROR;
      }
    }
    return null;
  }

  private GitCommandExecutionResponse getFilesFromGitUsingPath(GitFetchFilesRequest gitRequest, GitConfig gitConfig) {
    try {
      GitFetchFilesResult gitResult = gitClient.fetchFilesByPath(gitConfig, gitRequest, false);
      return GitCommandExecutionResponse.builder()
          .gitCommandRequest(gitRequest)
          .gitCommandResult(gitResult)
          .gitCommandStatus(GitCommandStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      return GitCommandExecutionResponse.builder()
          .gitCommandRequest(gitRequest)
          .errorMessage(e.getMessage())
          .gitCommandStatus(GitCommandStatus.FAILURE)
          .build();
    }
  }

  private void decryptGitConfig(GitConfig gitConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      encryptionService.decrypt(gitConfig, encryptionDetails, false);
    } catch (Exception ex) {
      throw new GitConnectionDelegateException(GIT_CONNECTION_ERROR, ex.getCause(), ex.getMessage(), USER_ADMIN);
    }
  }
}
