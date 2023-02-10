/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.lambda.waiters.LambdaWaiter;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class AwsLambdaClientImpl implements AwsLambdaClient {
  private static final String CLIENT_NAME = "AWS Lambda";
  @Override
  public CreateFunctionResponse createFunction(
      AwsInternalConfig awsInternalConfig, CreateFunctionRequest createFunctionRequest) {
    Region region = Region.of(awsInternalConfig.getDefaultRegion());
    try {
      LambdaClient awsLambdaClient = getAwsLambdaClient(region);
      LambdaWaiter waiter = awsLambdaClient.waiter();
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());

      CreateFunctionResponse functionResponse = awsLambdaClient.createFunction(createFunctionRequest);
      GetFunctionRequest getFunctionRequest =
          (GetFunctionRequest) GetFunctionRequest.builder().functionName(createFunctionRequest.functionName()).build();

      WaiterResponse<GetFunctionResponse> waiterResponse = waiter.waitUntilFunctionExists(getFunctionRequest);
      if (waiterResponse.matched().response().isPresent()) {
        log.info(format("The function ARN is %s", functionResponse.functionArn()));
      }
      return functionResponse;
    } catch (LambdaException e) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public DeleteFunctionResponse deleteFunction(
      AwsInternalConfig awsInternalConfig, DeleteFunctionRequest deleteFunctionRequest) {
    Region region = Region.of(awsInternalConfig.getDefaultRegion());

    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return getAwsLambdaClient(region).deleteFunction(deleteFunctionRequest);
    } catch (LambdaException e) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public InvokeResponse invokeFunction(AwsInternalConfig awsInternalConfig, InvokeRequest invokeRequest) {
    Region region = Region.of(awsInternalConfig.getDefaultRegion());

    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return getAwsLambdaClient(region).invoke(invokeRequest);
    } catch (LambdaException e) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  public LambdaClient getAwsLambdaClient(Region region) {
    LambdaClient awsLambda =
        (LambdaClient) LambdaClient.builder().region(region).credentialsProvider(ProfileCredentialsProvider.create());
    return awsLambda;
  }

  public void logCall(String client, String method) {
    log.info("AWS Cloud Call: client: {}, method: {}", client, method);
  }

  public void logError(String client, String method, String errorMessage) {
    log.error("AWS Cloud Call: client: {}, method: {}, error: {}", client, method, errorMessage);
  }
}
