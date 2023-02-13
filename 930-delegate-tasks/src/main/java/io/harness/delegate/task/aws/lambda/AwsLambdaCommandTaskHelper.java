/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.lambda.AwsLambdaClient;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.json.JSONObject;
import org.jooq.tools.json.JSONParser;
import org.jooq.tools.json.ParseException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.lambda.model.LogType;

@Slf4j
@OwnedBy(CDP)
public class AwsLambdaCommandTaskHelper {
  @Inject private AwsLambdaClient awsLambdaClient;
  public CreateFunctionResponse createFunction(
      AwsInternalConfig awsInternalConfig, String awsLambdaDeployManifestContent) throws JsonProcessingException {
    ObjectMapper jsonReader = new ObjectMapper(new JsonFactory());
    CreateFunctionRequest createFunctionRequest =
        jsonReader.readValue(awsLambdaDeployManifestContent, CreateFunctionRequest.class);

    return awsLambdaClient.createFunction(awsInternalConfig, createFunctionRequest);
  }

  public InvokeResponse invokeFunction(AwsInternalConfig awsInternalConfig, String awsLambdaDeployManifestContent,
      LogCallback logCallback, String functionName, String qualifier) throws ParseException, JsonProcessingException {
    if (isEmpty(awsLambdaDeployManifestContent)) {
      throw new InvalidRequestException("Aws Lambda Manifest cannot be empty");
    }

    ObjectMapper jsonReader = new ObjectMapper(new JsonFactory());
    CreateFunctionRequest createFunctionRequest =
        jsonReader.readValue(awsLambdaDeployManifestContent, CreateFunctionRequest.class);

    JSONParser parser = new JSONParser();
    JSONObject jsonObject = (JSONObject) parser.parse(awsLambdaDeployManifestContent);
    SdkBytes payload = SdkBytes.fromUtf8String(jsonObject.toString());

    try {
      InvokeRequest invokeRequest = (InvokeRequest) InvokeRequest.builder()
                                        .functionName(functionName)
                                        .qualifier(qualifier)
                                        .payload(payload)
                                        .logType(LogType.TAIL)
                                        .build();

      logCallback.saveExecutionLog(format("Invoking lambda function: ", createFunctionRequest.functionName()));
      InvokeResponse invokeResponse = awsLambdaClient.invokeFunction(awsInternalConfig, invokeRequest);
      logCallback.saveExecutionLog(format("Lambda Invocation result: ", invokeResponse.executedVersion()));
      return invokeResponse;

    } catch (LambdaException exception) {
      throw new InvalidRequestException(exception.getMessage());
    }
  }

  public DeleteFunctionResponse deleteFunction(
      AwsInternalConfig awsInternalConfig, String awsLambdaDeployManifestContent) throws JsonProcessingException {
    ObjectMapper jsonReader = new ObjectMapper(new JsonFactory());
    DeleteFunctionRequest deleteFunctionRequest =
        jsonReader.readValue(awsLambdaDeployManifestContent, DeleteFunctionRequest.class);

    return awsLambdaClient.deleteFunction(awsInternalConfig, null);
  }
}
