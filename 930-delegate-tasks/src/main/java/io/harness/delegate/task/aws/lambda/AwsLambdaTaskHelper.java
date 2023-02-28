/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.util.Strings;
import com.google.common.collect.ImmutableMap;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.exception.AwsLambdaException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.YamlUtils;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesRequest;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.AliasConfiguration;


@Slf4j
@OwnedBy(CDP)
public class AwsLambdaTaskHelper {
  @Inject private AwsLambdaClient awsLambdaClient;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  @Inject private TimeLimiter timeLimiter;

  private YamlUtils yamlUtils = new YamlUtils();

  String ACTIVE_LAST_UPDATE_STATUS = "Successful";
  String FAILED_LAST_UPDATE_STATUS = "Failed";

  long TIMEOUT_IN_SECONDS = 60 * 60L;
  long WAIT_SLEEP_IN_SECONDS = 10L;

  public CreateFunctionResponse deployFunction(AwsLambdaInfraConfig awsLambdaInfraConfig,
      AwsLambdaArtifactConfig awsLambdaArtifactConfig, String awsLambdaManifestContent, LogCallback logCallback) {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    CreateFunctionRequest.Builder createFunctionRequestBuilder =
        parseYamlAsObject(awsLambdaManifestContent, CreateFunctionRequest.serializableBuilderClass());

    CreateFunctionRequest createFunctionRequest = (CreateFunctionRequest) createFunctionRequestBuilder.build();

    String functionName = createFunctionRequest.functionName();
    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(functionName).build();

    try {
      Optional<GetFunctionResponse> existingFunctionOptional =
          awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionRequest);

      if (existingFunctionOptional.isEmpty()) {
        return createFunction(awsLambdaArtifactConfig, logCallback, awsLambdaFunctionsInfraConfig,
            createFunctionRequestBuilder, createFunctionRequest, functionName);
      } else {
        return updateFunction(awsLambdaArtifactConfig, awsLambdaManifestContent, logCallback,
            awsLambdaFunctionsInfraConfig, functionName, existingFunctionOptional.get());
      }
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }
  }

  public CreateFunctionResponse rollbackFunction(AwsLambdaInfraConfig awsLambdaInfraConfig,
      AwsLambdaArtifactConfig awsLambdaArtifactConfig, String awsLambdaManifestContent, String qualifier,
      LogCallback logCallback) throws Exception {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    CreateFunctionRequest.Builder createFunctionRequestBuilder =
        parseYamlAsObject(awsLambdaManifestContent, CreateFunctionRequest.serializableBuilderClass());

    CreateFunctionRequest createFunctionRequest = (CreateFunctionRequest) createFunctionRequestBuilder.build();

    String functionName = createFunctionRequest.functionName();
    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(functionName).build();

    Optional<GetFunctionResponse> existingFunctionOptional = null;
    try {
      existingFunctionOptional =
          awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionRequest);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }

    if (existingFunctionOptional.isEmpty()) {
      throw new AwsLambdaException(
          new Exception(format("Cannot find any function with function name: %s in region: %s %n", functionName,
              awsLambdaFunctionsInfraConfig.getRegion())));
    } else {
      try {
        logCallback.saveExecutionLog(
            format("Updating Function: %s in region: %s with same configuration and code as in qualifier:%s %n",
                functionName, awsLambdaFunctionsInfraConfig.getRegion(), qualifier, LogLevel.INFO));
        return updateFunction(awsLambdaArtifactConfig, awsLambdaManifestContent, logCallback,
            awsLambdaFunctionsInfraConfig, functionName, existingFunctionOptional.get());
      } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
      }
    }
  }

  public DeleteFunctionResponse deleteFunction(AwsLambdaInfraConfig awsLambdaInfraConfig,
      AwsLambdaArtifactConfig awsLambdaArtifactConfig, String awsLambdaManifestContent, LogCallback logCallback)
      throws Exception {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    CreateFunctionRequest.Builder createFunctionRequestBuilder =
        parseYamlAsObject(awsLambdaManifestContent, CreateFunctionRequest.serializableBuilderClass());

    CreateFunctionRequest createFunctionRequest = (CreateFunctionRequest) createFunctionRequestBuilder.build();

    String functionName = createFunctionRequest.functionName();
    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(functionName).build();

    Optional<GetFunctionResponse> existingFunctionOptional = null;
    try {
      existingFunctionOptional =
          awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionRequest);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }

    if (existingFunctionOptional.isEmpty()) {
      throw new AwsLambdaException(
          new Exception(format("Cannot find any function with function name: %s in region: %s %n", functionName,
              awsLambdaFunctionsInfraConfig.getRegion())));
    } else {
      try {
        logCallback.saveExecutionLog(format("Deleting Function: %s in region: %s %n", functionName,
            awsLambdaFunctionsInfraConfig.getRegion(), LogLevel.INFO));
        return awsLambdaClient.deleteFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                                  awsLambdaFunctionsInfraConfig.getRegion()),
            (DeleteFunctionRequest) DeleteFunctionRequest.builder().functionName(functionName).build());
      } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
      }
    }
  }

  private CreateFunctionResponse createFunction(AwsLambdaArtifactConfig awsLambdaArtifactConfig,
      LogCallback logCallback, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig,
      CreateFunctionRequest.Builder createFunctionRequestBuilder, CreateFunctionRequest createFunctionRequest,
      String functionName) {
    CreateFunctionResponse createFunctionResponse;
    FunctionCode functionCode;
    // create new function
    logCallback.saveExecutionLog(format("Creating Function: %s in region: %s %n", functionName,
        awsLambdaFunctionsInfraConfig.getRegion(), LogLevel.INFO));

    functionCode = prepareFunctionCode(awsLambdaArtifactConfig);
    createFunctionRequestBuilder.code(functionCode);
    createFunctionRequestBuilder.publish(true);
    if (awsLambdaArtifactConfig instanceof AwsLambdaS3ArtifactConfig) {
      createFunctionRequestBuilder.packageType(PackageType.ZIP);
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaEcrArtifactConfig) {
      createFunctionRequestBuilder.packageType(PackageType.IMAGE);
    } else {
      throw new InvalidRequestException("Not Support ArtifactConfig Type");
    }
    createFunctionResponse =
        awsLambdaClient.createFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                           awsLambdaFunctionsInfraConfig.getRegion()),
            (CreateFunctionRequest) createFunctionRequestBuilder.build());
    logCallback.saveExecutionLog(format("Created Function: %s in region: %s %n", functionName,
        awsLambdaFunctionsInfraConfig.getRegion(), LogLevel.INFO));

    logCallback.saveExecutionLog(
        format("Created Function Code Sha256: [%s]", createFunctionResponse.codeSha256(), INFO));

    logCallback.saveExecutionLog(format("Created Function ARN: [%s]", createFunctionResponse.functionArn(), INFO));

    logCallback.saveExecutionLog(format("Successfully deployed lambda function: [%s]", functionName));
    logCallback.saveExecutionLog("=================");

    return createFunctionResponse;
  }

  private CreateFunctionResponse updateFunction(AwsLambdaArtifactConfig awsLambdaArtifactConfig,
      String awsLambdaManifestContent, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName,
      GetFunctionResponse existingFunction) {
    logCallback.saveExecutionLog(format("Function: [%s] exists. Update and Publish", functionName));

    logCallback.saveExecutionLog(
        format("Existing Lambda Function Code Sha256: [%s].", existingFunction.configuration().codeSha256()));

    // Update Function Code
    updateFunctionCode(awsLambdaArtifactConfig, logCallback, awsLambdaFunctionsInfraConfig, functionName);

    // Update Function Configuration

    UpdateFunctionConfigurationResponse updateFunctionConfigurationResponse =
        getUpdateFunctionConfigurationResponse(awsLambdaManifestContent, awsLambdaFunctionsInfraConfig);

    waitForFunctionToUpdate(functionName, awsLambdaFunctionsInfraConfig, logCallback);

    // Publish New version
    PublishVersionResponse publishVersionResponse =
        getPublishVersionResponse(logCallback, awsLambdaFunctionsInfraConfig, updateFunctionConfigurationResponse);

    logCallback.saveExecutionLog(format("Successfully deployed lambda function: [%s]", functionName));
    logCallback.saveExecutionLog("=================");

    return (CreateFunctionResponse) CreateFunctionResponse.builder()
        .functionName(updateFunctionConfigurationResponse.functionName())
        .functionArn(updateFunctionConfigurationResponse.functionArn())
        .runtime(updateFunctionConfigurationResponse.runtimeAsString())
        .version(publishVersionResponse.version())
        .build();
  }

  private void updateFunctionCode(AwsLambdaArtifactConfig awsLambdaArtifactConfig, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName) {
    FunctionCode functionCode;
    functionCode = prepareFunctionCode(awsLambdaArtifactConfig);

    UpdateFunctionCodeRequest updateFunctionCodeRequest = null;

    if (awsLambdaArtifactConfig instanceof AwsLambdaS3ArtifactConfig) {
      updateFunctionCodeRequest = (UpdateFunctionCodeRequest) UpdateFunctionCodeRequest.builder()
                                      .functionName(functionName)
                                      .s3Bucket(functionCode.s3Bucket())
                                      .s3Key(functionCode.s3Key())
                                      .build();
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaEcrArtifactConfig) {
      updateFunctionCodeRequest = (UpdateFunctionCodeRequest) UpdateFunctionCodeRequest.builder()
                                      .functionName(functionName)
                                      .imageUri(functionCode.imageUri())
                                      .build();

    } else {
      throw new InvalidRequestException("Not Support ArtifactConfig Type");
    }

    UpdateFunctionCodeResponse updateFunctionCodeResponse =
        awsLambdaClient.updateFunctionCode(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                               awsLambdaFunctionsInfraConfig.getRegion()),
            updateFunctionCodeRequest);
    waitForFunctionToUpdate(functionName, awsLambdaFunctionsInfraConfig, logCallback);

    logCallback.saveExecutionLog(format("Updated Function Code Sha256: [%s]", updateFunctionCodeResponse.codeSha256()));

    logCallback.saveExecutionLog(format("Updated Function ARN: [%s]", updateFunctionCodeResponse.functionArn()));
  }

  private UpdateFunctionConfigurationResponse getUpdateFunctionConfigurationResponse(
      String awsLambdaManifestContent, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    UpdateFunctionConfigurationRequest.Builder updateFunctionConfigurationRequestBuilder =
        parseYamlAsObject(awsLambdaManifestContent, UpdateFunctionConfigurationRequest.serializableBuilderClass());

    return awsLambdaClient.updateFunctionConfiguration(
        getAwsInternalConfig(
            awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion()),
        (UpdateFunctionConfigurationRequest) updateFunctionConfigurationRequestBuilder.build());
  }

  private PublishVersionResponse getPublishVersionResponse(LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig,
      UpdateFunctionConfigurationResponse updateFunctionConfigurationResponse) {
    logCallback.saveExecutionLog("Publishing new version", INFO);

    PublishVersionRequest publishVersionRequest = (PublishVersionRequest) PublishVersionRequest.builder()
                                                      .functionName(updateFunctionConfigurationResponse.functionName())
                                                      .codeSha256(updateFunctionConfigurationResponse.codeSha256())
                                                      .build();

    PublishVersionResponse publishVersionResponse =
        awsLambdaClient.publishVersion(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                           awsLambdaFunctionsInfraConfig.getRegion()),
            publishVersionRequest);

    logCallback.saveExecutionLog(format("Published new version: [%s]", publishVersionResponse.version()));

    logCallback.saveExecutionLog(format("Published function ARN: [%s]", publishVersionResponse.functionArn()));

    return publishVersionResponse;
  }

  private FunctionCode prepareFunctionCode(AwsLambdaArtifactConfig awsLambdaArtifactConfig) {
    if (awsLambdaArtifactConfig instanceof AwsLambdaS3ArtifactConfig) {
      AwsLambdaS3ArtifactConfig awsLambdaS3ArtifactConfig = (AwsLambdaS3ArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder()
          .s3Bucket(awsLambdaS3ArtifactConfig.getBucketName())
          .s3Key(awsLambdaS3ArtifactConfig.getFilePath())
          .build();
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaEcrArtifactConfig) {
      AwsLambdaEcrArtifactConfig awsLambdaEcrArtifactConfig = (AwsLambdaEcrArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder().imageUri(awsLambdaEcrArtifactConfig.getImage()).build();
    }

    throw new InvalidRequestException("Not Support ArtifactConfig Type");
  }

  public AwsInternalConfig getAwsInternalConfig(AwsConnectorDTO awsConnectorDTO, String region) {
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

  public void waitForFunctionToUpdate(
      String functionName, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, LogCallback logCallback) {
    try {
      logCallback.saveExecutionLog("Verifying if status of function to be " + ACTIVE_LAST_UPDATE_STATUS);
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(TIMEOUT_IN_SECONDS), () -> {
        while (true) {
          GetFunctionConfigurationRequest getFunctionConfigurationRequest =
              (GetFunctionConfigurationRequest) GetFunctionConfigurationRequest.builder()
                  .functionName(functionName)
                  .build();
          Optional<GetFunctionConfigurationResponse> result = awsLambdaClient.getFunctionConfiguration(
              getAwsInternalConfig(
                  awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionConfigurationRequest);
          String status = result.get().lastUpdateStatusAsString();
          if (ACTIVE_LAST_UPDATE_STATUS.equalsIgnoreCase(status)) {
            break;
          } else if (FAILED_LAST_UPDATE_STATUS.equalsIgnoreCase(status)) {
            throw new InvalidRequestException(
                "Function failed to reach " + ACTIVE_LAST_UPDATE_STATUS + " status", WingsException.SRE);
          } else {
            logCallback.saveExecutionLog(format("function: [%s], status: [%s], reason: [%s]", functionName, status,
                result.get().lastUpdateStatusReason()));
          }
          sleep(ofSeconds(WAIT_SLEEP_IN_SECONDS));
        }
        return true;
      });
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timed out waiting for function to reach " + ACTIVE_LAST_UPDATE_STATUS + " status",
          "Timeout", ExceptionMessageSanitizer.sanitizeException(e), WingsException.SRE);
    } catch (WingsException e) {
      throw ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Error while waiting for function to reach " + ACTIVE_LAST_UPDATE_STATUS + " status", e);
    }
  }

  public ListVersionsByFunctionResponse listVersionsByFunction(String functionName, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    ListVersionsByFunctionResponse listVersionsByFunctionResult = null;
      try {
        ListVersionsByFunctionRequest listVersionsByFunctionRequest = (ListVersionsByFunctionRequest) ListVersionsByFunctionRequest.builder().functionName(functionName).build();
        listVersionsByFunctionResult = awsLambdaClient.listVersionsByFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                awsLambdaFunctionsInfraConfig.getRegion()), listVersionsByFunctionRequest);
    } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
    }

    return listVersionsByFunctionResult;
  }

  public ListAliasesResponse listAliases(String functionName, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    ListAliasesResponse listAliasesResponse = null;
    try {
      ListAliasesRequest listAliasesRequest = (ListAliasesRequest) ListAliasesRequest.builder().functionName(functionName).build();
      listAliasesResponse = awsLambdaClient.listAliases(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
              awsLambdaFunctionsInfraConfig.getRegion()), listAliasesRequest);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }

    return listAliasesResponse;
  }

  public AwsLambdaFunctionWithActiveVersions getAwsLambdaFunctionWithActiveVersions(AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName) {
    GetFunctionRequest getFunctionRequest =
            (GetFunctionRequest) GetFunctionRequest.builder().functionName(functionName).build();

    Optional<GetFunctionResponse> existingFunctionOptional = null;
    try {
      existingFunctionOptional =
              awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                              awsLambdaFunctionsInfraConfig.getRegion()),
                      getFunctionRequest);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }

    if (existingFunctionOptional.isEmpty()) {
      throw new AwsLambdaException(
              new Exception(format("Cannot find any function with function name: %s in region: %s %n", functionName,
                      awsLambdaFunctionsInfraConfig.getRegion())));
    } else {
      try {
        List<String> activeVersions = new ArrayList<>();
        ListVersionsByFunctionResponse listVersionsByFunctionResult = listVersionsByFunction(functionName, awsLambdaFunctionsInfraConfig);
        if(!(listVersionsByFunctionResult == null || listVersionsByFunctionResult.versions() == null)) {
          throw new InvalidRequestException("Cannot find Versions for the given function");
        }
        for(FunctionConfiguration functionConfiguration : listVersionsByFunctionResult.versions()) {
          if(State.ACTIVE.equals(functionConfiguration.state()) || State.UNKNOWN_TO_SDK_VERSION.equals(functionConfiguration.state())) {
            activeVersions.add(functionConfiguration.version());
          }
        }

        ListAliasesResponse listAliasesResponse = listAliases(functionName, awsLambdaFunctionsInfraConfig);
        return convertToAwsLambdaFunctionWithActiveVersion(existingFunctionOptional.get(), listAliasesResponse, activeVersions);
      } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
      }
    }
  }

  public AwsLambdaFunctionWithActiveVersions convertToAwsLambdaFunctionWithActiveVersion(GetFunctionResponse result, ListAliasesResponse listAliasesResult, List<String> activeVersions) {
    final FunctionConfiguration config = result.configuration();
    final AwsLambdaFunctionWithActiveVersions.AwsLambdaFunctionWithActiveVersionsBuilder builder = AwsLambdaFunctionWithActiveVersions.builder()
            .functionArn(config.functionArn())
            .functionName(config.functionName())
            .runtime(config.runtime().toString())
            .role(config.role())
            .handler(config.handler())
            .codeSize(config.codeSize())
            .description(config.description())
            .timeout(config.timeout())
            .memorySize(config.memorySize())
            .codeSha256(config.codeSha256())
            .versions(activeVersions)
            .kMSKeyArn(config.kmsKeyArn())
            .masterArn(config.masterArn())
            .revisionId(config.revisionId());

    if (Strings.isNotEmpty(config.lastModified())) {
      try {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        builder.lastModified(simpleDateFormat.parse(config.lastModified()));
      } catch (ParseException e) {
        log.warn("Unable to parse date [{}]", config.lastModified());
      }
    }

    if (MapUtils.isNotEmpty(result.tags())) {
      builder.tags(ImmutableMap.copyOf(result.tags()));
    }

    if (listAliasesResult != null) {
      builder.aliases(
              emptyIfNull(listAliasesResult.aliases()).stream().map(AliasConfiguration::name).collect(toList()));
    }
    return builder.build();
  }
}
