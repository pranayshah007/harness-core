/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.beans.CommitDetails;
import io.harness.beans.Repository;
import io.harness.beans.WebhookGitUser;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiConfirmSubParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskResponse;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitDataObtainmentParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitDataObtainmentTaskResult;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import com.amazonaws.arn.Arn;
import com.amazonaws.services.codecommit.model.Commit;
import com.amazonaws.services.codecommit.model.RepositoryMetadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Singleton
@Slf4j
public class AwsCodeCommitApiDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private AwsClient awsClient;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  public AwsCodeCommitApiDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    AwsCodeCommitApiTaskParams awsCodeCommitTaskParams = (AwsCodeCommitApiTaskParams) parameters;

    try {
      switch (awsCodeCommitTaskParams.getRequestType()) {
        case OBTAIN_AWS_CODECOMMIT_DATA:
          return handleAwsDecorateWebHookPayloadTask(awsCodeCommitTaskParams);
        case CONFIRM_TRIGGER_SUBSCRIPTION:
          return handleAwsConfirmTriggerSubscription(awsCodeCommitTaskParams);
        default:
          throw new UnsupportedOperationException("Unknown request type: " + awsCodeCommitTaskParams.getRequestType());
      }
    } catch (Exception ex) {
      log.error("failed to send status", ex);
      return AwsCodeCommitApiTaskResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ex.getMessage())
          .build();
    }
  }

  private DelegateResponseData handleAwsConfirmTriggerSubscription(AwsCodeCommitApiTaskParams awsCodeCommitTaskParams) {
    AwsCodeCommitApiConfirmSubParams apiParams =
        (AwsCodeCommitApiConfirmSubParams) awsCodeCommitTaskParams.getApiParams();
    awsClient.confirmSnsSubscription(apiParams.getSubscriptionConfirmationMessage(), apiParams.getTopicArn());
    return AwsCodeCommitApiTaskResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  }

  private DelegateResponseData handleAwsDecorateWebHookPayloadTask(AwsCodeCommitApiTaskParams awsCodeCommitTaskParams) {
    List<EncryptedDataDetail> encryptionDetails = awsCodeCommitTaskParams.getEncryptedDataDetails();
    AwsCodeCommitAuthenticationDTO authenticationDTO =
        awsCodeCommitTaskParams.getAwsCodeCommitConnectorDTO().getAuthentication();
    AwsConfig awsConfig = awsNgConfigMapper.mapAwsCodeCommit(authenticationDTO, encryptionDetails);
    AwsCodeCommitDataObtainmentParams apiParams =
        (AwsCodeCommitDataObtainmentParams) awsCodeCommitTaskParams.getApiParams();
    Arn repoArn = Arn.fromString(apiParams.getRepoArn());
    RepositoryMetadata repositoryMetadata =
        awsClient.fetchRepositoryInformation(awsConfig, repoArn.getRegion(), repoArn.getResource().getResource());
    Repository repository = Repository.builder()
                                .id(repositoryMetadata.getRepositoryId())
                                .name(repositoryMetadata.getRepositoryName())
                                .httpURL(repositoryMetadata.getCloneUrlHttp())
                                .sshURL(repositoryMetadata.getCloneUrlSsh())
                                .branch(repositoryMetadata.getDefaultBranch())
                                .build();

    List<Commit> commits = awsClient.fetchCommitInformation(
        awsConfig, repoArn.getRegion(), repoArn.getResource().getResource(), apiParams.getCommitIds());

    List<CommitDetails> commitDetails = new ArrayList<>();
    for (Commit commit : commits) {
      String[] timestamp = commit.getAuthor().getDate().split(" ");
      long adjustedTimestamp =
          Instant.ofEpochSecond(Long.parseLong(timestamp[0])).atOffset(ZoneOffset.of(timestamp[1])).toEpochSecond();
      commitDetails.add(CommitDetails.builder()
                            .commitId(commit.getCommitId())
                            .message(commit.getMessage())
                            .ownerEmail(commit.getAuthor().getEmail())
                            .ownerName(commit.getAuthor().getName())
                            .timeStamp(adjustedTimestamp)
                            .build());
    }

    WebhookGitUser webhookGitUser = WebhookGitUser.builder()
                                        .gitId(apiParams.getTriggerUserArn())
                                        .name(Arn.fromString(apiParams.getTriggerUserArn()).getResourceAsString())
                                        .build();

    return AwsCodeCommitApiTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .awsCodecommitApiResult(AwsCodeCommitDataObtainmentTaskResult.builder()
                                    .repository(repository)
                                    .webhookGitUser(webhookGitUser)
                                    .commitDetailsList(commitDetails)
                                    .build())
        .build();
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }
}
