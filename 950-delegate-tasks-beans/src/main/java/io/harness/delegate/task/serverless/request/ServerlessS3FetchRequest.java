/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.serverless.ServerlessS3FetchFileConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessS3FetchRequest implements ActivityAccess, TaskParameters, ExecutionCapabilityDemander,
                                                 ExpressionReflectionUtils.NestedAnnotationResolver {
  String executionLogName;
  String activityId;
  String accountId;
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessS3FetchFileConfig serverlessS3FetchFileConfig;
  @Builder.Default boolean shouldOpenLogStream = true;
  boolean closeLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    S3StoreDelegateConfig s3StoreDelegateConfig = serverlessS3FetchFileConfig.getS3StoreDelegateConfig();
    capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
        s3StoreDelegateConfig.getAwsConnector(), maskingEvaluator));
    capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
        s3StoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    return capabilities;
  }
}
