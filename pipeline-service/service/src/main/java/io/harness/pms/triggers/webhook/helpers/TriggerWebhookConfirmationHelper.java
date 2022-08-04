/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.constants.Constants.X_AMZ_SNS_TOPIC_ARN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.aws.codecommit.AwsCodeCommitRequestType.CONFIRM_TRIGGER_SUBSCRIPTION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_SUCCESSFUL;

import static java.lang.String.format;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.HeaderConfig;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiConfirmSubParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskResponse;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class TriggerWebhookConfirmationHelper {
  private final DelegateServiceGrpcClient delegateServiceGrpcClient;
  private final Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  private final KryoSerializer kryoSerializer;

  public WebhookEventProcessingResult handleTriggerWebhookConfirmationEvent(TriggerWebhookEvent event) {
    try {
      AwsCodeCommitApiTaskResponse awsCodeCommitApiTaskResponse = buildAndFireDelegateTask(event);
      if (awsCodeCommitApiTaskResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
        log.info(
            "Successfully confirmed trigger subscription event for accountId:{}, orgId: {}, projectId: {}, sourceRepoType: {}",
            event.getAccountId(), event.getOrgIdentifier(), event.getProjectIdentifier(), event.getSourceRepoType());
        TriggerEventResponse webhookEventResponse =
            TriggerEventResponseHelper.toResponse(TRIGGER_CONFIRMATION_SUCCESSFUL, event, null, null, null);
        return WebhookEventProcessingResult.builder()
            .mappedToTriggers(false)
            .responses(Collections.singletonList(webhookEventResponse))
            .build();
      } else {
        log.error(format(
            "Failed to confirm trigger subscription event for accountId: %s, orgId: %s, projectId: %s, sourceRepoType: %s, with error message: %s",
            event.getAccountId(), event.getOrgIdentifier(), event.getProjectIdentifier(), event.getSourceRepoType(),
            awsCodeCommitApiTaskResponse.getErrorMessage()));
        return WebhookEventProcessingResult.builder()
            .mappedToTriggers(false)
            .responses(Collections.singletonList(TriggerEventResponseHelper.toResponse(
                TRIGGER_CONFIRMATION_FAILED, event, null, awsCodeCommitApiTaskResponse.getErrorMessage(), null)))
            .build();
      }

    } catch (Exception e) {
      log.error(
          format(
              "Failed to confirm trigger subscription event for accountId:%s, orgId:%s, projectId:%s, sourceRepoType: %s",
              event.getAccountId(), event.getOrgIdentifier(), event.getProjectIdentifier(), event.getSourceRepoType()),
          e);
      return WebhookEventProcessingResult.builder()
          .mappedToTriggers(false)
          .responses(Collections.singletonList(
              TriggerEventResponseHelper.toResponse(TRIGGER_CONFIRMATION_FAILED, event, null, e.getMessage(), null)))
          .build();
    }
  }

  private AwsCodeCommitApiTaskResponse buildAndFireDelegateTask(TriggerWebhookEvent event) {
    String topicArn = null;
    for (HeaderConfig headerConfig : event.getHeaders()) {
      if (headerConfig.getKey().equalsIgnoreCase(X_AMZ_SNS_TOPIC_ARN)) {
        List<String> values = headerConfig.getValues();
        if (isNotEmpty(values) && values.size() == 1) {
          topicArn = values.get(0);
        }
        break;
      }
    }

    ResponseData responseData = delegateServiceGrpcClient.executeSyncTaskReturningResponseData(
        DelegateTaskRequest.builder()
            .accountId(event.getAccountId())
            .executionTimeout(Duration.ofSeconds(30))
            .taskType("AWS_CODECOMMIT_API_TASK")
            .taskParameters(AwsCodeCommitApiTaskParams.builder()
                                .requestType(CONFIRM_TRIGGER_SUBSCRIPTION)
                                .apiParams(AwsCodeCommitApiConfirmSubParams.builder()
                                               .topicArn(topicArn)
                                               .subscriptionConfirmationMessage(event.getPayload())
                                               .build())
                                .build())
            .build(),
        delegateCallbackTokenSupplier.get());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (object instanceof AwsCodeCommitApiTaskResponse) {
        return (AwsCodeCommitApiTaskResponse) object;
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        throw new TriggerException(
            format("Failed to confirm aws code commit subscription. Reason: %s", errorResponseData.getErrorMessage()),
            WingsException.SRE);
      }
    }
    throw new TriggerException("Failed to confirm aws code commit subscription.", WingsException.SRE);
  }
}
