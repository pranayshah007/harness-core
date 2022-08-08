/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.GitFile;
import io.harness.git.model.GitRepositoryType;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.trigger.TriggerCommand.TriggerCommandType;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.trigger.request.TriggerDeploymentNeededRequest;
import software.wings.helpers.ext.trigger.request.TriggerRequest;
import software.wings.helpers.ext.trigger.response.TriggerDeploymentNeededResponse;
import software.wings.helpers.ext.trigger.response.TriggerResponse;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TriggerTask extends AbstractDelegateRunnableTask {
  @Inject private EncryptionService encryptionService;
  @Inject private GitService gitService;

  public TriggerTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public TriggerResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public TriggerResponse run(Object[] parameters) {
    TriggerRequest triggerRequest = (TriggerRequest) (parameters[0]);
    TriggerCommandType triggerCommandType = triggerRequest.getTriggerCommandType();
    TriggerResponse triggerResponse;

    log.info("Executing trigger task for account {} and type is {}", triggerRequest.getAccountId(), triggerCommandType);

    try {
      switch (triggerCommandType) {
        case DEPLOYMENT_NEEDED_CHECK:
          TriggerDeploymentNeededRequest triggerDeploymentNeededRequest =
              (TriggerDeploymentNeededRequest) triggerRequest;
          triggerResponse = isDeploymentNeeded(triggerDeploymentNeededRequest);
          break;

        default:
          throw new InvalidRequestException("Operation not supported");
      }

      return triggerResponse;
    } catch (Exception ex) {
      log.error("Exception in processing trigger task for account {}, triggerCommandType {}",
          triggerRequest.getAccountId(), triggerCommandType, ex);
      return new TriggerResponse(null, ExecutionStatus.FAILED, ExceptionUtils.getMessage(ex));
    }
  }

  private TriggerDeploymentNeededResponse isDeploymentNeeded(TriggerDeploymentNeededRequest triggerRequest) {
    GitConfig gitConfig = triggerRequest.getGitConfig();
    String gitConnectorId = triggerRequest.getGitConnectorId();
    String currentCommitId = triggerRequest.getCurrentCommitId();
    String oldCommitId = triggerRequest.getOldCommitId();
    List<String> filePaths = triggerRequest.getFilePaths();
    List<EncryptedDataDetail> encryptionDetails = triggerRequest.getEncryptionDetails();

    log.info("Checking if deployment needed - gitConnectorId {}, currentCommitId {}, oldCommitId {}", gitConnectorId,
        currentCommitId, oldCommitId);

    try {
      encryptionService.decrypt(gitConfig, encryptionDetails, false);

      gitConfig.setGitRepoType(GitRepositoryType.TRIGGER);
      GitFetchFilesResult gitFetchFilesResult =
          gitService.fetchFilesBetweenCommits(gitConfig, currentCommitId, oldCommitId, gitConnectorId);

      boolean deploymentNeeded = isDeploymentNeeded(filePaths, gitFetchFilesResult.getFiles());
      return TriggerDeploymentNeededResponse.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .deploymentNeeded(deploymentNeeded)
          .build();
    } catch (Exception ex) {
      log.error("Exception in checking if deployment needed " + ExceptionUtils.getMessage(ex));
      throw ex;
    }
  }

  private boolean isDeploymentNeeded(List<String> filePaths, List<GitFile> changedFiles) {
    Set<String> changedFilesSet = new HashSet<>();

    if (isNotEmpty(changedFiles)) {
      for (GitFile gitFile : changedFiles) {
        changedFilesSet.add(gitFile.getFilePath());
      }

      for (String path : filePaths) {
        if (changedFilesSet.contains(path)) {
          return true;
        }
      }
    }

    return false;
  }
}
