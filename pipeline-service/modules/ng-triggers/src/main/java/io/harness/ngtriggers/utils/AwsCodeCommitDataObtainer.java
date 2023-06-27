/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CommitDetails;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.PushWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.WebhookEvent;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskResponse;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitDataObtainmentParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitDataObtainmentTaskResult;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitRequestType;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.mapper.proto.SCMProtoMessageMapper;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.User;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(CI)
public class AwsCodeCommitDataObtainer implements GitProviderBaseDataObtainer {
  private final TaskExecutionUtils taskExecutionUtils;
  private final ConnectorUtils connectorUtils;
  private final KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  private final WebhookEventPayloadParser webhookEventPayloadParser;

  @Override
  public void acquireProviderData(FilterRequestData filterRequestData, List<TriggerDetails> triggers) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();

    if (webhookPayloadData.getWebhookEvent().getType() != WebhookEvent.Type.PUSH) {
      throw new TriggerException(String.format("Unsupported web hook event type:[%s] for aws codecommit",
                                     webhookPayloadData.getWebhookEvent().getType()),
          WingsException.SRE);
    }

    for (TriggerDetails details : triggers) {
      try {
        String connectorIdentifier =
            details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getConnectorIdentifier();
        AwsCodeCommitApiTaskResponse taskResponse =
            buildAndExecuteAwsCodeCommitDelegateTask(webhookPayloadData, details, connectorIdentifier);
        if (taskResponse.getCommandExecutionStatus() == SUCCESS) {
          AwsCodeCommitDataObtainmentTaskResult awsCodecommitApiResult =
              (AwsCodeCommitDataObtainmentTaskResult) taskResponse.getAwsCodecommitApiResult();
          ParseWebhookResponse parseWebhookResponse =
              parsePrHook(filterRequestData.getWebhookPayloadData().getParseWebhookResponse(), awsCodecommitApiResult);
          WebhookPayloadData decoratedPayloadData = webhookEventPayloadParser.convertWebhookResponse(
              parseWebhookResponse, webhookPayloadData.getOriginalEvent());
          filterRequestData.setWebhookPayloadData(decoratedPayloadData);
          break;
        }
      } catch (Exception e) {
        log.error("Failed while fetching additional information from aws codecommit for branch webhook event"
                + "Project : " + filterRequestData.getAccountId() + ", with Exception" + e.getMessage(),
            e);
      }
    }
  }

  @VisibleForTesting
  AwsCodeCommitApiTaskResponse buildAndExecuteAwsCodeCommitDelegateTask(
      WebhookPayloadData webhookPayloadData, TriggerDetails details, String connectorIdentifier) {
    ConnectorDetails connectorDetails =
        connectorUtils.getConnectorDetails(IdentifierRef.builder()
                                               .accountIdentifier(details.getNgTriggerEntity().getAccountId())
                                               .orgIdentifier(details.getNgTriggerEntity().getOrgIdentifier())
                                               .projectIdentifier(details.getNgTriggerEntity().getProjectIdentifier())
                                               .build(),
            connectorIdentifier);

    Repository repository = webhookPayloadData.getRepository();
    List<String> commitIds;
    PushWebhookEvent pushWebhookEvent = (PushWebhookEvent) webhookPayloadData.getWebhookEvent();
    List<CommitDetails> commitDetailsList = pushWebhookEvent.getCommitDetailsList();
    commitIds = commitDetailsList.stream().map(CommitDetails::getCommitId).collect(toList());

    ResponseData responseData = taskExecutionUtils.executeSyncTask(
        DelegateTaskRequest.builder()
            .accountId(details.getNgTriggerEntity().getAccountId())
            .executionTimeout(Duration.ofSeconds(30))
            .taskType("AWS_CODECOMMIT_API_TASK")
            .taskParameters(
                AwsCodeCommitApiTaskParams.builder()
                    .requestType(AwsCodeCommitRequestType.OBTAIN_AWS_CODECOMMIT_DATA)
                    .awsCodeCommitConnectorDTO((AwsCodeCommitConnectorDTO) connectorDetails.getConnectorConfig())
                    .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
                    .apiParams(AwsCodeCommitDataObtainmentParams.builder()
                                   .repoArn(repository.getId())
                                   .triggerUserArn(webhookPayloadData.getWebhookGitUser().getGitId())
                                   .commitIds(commitIds)
                                   .build())
                    .build())
            .build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = binaryResponseData.isUsingKryoWithoutReference()
          ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
          : kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (object instanceof AwsCodeCommitApiTaskResponse) {
        return (AwsCodeCommitApiTaskResponse) object;
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        throw new TriggerException(
            String.format("Failed to fetch aws code commit details. Reason: %s", errorResponseData.getErrorMessage()),
            WingsException.SRE);
      }
    }
    throw new TriggerException("Failed to fetch aws code commit details", WingsException.SRE);
  }

  public ParseWebhookResponse parsePrHook(ParseWebhookResponse parseWebhookResponse,
      AwsCodeCommitDataObtainmentTaskResult awsCodeCommitDataObtainmentTaskResult) {
    List<Commit> commits = awsCodeCommitDataObtainmentTaskResult.getCommitDetailsList()
                               .stream()
                               .map(SCMProtoMessageMapper::convertCommitDetails)
                               .collect(toList());
    io.harness.product.ci.scm.proto.Repository repository =
        SCMProtoMessageMapper.convertRepository(awsCodeCommitDataObtainmentTaskResult.getRepository());

    User user = SCMProtoMessageMapper.convertWebhookGitUser(awsCodeCommitDataObtainmentTaskResult.getWebhookGitUser());
    PushHook decoratedPushHook = parseWebhookResponse.getPush()
                                     .toBuilder()
                                     .mergeFrom(PushHook.newBuilder().setRepo(repository).setSender(user).build())
                                     .clearCommits()
                                     .addAllCommits(commits)
                                     .build();
    return ParseWebhookResponse.newBuilder().setPush(decoratedPushHook).build();
  }
}
