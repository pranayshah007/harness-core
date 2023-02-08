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
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.YamlUtils;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;

@Slf4j
@OwnedBy(CDP)
public class AwsLambdaCommandTaskHelper {
  @Inject private AwsLambdaClient awsLambdaClient;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  private YamlUtils yamlUtils = new YamlUtils();
  public CreateFunctionResponse deployFunction(AwsLambdaInfraConfig awsLambdaInfraConfig,
      AwsLambdaArtifactConfig awsLambdaArtifactConfig, String awsLambdaManifestContent, LogCallback logCallback) {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    CreateFunctionRequest.Builder createFunctionRequestBuilder =
        parseYamlAsObject(awsLambdaManifestContent, CreateFunctionRequest.serializableBuilderClass());

    CreateFunctionRequest createFunctionRequest = (CreateFunctionRequest) createFunctionRequestBuilder.build();

    CreateFunctionResponse createFunctionResponse;
    FunctionCode functionCode;
    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(createFunctionRequest.functionName()).build();

    try {
      Optional<GetFunctionResponse> existingFunctionOptional =
          awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionRequest);

      if (existingFunctionOptional.isEmpty()) {
        // create new function
        logCallback.saveExecutionLog(format("Creating Function: %s in region: %s %n",
            createFunctionRequest.functionName(), awsLambdaFunctionsInfraConfig.getRegion(), LogLevel.INFO));

        if (createFunctionRequest.code() == null || createFunctionRequest.code().zipFile() == null) {
          functionCode = prepareFunctionCode(awsLambdaArtifactConfig);
          createFunctionRequestBuilder.code(functionCode);
          createFunctionResponse =
              awsLambdaClient.createFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                                 awsLambdaFunctionsInfraConfig.getRegion()),
                  createFunctionRequest);
        } else {
          createFunctionResponse =
              awsLambdaClient.createFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                                 awsLambdaFunctionsInfraConfig.getRegion()),
                  createFunctionRequest);
        }
        logCallback.saveExecutionLog(format("Created Function: %s in region: %s %n",
            createFunctionResponse.functionName(), awsLambdaFunctionsInfraConfig.getRegion(), LogLevel.INFO));

        return createFunctionResponse;
      }
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }
    throw new InvalidRequestException(
        format("Unable to deploy Aws Lambda function %s", createFunctionRequest.functionName()));
  }

  private FunctionCode prepareFunctionCode(AwsLambdaArtifactConfig awsLambdaArtifactConfig) {
    if (awsLambdaArtifactConfig instanceof AwsLambdaS3ArtifactConfig) {
      AwsLambdaS3ArtifactConfig awsLambdaS3ArtifactConfig = (AwsLambdaS3ArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder()
          .s3Bucket(awsLambdaS3ArtifactConfig.getBucketName())
          .s3Key(awsLambdaS3ArtifactConfig.getFilePath())
          .build();
    }

    throw new InvalidRequestException("Not Support ArtifactConfig Type");
  }

  private AwsInternalConfig getAwsInternalConfig(AwsConnectorDTO awsConnectorDTO, String region) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(region);
    return awsInternalConfig;
  }

  public <T> T parseYamlAsObject(String yaml, Class<T> tClass) {
    T object;
    try {
      object = yamlUtils.read(yaml, tClass);
    } catch (Exception e) {
      // Set default
      String schema = tClass.getName();

      if (tClass == CreateFunctionRequest.serializableBuilderClass()) {
        schema = "Create Function Request";
      }

      throw NestedExceptionUtils.hintWithExplanationException(
          format("Please check yaml configured matches schema %s", schema),
          format(
              "Error while parsing yaml %s. Its expected to be matching %s schema. Please check Harness documentation https://docs.harness.io for more details",
              yaml, schema),
          e);
    }
    return object;
  }
}
