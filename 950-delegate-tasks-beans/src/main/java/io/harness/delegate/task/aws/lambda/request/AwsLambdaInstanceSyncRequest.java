/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaCommandTypeNG;
import io.harness.delegate.task.aws.lambda.AwsLambdaInfraConfig;
import io.harness.reflection.ExpressionReflectionUtils;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaInstanceSyncRequest
    implements AwsLambdaCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {
  String accountId;
  AwsLambdaCommandTypeNG awsLambdaCommandType;
  AwsLambdaInfraConfig awsLambdaInfraConfig;
  AwsLambdaArtifactConfig awsLambdaArtifactConfig;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  Integer timeoutIntervalInMin;
  String function;

  @Override
  public AwsLambdaCommandTypeNG getAwsLambdaCommandType() {
    return AwsLambdaCommandTypeNG.AWS_INSTANCE_SYNC;
  }
}
