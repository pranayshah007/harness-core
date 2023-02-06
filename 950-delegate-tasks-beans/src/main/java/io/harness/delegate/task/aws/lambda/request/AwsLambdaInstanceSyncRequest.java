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
import io.harness.delegate.task.aws.lambda.AwsLambdaCommandTypeNG;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import static io.harness.expression.Expression.ALLOW_SECRETS;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaInstanceSyncRequest
    implements AwsLambdaCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {
  String accountId;
  AwsLambdaCommandTypeNG awsLambdaCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS)
  AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  String function;
}
