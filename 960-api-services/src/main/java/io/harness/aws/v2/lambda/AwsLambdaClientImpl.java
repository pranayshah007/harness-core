/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.AwsClientHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.lambda.model.ListAliasesRequest;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.waiters.LambdaWaiter;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class AwsLambdaClientImpl extends AwsClientHelper implements AwsLambdaClient {
  private static final String CLIENT_NAME = "AWS Lambda";

  @Override
  public CreateFunctionResponse createFunction(
      AwsInternalConfig awsInternalConfig, CreateFunctionRequest createFunctionRequest) {
    try {
      LambdaClient awsLambdaClient = (LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion());
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
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return ((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
          .deleteFunction(deleteFunctionRequest);
    } catch (LambdaException e) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public Optional<GetFunctionResponse> getFunction(
      AwsInternalConfig awsInternalConfig, GetFunctionRequest getFunctionRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return Optional.ofNullable(((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
                                     .getFunction(getFunctionRequest));
    } catch (LambdaException lambdaException) {
      if (lambdaException instanceof ResourceNotFoundException) {
        return Optional.empty();
      }
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), lambdaException.getMessage());
      throw new InvalidRequestException(lambdaException.getMessage());
    }
  }

  @Override
  public Optional<GetFunctionConfigurationResponse> getFunctionConfiguration(
      AwsInternalConfig awsInternalConfig, GetFunctionConfigurationRequest getFunctionConfigurationRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return Optional.ofNullable(((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
                                     .getFunctionConfiguration(getFunctionConfigurationRequest));
    } catch (LambdaException lambdaException) {
      if (lambdaException instanceof ResourceNotFoundException) {
        return Optional.empty();
      }
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), lambdaException.getMessage());
      throw new InvalidRequestException(lambdaException.getMessage());
    }
  }

  @Override
  public UpdateFunctionCodeResponse updateFunctionCode(
      AwsInternalConfig awsInternalConfig, UpdateFunctionCodeRequest updateFunctionCodeRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return ((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
          .updateFunctionCode(updateFunctionCodeRequest);
    } catch (LambdaException lambdaException) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), lambdaException.getMessage());
      throw new InvalidRequestException(lambdaException.getMessage());
    }
  }

  @Override
  public UpdateFunctionConfigurationResponse updateFunctionConfiguration(
      AwsInternalConfig awsInternalConfig, UpdateFunctionConfigurationRequest updateFunctionConfigurationRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return ((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
          .updateFunctionConfiguration(updateFunctionConfigurationRequest);
    } catch (LambdaException lambdaException) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), lambdaException.getMessage());
      throw new InvalidRequestException(lambdaException.getMessage());
    }
  }

  @Override
  public PublishVersionResponse publishVersion(
      AwsInternalConfig awsInternalConfig, PublishVersionRequest publishVersionRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return ((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
          .publishVersion(publishVersionRequest);
    } catch (LambdaException lambdaException) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), lambdaException.getMessage());
      throw new InvalidRequestException(lambdaException.getMessage());
    }
  }

  @Override
  public InvokeResponse invokeFunction(AwsInternalConfig awsInternalConfig, InvokeRequest invokeRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return ((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion())).invoke(invokeRequest);
    } catch (LambdaException e) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public ListVersionsByFunctionResponse listVersionsByFunction(
      AwsInternalConfig awsInternalConfig, ListVersionsByFunctionRequest listVersionsByFunctionRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return ((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
          .listVersionsByFunction(listVersionsByFunctionRequest);
    } catch (LambdaException e) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public ListAliasesResponse listAliases(AwsInternalConfig awsInternalConfig, ListAliasesRequest listAliasesRequest) {
    try {
      logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return ((LambdaClient) getClient(awsInternalConfig, awsInternalConfig.getDefaultRegion()))
          .listAliases(listAliasesRequest);
    } catch (LambdaException e) {
      logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public SdkClient getClient(AwsInternalConfig awsConfig, String region) {
    return software.amazon.awssdk.services.lambda.LambdaClient.builder()
        .credentialsProvider(getAwsCredentialsProvider(awsConfig))
        .region(Region.of(region))
        .overrideConfiguration(getClientOverrideConfiguration(awsConfig))
        .build();
  }

  @Override
  public String client() {
    return "LAMBDA";
  }

  @Override
  public void handleClientServiceException(AwsServiceException awsServiceException) {
    if (awsServiceException instanceof LambdaException) {
      throw new InvalidRequestException(awsServiceException.getMessage());
    }
  }

  public void logCall(String client, String method) {
    log.info("AWS Cloud Call: client: {}, method: {}", client, method);
  }

  public void logError(String client, String method, String errorMessage) {
    log.error("AWS Cloud Call: client: {}, method: {}, error: {}", client, method, errorMessage);
  }
}
