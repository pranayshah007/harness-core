/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.constants;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
@OwnedBy(DX)
public interface Constants {
  String X_GIT_HUB_EVENT = "X-GitHub-Event";
  String X_GIT_LAB_EVENT = "X-Gitlab-Event";
  String X_BIT_BUCKET_EVENT = "X-Event-Key";
  String BITBUCKET_SERVER_HEADER_KEY = "X-Request-Id";
  String BITBUCKET_CLOUD_HEADER_KEY = "X-Request-UUID";
  String X_AMZ_SNS_MESSAGE_TYPE = "X-Amz-Sns-Message-Type";
  String AMZ_SUBSCRIPTION_CONFIRMATION_TYPE = "SubscriptionConfirmation";
  String X_AMZ_SNS_TOPIC_ARN = "X-Amz-Sns-Topic-Arn";
  String X_HARNESS_WEBHOOK_TOKEN = "X-HARNESS-WEBHOOK-TOKEN";
  String X_HARNESS_TRIGGER_ID = "X-HARNESS-TRIGGER-ID";
  String X_VSS_HEADER = "x-vss-subscriptionid";
  String UNRECOGNIZED_WEBHOOK = "Unrecognized Webhook";
  String X_HUB_SIGNATURE_256 = "X-Hub-Signature-256";

  int SCM_CONFLICT_ERROR_CODE = 409;
  String SCM_CONFLICT_ERROR_MESSAGE = "Cannot update file as it has conflicts with remote";
  int SCM_INTERNAL_SERVER_ERROR_CODE = 500;
  String SCM_INTERNAL_SERVER_ERROR_MESSAGE = "Faced internal server error on SCM, couldn't complete operation";
  String SCM_GIT_PROVIDER_ERROR_MESSAGE =
      "Facing issues while performing required operation on git provider, please try again later";
  int SCM_BAD_RESPONSE_ERROR_CODE = 500;
  int HTTP_SUCCESS_STATUS_CODE = 200;
  int HTTP_BAD_REQUEST_STATUS_CODE = 400;
}
