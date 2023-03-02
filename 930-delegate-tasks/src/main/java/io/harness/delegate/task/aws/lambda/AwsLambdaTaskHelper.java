/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static software.amazon.awssdk.services.lambda.model.State.FAILED;
import static software.amazon.awssdk.services.lambda.model.State.INACTIVE;
import static software.amazon.awssdk.services.lambda.model.State.PENDING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.exception.AwsLambdaException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionWithActiveVersions.AwsLambdaFunctionWithActiveVersionsBuilder;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.FileCreationException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.YamlUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.util.Strings;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.AliasConfiguration;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionCodeLocation;
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
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;

@Slf4j
@OwnedBy(CDP)
public class AwsLambdaTaskHelper {
  @Inject private AwsLambdaClient awsLambdaClient;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private TimeLimiter timeLimiter;
  private YamlUtils yamlUtils = new YamlUtils();

  private final String ACTIVE_LAST_UPDATE_STATUS = "Successful";
  private final String FAILED_LAST_UPDATE_STATUS = "Failed";
  private final String TEMP_ARTIFACT_FILE = "tempArtifactFile";

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
      Optional<GetFunctionResponse> existingFunction =
          awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionRequest);

      if (existingFunction.isEmpty()) {
        return createFunction(awsLambdaArtifactConfig, logCallback, awsLambdaFunctionsInfraConfig,
            createFunctionRequestBuilder, functionName);
      } else {
        return updateFunctionWithArtifact(awsLambdaArtifactConfig, awsLambdaManifestContent, logCallback,
            awsLambdaFunctionsInfraConfig, existingFunction.get());
      }
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  public CreateFunctionResponse rollbackFunction(String functionName, AwsLambdaInfraConfig awsLambdaInfraConfig,
      String functionCode, String functionConfiguration, String qualifier, LogCallback logCallback)
      throws AwsLambdaException {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(functionName).build();

    Optional<GetFunctionResponse> existingFunctionOptional =
        getAwsLambdaFunctionFromAws(awsLambdaFunctionsInfraConfig, getFunctionRequest);

    if (existingFunctionOptional.isEmpty()) {
      throw new AwsLambdaException(format("Cannot find any function with function name: %s in region: %s %n",
          functionName, awsLambdaFunctionsInfraConfig.getRegion()));
    } else {
      try {
        logCallback.saveExecutionLog(
            format("Updating Function: %s in region: %s with same configuration and code as in qualifier:%s %n",
                functionName, awsLambdaFunctionsInfraConfig.getRegion(), qualifier, LogLevel.INFO));

        return updateFunctionForRollback(functionConfiguration, logCallback, awsLambdaFunctionsInfraConfig,
            functionName, existingFunctionOptional.get(), functionCode);
      } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
      }
    }
  }

  private Optional<GetFunctionResponse> getAwsLambdaFunctionFromAws(
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, GetFunctionRequest getFunctionRequest) {
    Optional<GetFunctionResponse> existingFunctionOptional;
    try {
      existingFunctionOptional =
          awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionRequest);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }
    return existingFunctionOptional;
  }

  private CreateFunctionResponse updateFunctionWithArtifact(AwsLambdaArtifactConfig awsLambdaArtifactConfig,
      String awsLambdaManifestContent, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, GetFunctionResponse function) {
    String functionName = function.configuration().functionName();
    String functionCodeSha = function.configuration().codeSha256();
    logCallback.saveExecutionLog(format("Function: [%s] already exists. Update and Publish.", functionName));

    logCallback.saveExecutionLog(
        format("Existing Lambda Function Code Sha256: [%s].", function.configuration().codeSha256()));

    // Update Function Code
    updateFunctionCodeWithArtifact(
        awsLambdaArtifactConfig, logCallback, awsLambdaFunctionsInfraConfig, functionName, functionCodeSha);

    // Update Function Configuration
    UpdateFunctionConfigurationResponse updateFunctionConfigurationResponse =
        getUpdateFunctionConfigurationResponse(awsLambdaManifestContent, awsLambdaFunctionsInfraConfig);

    waitForFunctionToUpdate(functionName, awsLambdaFunctionsInfraConfig, logCallback);

    // Publish New version
    PublishVersionResponse publishVersionResponse =
        getPublishVersionResponse(logCallback, awsLambdaFunctionsInfraConfig,
            updateFunctionConfigurationResponse.functionName(), updateFunctionConfigurationResponse.codeSha256());

    logCallback.saveExecutionLog(format("Successfully deployed lambda function: [%s]", functionName));
    logCallback.saveExecutionLog("=================");

    return (CreateFunctionResponse) CreateFunctionResponse.builder()
        .functionName(updateFunctionConfigurationResponse.functionName())
        .functionArn(updateFunctionConfigurationResponse.functionArn())
        .runtime(updateFunctionConfigurationResponse.runtimeAsString())
        .version(publishVersionResponse.version())
        .build();
  }

  private void updateFunctionCodeWithArtifact(AwsLambdaArtifactConfig awsLambdaArtifactConfig, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName,
      String existingFunctionCodeSha) {
    FunctionCode functionCode;
    functionCode = prepareFunctionCode(awsLambdaArtifactConfig);

    UpdateFunctionCodeRequest.Builder updateFunctionCodeRequest;

    if (awsLambdaArtifactConfig instanceof AwsLambdaS3ArtifactConfig) {
      updateFunctionCodeRequest = UpdateFunctionCodeRequest.builder()
                                      .functionName(functionName)
                                      .s3Bucket(functionCode.s3Bucket())
                                      .s3Key(functionCode.s3Key());
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaEcrArtifactConfig) {
      updateFunctionCodeRequest =
          UpdateFunctionCodeRequest.builder().functionName(functionName).imageUri(functionCode.imageUri());

    } else {
      throw new InvalidRequestException("Not Support ArtifactConfig Type");
    }

    if (isFunctionCodeUpdateNeeded(awsLambdaFunctionsInfraConfig, updateFunctionCodeRequest, existingFunctionCodeSha)) {
      // Update Function Code
      UpdateFunctionCodeResponse updateFunctionCodeResponse =
          awsLambdaClient.updateFunctionCode(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                                 awsLambdaFunctionsInfraConfig.getRegion()),
              (UpdateFunctionCodeRequest) updateFunctionCodeRequest.build());
      waitForFunctionToUpdate(functionName, awsLambdaFunctionsInfraConfig, logCallback);

      logCallback.saveExecutionLog(
          format("Updated Function Code Sha256: [%s]", updateFunctionCodeResponse.codeSha256()));

      logCallback.saveExecutionLog(format("Updated Function ARN: [%s]", updateFunctionCodeResponse.functionArn()));

    } else {
      logCallback.saveExecutionLog("Function code didn't change. Skip function code update", INFO);
    }
  }

  private boolean isFunctionCodeUpdateNeeded(AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig,
      UpdateFunctionCodeRequest.Builder updateFunctionCodeRequest, String existingFunctionCodeSha) {
    UpdateFunctionCodeResponse updateFunctionCodeResponseDryRun =
        awsLambdaClient.updateFunctionCode(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                               awsLambdaFunctionsInfraConfig.getRegion()),
            (UpdateFunctionCodeRequest) updateFunctionCodeRequest.dryRun(Boolean.TRUE).build());
    return !updateFunctionCodeResponseDryRun.codeSha256().equals(existingFunctionCodeSha);
  }

  protected FunctionCode prepareFunctionCode(AwsLambdaArtifactConfig awsLambdaArtifactConfig) {
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

  public DeleteFunctionResponse deleteFunction(AwsLambdaInfraConfig awsLambdaInfraConfig, String functionName,
      LogCallback logCallback) throws AwsLambdaException {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(functionName).build();

    Optional<GetFunctionResponse> existingFunctionOptional =
        getAwsLambdaFunctionFromAws(awsLambdaFunctionsInfraConfig, getFunctionRequest);

    logCallback.saveExecutionLog(format("Deleting Function: %s in region: %s %n", functionName,
        awsLambdaFunctionsInfraConfig.getRegion(), LogLevel.INFO));
    if (existingFunctionOptional.isEmpty()) {
      throw new AwsLambdaException(format("Cannot find any function with function name: %s in region: %s %n",
          functionName, awsLambdaFunctionsInfraConfig.getRegion()));
    } else {
      try {
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
      CreateFunctionRequest.Builder createFunctionRequestBuilder, String functionName) {
    CreateFunctionResponse createFunctionResponse;
    FunctionCode functionCode;

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
    logCallback.saveExecutionLog(format("Function Version: [%s]", createFunctionResponse.version(), INFO));
    logCallback.saveExecutionLog(
        format("Created Function Code Sha256: [%s]", createFunctionResponse.codeSha256(), INFO));
    logCallback.saveExecutionLog(format("Created Function ARN: [%s]", createFunctionResponse.functionArn(), INFO));

    logCallback.saveExecutionLog(format("Successfully deployed lambda function: [%s]", functionName));
    logCallback.saveExecutionLog("=================");

    return createFunctionResponse;
  }

  private CreateFunctionResponse updateFunctionForRollback(String functionConfiguration, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName,
      GetFunctionResponse existingFunction, String functionCode) throws IOException {
    logCallback.saveExecutionLog(
        format("Existing Lambda Function Code Sha256: [%s].", existingFunction.configuration().codeSha256()));

    // Update Function Code
    updateFunctionCode(functionCode, logCallback, awsLambdaFunctionsInfraConfig, functionName, functionConfiguration);

    // Update Function Configuration
    UpdateFunctionConfigurationResponse updateFunctionConfigurationResponse =
        getUpdateFunctionConfigurationResponse(functionConfiguration, awsLambdaFunctionsInfraConfig);

    waitForFunctionToUpdate(functionName, awsLambdaFunctionsInfraConfig, logCallback);

    // Publish New version
    PublishVersionResponse publishVersionResponse =
        getPublishVersionResponse(logCallback, awsLambdaFunctionsInfraConfig,
            updateFunctionConfigurationResponse.functionName(), updateFunctionConfigurationResponse.codeSha256());

    logCallback.saveExecutionLog(format("Successfully deployed lambda function: [%s]", functionName));
    logCallback.saveExecutionLog("=================");

    return (CreateFunctionResponse) CreateFunctionResponse.builder()
        .functionName(updateFunctionConfigurationResponse.functionName())
        .functionArn(updateFunctionConfigurationResponse.functionArn())
        .runtime(updateFunctionConfigurationResponse.runtimeAsString())
        .version(publishVersionResponse.version())
        .build();
  }

  private void updateFunctionCode(String functionCode, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName, String funcConfiguration)
      throws IOException {
    FunctionCodeLocation.Builder codeLocBuilder =
        parseYamlAsObject(functionCode, FunctionCodeLocation.serializableBuilderClass());
    FunctionCodeLocation functionCodeLocation = (FunctionCodeLocation) codeLocBuilder.build();

    UpdateFunctionCodeRequest updateFunctionCodeRequest = getUpdateFunctionCodeRequest(
        functionName, functionCodeLocation, funcConfiguration, logCallback, awsLambdaFunctionsInfraConfig);

    UpdateFunctionCodeResponse updateFunctionCodeResponse =
        awsLambdaClient.updateFunctionCode(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                               awsLambdaFunctionsInfraConfig.getRegion()),
            updateFunctionCodeRequest);
    waitForFunctionToUpdate(functionName, awsLambdaFunctionsInfraConfig, logCallback);

    logCallback.saveExecutionLog(format("Updated Function Code Sha256: [%s]", updateFunctionCodeResponse.codeSha256()));

    logCallback.saveExecutionLog(format("Updated Function ARN: [%s]", updateFunctionCodeResponse.functionArn()));
  }

  /**
   * Download the previous version of artifact from S3 and prepare SdkBytes
   * @param funcCodeLocation Presigned URL to download file from S3
   * @param logCallback Used for logging
   * @return SdkBytes Aws Object that is required to update function for .zip packages
   * @throws IOException
   */
  private SdkBytes downloadArtifactZipAndPrepareSdkBytes(String funcCodeLocation, LogCallback logCallback)
      throws IOException {
    // Create directory for downloading files
    String baseDir = "./aws-lambda-working-dir/";
    String workingDir = baseDir + UUIDGenerator.generateUuid();
    String artifactDir = Paths.get(workingDir).toString();

    createDirectoryIfDoesNotExist(artifactDir);
    waitForDirectoryToBeAccessibleOutOfProcess(artifactDir, 10);
    String artifactFilePath = Paths.get(artifactDir, TEMP_ARTIFACT_FILE).toAbsolutePath().toString();

    File tempArtifact = new File(artifactFilePath);

    if (!tempArtifact.createNewFile()) {
      log.error("Failed to create new file");
      logCallback.saveExecutionLog("Failed to create a file for s3 object", ERROR);
      throw new FileCreationException("Failed to create file " + tempArtifact.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }

    // Call S3 with presigned url to fetch file
    InputStream is;
    try {
      OkHttpClient client = new OkHttpClient();
      Request request = new Request.Builder().url(funcCodeLocation).build();
      Response response = client.newCall(request).execute();
      if (!response.isSuccessful()) {
        throw new InvalidRequestException("Failed to download file.");
      }
      FileOutputStream fos = new FileOutputStream(tempArtifact);
      fos.write(response.body().bytes());
      fos.close();

      is = new FileInputStream(tempArtifact);
    } catch (Exception ex) {
      log.error("Unable to download file from S3. Rollback failed");
      throw new InvalidRequestException("Unable to download file from S3. Rollback failed", ex);
    } finally {
      deleteDirectoryAndItsContentIfExists(artifactDir);
    }

    return SdkBytes.fromInputStream(is);
  }

  private UpdateFunctionCodeRequest getUpdateFunctionCodeRequest(String functionName, FunctionCodeLocation funcCodeLoc,
      String functionConfiguration, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) throws IOException {
    UpdateFunctionCodeRequest.Builder builder = UpdateFunctionCodeRequest.builder();
    builder.functionName(functionName);
    if (funcCodeLoc.repositoryType().equals("S3")) {
      // Fetch Existing function to get functions code location
      GetFunctionResponse function =
          fetchExistingFunctionWithFunctioArn(functionConfiguration, awsLambdaFunctionsInfraConfig);
      builder.zipFile(downloadArtifactZipAndPrepareSdkBytes(function.code().location(), logCallback));
    } else if (funcCodeLoc.repositoryType().equals("ECR")) {
      builder.imageUri(funcCodeLoc.imageUri());
    } else {
      String msg = format("Unsupported Repository Type %s", funcCodeLoc.repositoryType());
      log.warn(msg);
      throw new InvalidRequestException(msg);
    }
    return (UpdateFunctionCodeRequest) builder.build();
  }

  private GetFunctionResponse fetchExistingFunctionWithFunctioArn(
      String functionConfiguration, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    FunctionConfiguration.Builder funcConfigBuilder =
        parseYamlAsObject(functionConfiguration, FunctionConfiguration.serializableBuilderClass());
    FunctionConfiguration funcConfigObj = (FunctionConfiguration) funcConfigBuilder.build();

    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(funcConfigObj.functionArn()).build();

    Optional<GetFunctionResponse> existingFunctionOptional =
        getAwsLambdaFunctionFromAws(awsLambdaFunctionsInfraConfig, getFunctionRequest);

    if (existingFunctionOptional.isEmpty()) {
      throw new InvalidRequestException(format(
          "Previously deployed function doesn't exist. Unable to rollback to version: %s", funcConfigObj.version()));
    }

    return existingFunctionOptional.get();
  }

  private UpdateFunctionConfigurationResponse getUpdateFunctionConfigurationResponse(
      String functionConfiguration, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    UpdateFunctionConfigurationRequest.Builder updateFunctionConfigurationRequestBuilder =
        parseYamlAsObject(functionConfiguration, UpdateFunctionConfigurationRequest.serializableBuilderClass());

    return awsLambdaClient.updateFunctionConfiguration(
        getAwsInternalConfig(
            awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion()),
        (UpdateFunctionConfigurationRequest) updateFunctionConfigurationRequestBuilder.build());
  }

  private PublishVersionResponse getPublishVersionResponse(LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName, String codeSha256) {
    logCallback.saveExecutionLog("Publishing new version", INFO);

    PublishVersionRequest publishVersionRequest = (PublishVersionRequest) PublishVersionRequest.builder()
                                                      .functionName(functionName)
                                                      .codeSha256(codeSha256)
                                                      .build();

    PublishVersionResponse publishVersionResponse =
        awsLambdaClient.publishVersion(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                           awsLambdaFunctionsInfraConfig.getRegion()),
            publishVersionRequest);

    logCallback.saveExecutionLog(format("Published new version: [%s]", publishVersionResponse.version()));

    logCallback.saveExecutionLog(format("Published function ARN: [%s]", publishVersionResponse.functionArn()));

    return publishVersionResponse;
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

  public String toYaml(Object obj) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return objectMapper.writeValueAsString(obj);
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

  public ListVersionsByFunctionResponse listVersionsByFunction(
      String functionName, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    ListVersionsByFunctionResponse listVersionsByFunctionResult = null;
    try {
      ListVersionsByFunctionRequest listVersionsByFunctionRequest =
          (ListVersionsByFunctionRequest) ListVersionsByFunctionRequest.builder().functionName(functionName).build();
      listVersionsByFunctionResult = awsLambdaClient.listVersionsByFunction(
          getAwsInternalConfig(
              awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion()),
          listVersionsByFunctionRequest);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }

    return listVersionsByFunctionResult;
  }

  public ListAliasesResponse listAliases(
      String functionName, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    ListAliasesResponse listAliasesResponse = null;
    try {
      ListAliasesRequest listAliasesRequest =
          (ListAliasesRequest) ListAliasesRequest.builder().functionName(functionName).build();
      listAliasesResponse =
          awsLambdaClient.listAliases(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              listAliasesRequest);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }

    return listAliasesResponse;
  }

  public AwsLambdaFunctionWithActiveVersions getAwsLambdaFunctionWithActiveVersions(
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName) throws AwsLambdaException {
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
      throw new AwsLambdaException(format("Cannot find any function with function name: %s in region: %s %n",
          functionName, awsLambdaFunctionsInfraConfig.getRegion()));
    } else {
      try {
        List<String> activeVersions = getActiveVersions(functionName, awsLambdaFunctionsInfraConfig);
        ListAliasesResponse listAliasesResponse = listAliases(functionName, awsLambdaFunctionsInfraConfig);
        return convertToAwsLambdaFunctionWithActiveVersion(
            existingFunctionOptional.get(), listAliasesResponse, activeVersions);
      } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
      }
    }
  }

  public List<String> getActiveVersions(
      String functionName, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    List<String> activeVersions = new ArrayList<>();
    ListVersionsByFunctionResponse listVersionsByFunctionResult =
        listVersionsByFunction(functionName, awsLambdaFunctionsInfraConfig);
    if (listVersionsByFunctionResult == null || listVersionsByFunctionResult.versions() == null) {
      throw new InvalidRequestException("Cannot find Versions for the given function");
    }
    for (FunctionConfiguration functionConfiguration : listVersionsByFunctionResult.versions()) {
      if (!(PENDING.equals(functionConfiguration.state()) || INACTIVE.equals(functionConfiguration.state())
              || FAILED.equals(functionConfiguration.state()))) {
        activeVersions.add(functionConfiguration.version());
      }
    }
    return activeVersions;
  }

  public AwsLambdaFunctionWithActiveVersions convertToAwsLambdaFunctionWithActiveVersion(
      GetFunctionResponse result, ListAliasesResponse listAliasesResult, List<String> activeVersions) {
    final FunctionConfiguration config = result.configuration();
    final AwsLambdaFunctionWithActiveVersionsBuilder builder = AwsLambdaFunctionWithActiveVersions.builder()
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