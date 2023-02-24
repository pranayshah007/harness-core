/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaCommandTypeNG;
import io.harness.delegate.task.aws.lambda.AwsLambdaInfraConfig;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaRollbackRequest implements AwsLambdaCommandRequest, NestedAnnotationResolver {
  AwsLambdaCommandTypeNG awsLambdaCommandTypeNG;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS) String awsLambdaDeployManifestContent;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) AwsLambdaInfraConfig awsLambdaInfraConfig;
  @NonFinal @Expression(ALLOW_SECRETS) AwsLambdaArtifactConfig awsLambdaArtifactConfig;
  @NonFinal @Expression(ALLOW_SECRETS) String functionName;
  @NonFinal @Expression(ALLOW_SECRETS) String qualifier;
  boolean firstDeployment;

  @Override
  public AwsLambdaCommandTypeNG getAwsLambdaCommandType() {
    return AwsLambdaCommandTypeNG.AWS_LAMBDA_ROLLBACK;
  }
}
