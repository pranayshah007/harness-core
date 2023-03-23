/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_FAILED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_HINT;
import static io.harness.delegate.utils.NexusUtils.getNexusArtifactFileName;
import static io.harness.delegate.utils.NexusUtils.getNexusVersion;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static software.amazon.awssdk.services.lambda.model.State.FAILED;
import static software.amazon.awssdk.services.lambda.model.State.INACTIVE;
import static software.amazon.awssdk.services.lambda.model.State.PENDING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.beans.DecryptableEntity;
import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.exception.AwsLambdaException;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionWithActiveVersions.AwsLambdaFunctionWithActiveVersionsBuilder;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.utils.NexusVersion;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.nexus.NexusRequest;
import io.harness.pcf.PivotalClientApiException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.YamlUtils;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.service.impl.jenkins.JenkinsUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.Strings;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.AliasConfiguration;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
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
import software.amazon.awssdk.services.lambda.model.UpdateAliasRequest;
import software.amazon.awssdk.services.lambda.model.UpdateAliasResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;

@Slf4j
@OwnedBy(CDP)
public class AwsLambdaTaskHelper {
  @Inject private AwsLambdaClient awsLambdaClient;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  @Inject private AwsLambdaTaskHelperBase awsLambdaTaskHelperBase;
  @Inject private TimeLimiter timeLimiter;
  @Inject private DecryptionHelper decryptionHelper;
  @Inject private NexusMapper nexusMapper;
  @Inject private NexusService nexusService;
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject private ArtifactoryNgService artifactoryNgService;

  private final List<AwsLambdaArtifactType> ZIP_DOWNLOAD_SUPPORTED_ARTIFACTS = Arrays.asList(
      AwsLambdaArtifactType.NEXUS_PACKAGE, AwsLambdaArtifactType.JENKINS, AwsLambdaArtifactType.ARTIFACTORY);
  private final String NEXUS_FAILED_DOWNLOAD_EXPLANATION = "Unable to download nexus artifact due to: ";
  private final String NEXUS_FAILED_DOWNLOAD_HINT =
      "Review artifact configuration and nexus connector details. For any intermittent network I/O issues please check delegate connectivity with Nexus server";

  private YamlUtils yamlUtils = new YamlUtils();

  private final String ACTIVE_LAST_UPDATE_STATUS = "Successful";
  private final String FAILED_LAST_UPDATE_STATUS = "Failed";

  long TIMEOUT_IN_SECONDS = 60 * 60L;
  long WAIT_SLEEP_IN_SECONDS = 10L;

  public ObjectMapper getObjectMapper() {
    return new ObjectMapper(new YAMLFactory());
  }

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }

  public CreateFunctionResponse deployFunction(AwsLambdaInfraConfig awsLambdaInfraConfig,
      AwsLambdaArtifactConfig awsLambdaArtifactConfig, String awsLambdaManifestContent,
      List<String> awsLambdaAliasManifestContent, LogCallback logCallback) {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    CreateFunctionRequest.Builder createFunctionRequestBuilder =
        parseYamlAsObject(awsLambdaManifestContent, CreateFunctionRequest.serializableBuilderClass());

    CreateFunctionRequest createFunctionRequest = createFunctionRequestBuilder.build();

    if (isEmpty(createFunctionRequest.functionName())) {
      logCallback.saveExecutionLog(
          format("%nFunction Name not found from function definition manifest."), LogLevel.ERROR);
      throw new InvalidRequestException("Function Name not found from function definition manifest.");
    }

    String functionName = createFunctionRequest.functionName();

    GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder().functionName(functionName).build();

    try {
      Optional<GetFunctionResponse> existingFunction =
          awsLambdaClient.getFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                          awsLambdaFunctionsInfraConfig.getRegion()),
              getFunctionRequest);

      CreateFunctionResponse function;

      if (existingFunction.isEmpty()) {
        function = createFunction(awsLambdaArtifactConfig, logCallback, awsLambdaFunctionsInfraConfig,
            createFunctionRequestBuilder, functionName);
      } else {
        function = updateFunctionWithArtifact(awsLambdaArtifactConfig, awsLambdaManifestContent, logCallback,
            awsLambdaFunctionsInfraConfig, existingFunction.get());
      }

      // create or update alias
      if (CollectionUtils.isNotEmpty(awsLambdaAliasManifestContent)) {
        createOrUpdateAlias(awsLambdaAliasManifestContent, function.functionName(), function.version(),
            awsLambdaFunctionsInfraConfig, logCallback);
      }

      return function;
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  protected void createOrUpdateAlias(List<String> awsLambdaAliasManifestContent, String functionName,
      String functionVersion, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        format("%nCreate or Update Aliases for function %s with version %s. %n", functionName, functionVersion),
        LogLevel.INFO);

    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(
        awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion());

    ListAliasesRequest listAliasesRequest = ListAliasesRequest.builder().functionName(functionName).build();

    ListAliasesResponse listAliasesResponse = awsLambdaClient.listAliases(awsInternalConfig, listAliasesRequest);

    Set<String> alreadyPresentAliases =
        new HashSet<>(listAliasesResponse.aliases()
                          .stream()
                          .map(aliasConfiguration -> { return aliasConfiguration.name(); })
                          .collect(Collectors.toList()));

    for (String aliasContent : awsLambdaAliasManifestContent) {
      CreateAliasRequest.Builder aliasRequestBuilder =
          parseYamlAsObject(aliasContent, CreateAliasRequest.serializableBuilderClass());
      CreateAliasRequest createAliasRequest =
          aliasRequestBuilder.functionName(functionName).functionVersion(functionVersion).build();

      String alias = createAliasRequest.name();

      if (!alreadyPresentAliases.contains(alias)) {
        logCallback.saveExecutionLog(format("%nCreating Alias %s for function %s with version %s. %n",
                                         createAliasRequest.name(), functionName, functionVersion),
            LogLevel.INFO);

        CreateAliasResponse createAliasResponse = awsLambdaClient.createAlias(awsInternalConfig, createAliasRequest);
        logCallback.saveExecutionLog(format("%nCreated Alias %s for function %s with version %s. %n",
                                         createAliasRequest.name(), functionName, functionVersion),
            LogLevel.INFO);
      } else {
        logCallback.saveExecutionLog(format("%nUpdating Alias %s for function %s with version %s. %n",
                                         createAliasRequest.name(), functionName, functionVersion),
            LogLevel.INFO);

        UpdateAliasRequest updateAliasRequest = UpdateAliasRequest.builder()
                                                    .name(alias)
                                                    .functionName(createAliasRequest.functionName())
                                                    .functionVersion(createAliasRequest.functionVersion())
                                                    .description(createAliasRequest.description())
                                                    .build();

        UpdateAliasResponse updateAliasResponse = awsLambdaClient.updateAlias(awsInternalConfig, updateAliasRequest);

        logCallback.saveExecutionLog(format("%nUpdated Alias %s for function %s with version %s. %n",
                                         createAliasRequest.name(), functionName, functionVersion),
            LogLevel.INFO);
      }
    }

    logCallback.saveExecutionLog(format("%nDone Creating Aliases"), LogLevel.INFO);
  }

  public CreateFunctionResponse rollbackFunction(String functionName, AwsLambdaInfraConfig awsLambdaInfraConfig,
      String functionCode, String functionConfiguration, String qualifier, LogCallback logCallback,
      List<String> awsLambdaFunctionAliasDefinitionContents) throws AwsLambdaException {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder().functionName(functionName).build();

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

        CreateFunctionResponse createFunctionResponse = updateFunctionForRollback(functionConfiguration, logCallback,
            awsLambdaFunctionsInfraConfig, functionName, existingFunctionOptional.get(), functionCode);
        // create or update alias
        if (CollectionUtils.isNotEmpty(awsLambdaFunctionAliasDefinitionContents)) {
          createOrUpdateAlias(awsLambdaFunctionAliasDefinitionContents, createFunctionResponse.functionName(),
              createFunctionResponse.version(), awsLambdaFunctionsInfraConfig, logCallback);
        }
        return createFunctionResponse;
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
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, GetFunctionResponse function) throws IOException {
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

    return CreateFunctionResponse.builder()
        .functionName(updateFunctionConfigurationResponse.functionName())
        .functionArn(updateFunctionConfigurationResponse.functionArn())
        .runtime(updateFunctionConfigurationResponse.runtimeAsString())
        .version(publishVersionResponse.version())
        .build();
  }

  private void updateFunctionCodeWithArtifact(AwsLambdaArtifactConfig awsLambdaArtifactConfig, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName, String existingFunctionCodeSha)
      throws IOException {
    FunctionCode functionCode;
    functionCode = prepareFunctionCode(awsLambdaArtifactConfig, logCallback);

    UpdateFunctionCodeRequest.Builder updateFunctionCodeRequest =
        UpdateFunctionCodeRequest.builder().functionName(functionName);

    if (awsLambdaArtifactConfig instanceof AwsLambdaS3ArtifactConfig) {
      updateFunctionCodeRequest =
          UpdateFunctionCodeRequest.builder().functionName(functionName).zipFile(functionCode.zipFile());
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaEcrArtifactConfig) {
      updateFunctionCodeRequest.imageUri(functionCode.imageUri());

    } else if (ZIP_DOWNLOAD_SUPPORTED_ARTIFACTS.contains(awsLambdaArtifactConfig.getAwsLambdaArtifactType())) {
      updateFunctionCodeRequest.zipFile(functionCode.zipFile());
    } else {
      throw new InvalidRequestException("Not Support ArtifactConfig Type");
    }

    if (isFunctionCodeUpdateNeeded(awsLambdaFunctionsInfraConfig, updateFunctionCodeRequest, existingFunctionCodeSha)) {
      // Update Function Code
      updateFunctionCodeRequest.dryRun(false).publish(true);
      UpdateFunctionCodeResponse updateFunctionCodeResponse =
          awsLambdaClient.updateFunctionCode(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                                 awsLambdaFunctionsInfraConfig.getRegion()),
              updateFunctionCodeRequest.build());
      waitForFunctionToUpdate(functionName, awsLambdaFunctionsInfraConfig, logCallback);

      logCallback.saveExecutionLog(
          format("Updated Function Code Sha256: [%s]", updateFunctionCodeResponse.codeSha256()));

      logCallback.saveExecutionLog(format("Updated Function ARN: [%s]", updateFunctionCodeResponse.functionArn()));

    } else {
      logCallback.saveExecutionLog("Function code didn't change. Skip function code update", INFO);
    }
  }

  protected boolean isFunctionCodeUpdateNeeded(AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig,
      UpdateFunctionCodeRequest.Builder updateFunctionCodeRequest, String existingFunctionCodeSha) {
    UpdateFunctionCodeResponse updateFunctionCodeResponseDryRun =
        awsLambdaClient.updateFunctionCode(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                               awsLambdaFunctionsInfraConfig.getRegion()),
            updateFunctionCodeRequest.dryRun(Boolean.TRUE).build());
    return !updateFunctionCodeResponseDryRun.codeSha256().equals(existingFunctionCodeSha);
  }

  /**
   *
   * @param awsLambdaArtifactConfig Configuration information about the artifact source
   * @param logCallback
   * @return FunctionCode AWS object that contains the zip blob for package type artifacts and image uri for ECR
   *     artifacts
   * @throws IOException
   */
  protected FunctionCode prepareFunctionCode(AwsLambdaArtifactConfig awsLambdaArtifactConfig, LogCallback logCallback)
      throws IOException {
    if (awsLambdaArtifactConfig instanceof AwsLambdaS3ArtifactConfig) {
      AwsLambdaS3ArtifactConfig awsLambdaS3ArtifactConfig = (AwsLambdaS3ArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder()
          .zipFile(awsLambdaTaskHelperBase.downloadArtifactFromS3BucketAndPrepareSdkBytes(
              awsLambdaS3ArtifactConfig, logCallback))
          .build();
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaCustomArtifactConfig) {
      AwsLambdaCustomArtifactConfig awsLambdaCustomArtifactConfig =
          (AwsLambdaCustomArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder()
          .s3Bucket(awsLambdaCustomArtifactConfig.getBucketName())
          .s3Key(awsLambdaCustomArtifactConfig.getFilePath())
          .build();
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaEcrArtifactConfig) {
      AwsLambdaEcrArtifactConfig awsLambdaEcrArtifactConfig = (AwsLambdaEcrArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder().imageUri(awsLambdaEcrArtifactConfig.getImage()).build();
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaNexusArtifactConfig) {
      AwsLambdaNexusArtifactConfig awsLambdaNexusArtifactConfig =
          (AwsLambdaNexusArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder()
          .zipFile(SdkBytes.fromInputStream(downloadFromNexus(awsLambdaNexusArtifactConfig, logCallback)))
          .build();
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaJenkinsArtifactConfig) {
      AwsLambdaJenkinsArtifactConfig awsLambdaJenkinsArtifactConfig =
          (AwsLambdaJenkinsArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder()
          .zipFile(SdkBytes.fromInputStream(downloadFromJenkins(awsLambdaJenkinsArtifactConfig, logCallback)))
          .build();
    } else if (awsLambdaArtifactConfig instanceof AwsLambdaArtifactoryArtifactConfig) {
      AwsLambdaArtifactoryArtifactConfig awsLambdaArtifactoryArtifactConfig =
          (AwsLambdaArtifactoryArtifactConfig) awsLambdaArtifactConfig;
      return FunctionCode.builder()
          .zipFile(SdkBytes.fromInputStream(downloadFromArtifactory(awsLambdaArtifactoryArtifactConfig, logCallback)))
          .build();
    }
    throw new InvalidRequestException("Not Support ArtifactConfig Type");
  }

  private InputStream downloadFromNexus(AwsLambdaNexusArtifactConfig artifactConfig, LogCallback logCallback) {
    if (!(artifactConfig.getConnectorConfig() instanceof NexusConnectorDTO)) {
      throw NestedExceptionUtils.hintWithExplanationException("Configure nexus connector for nexus configuration",
          format("Unexpected connector type '%s' for nexus configuration",
              artifactConfig.getConnectorConfig().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("connectorConfig",
              format("Invalid connector type '%s', expected '%s'",
                  artifactConfig.getConnectorConfig().getClass().getSimpleName(),
                  NexusConnectorDTO.class.getSimpleName()))));
    }

    NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) artifactConfig.getConnectorConfig();
    decryptEntity(
        decryptionHelper, nexusConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

    NexusVersion nexusVersion = getNexusVersion(nexusConnectorDTO);
    String artifactUrl = artifactConfig.getArtifactUrl();
    NexusRequest nexusRequest =
        nexusMapper.toNexusRequest(nexusConnectorDTO, artifactConfig.isCertValidationRequired(), artifactUrl);

    try {
      String artifactName =
          getNexusArtifactFileName(nexusVersion, artifactConfig.getRepositoryFormat(), artifactConfig.getMetadata());
      logCallback.saveExecutionLog(
          color(format("Downloading artifact '%s' from nexus url '%s'", artifactName, artifactUrl), LogColor.White,
              LogWeight.Bold));

      return nexusService.downloadArtifactByUrl(nexusRequest, artifactName, artifactUrl).getValue();
    } catch (WingsException e) {
      WingsException sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      logCallback.saveExecutionLog(format(
          "Failed to download artifact '%s' due to: %s", artifactUrl, ExceptionUtils.getMessage(sanitizedException)));
      throw sanitizedException;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      String message = ExceptionUtils.getMessage(sanitizedException);
      log.error("Failure in downloading artifact from Nexus", sanitizedException);
      logCallback.saveExecutionLog(format(
          "Failed to download artifact '%s' due to: %s", artifactUrl, ExceptionUtils.getMessage(sanitizedException)));
      throw NestedExceptionUtils.hintWithExplanationException(NEXUS_FAILED_DOWNLOAD_HINT,
          NEXUS_FAILED_DOWNLOAD_EXPLANATION + message, new PivotalClientApiException(message));
    }
  }

  private InputStream downloadFromJenkins(AwsLambdaJenkinsArtifactConfig artifactConfig, LogCallback logCallback) {
    validateJenkinsArtifact(artifactConfig, logCallback);
    Pair<String, InputStream> pair = null;

    try {
      JenkinsConnectorDTO jenkinsConnectorDto = (JenkinsConnectorDTO) artifactConfig.getConnectorConfig();
      decryptEntity(
          decryptionHelper, jenkinsConnectorDto.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

      logCallback.saveExecutionLog(
          color(format("Downloading jenkins artifact: %s/job/%s/%s/artifact/%s", jenkinsConnectorDto.getJenkinsUrl(),
                    artifactConfig.getJobName(), artifactConfig.getBuild(), artifactConfig.getArtifactPath()),
              White, Bold));
      Jenkins jenkins = configureJenkins((JenkinsConnectorDTO) artifactConfig.getConnectorConfig());
      if (!isNull(jenkins)) {
        pair = jenkins.downloadArtifact(
            artifactConfig.getJobName(), artifactConfig.getBuild(), artifactConfig.getArtifactPath());
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading jenkins artifact ", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download jenkins artifact. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(JENKINS_ARTIFACT_DOWNLOAD_HINT,
          format(JENKINS_ARTIFACT_DOWNLOAD_EXPLANATION, artifactConfig.getIdentifier()),
          new InvalidArgumentsException(format(JENKINS_ARTIFACT_DOWNLOAD_FAILED, artifactConfig.getIdentifier())));
    }
    if (pair != null) {
      return pair.getRight();
    } else {
      throw new InvalidArgumentsException(format(JENKINS_ARTIFACT_DOWNLOAD_FAILED, artifactConfig.getIdentifier()));
    }
  }

  private InputStream downloadFromArtifactory(
      AwsLambdaArtifactoryArtifactConfig artifactConfig, LogCallback logCallback) {
    if (!(artifactConfig.getConnectorConfig() instanceof ArtifactoryConnectorDTO)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Configure artifactory connector for artifactory configuration",
          format("Unexpected connector type '%s' for artifactory configuration",
              artifactConfig.getConnectorConfig().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("connectorConfig",
              format("Invalid connector type '%s', expected '%s'",
                  artifactConfig.getConnectorConfig().getClass().getSimpleName(),
                  ArtifactoryConnectorDTO.class.getSimpleName()))));
    }

    ArtifactoryConnectorDTO artifactoryConnector = (ArtifactoryConnectorDTO) artifactConfig.getConnectorConfig();
    decryptEntity(
        decryptionHelper, artifactoryConnector.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnector);
    String artifactPath = artifactConfig.getArtifactPaths().get(0);
    logCallback.saveExecutionLog(color(format("Downloading artifact '%s' from artifactory server %s", artifactPath,
                                           artifactoryConfigRequest.getArtifactoryUrl()),
        LogColor.White, LogWeight.Bold));

    return artifactoryNgService.downloadArtifacts(artifactoryConfigRequest, artifactConfig.getRepository(),
        artifactConfig.toMetadata(), ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactName);
  }

  private Jenkins configureJenkins(JenkinsConnectorDTO jenkinsConnectorDto) {
    JenkinsAuthType authType = jenkinsConnectorDto.getAuth().getAuthType();
    Jenkins jenkins = null;
    if (JenkinsAuthType.USER_PASSWORD.equals(authType)) {
      JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO =
          (JenkinsUserNamePasswordDTO) jenkinsConnectorDto.getAuth().getCredentials();
      JenkinsConfig jenkinsConfig = JenkinsConfig.builder()
                                        .jenkinsUrl(jenkinsConnectorDto.getJenkinsUrl())
                                        .username(jenkinsUserNamePasswordDTO.getUsername())
                                        .password(jenkinsUserNamePasswordDTO.getPasswordRef().getDecryptedValue())
                                        .build();
      jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
    } else if (JenkinsAuthType.BEARER_TOKEN.equals(authType)) {
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO =
          (JenkinsBearerTokenDTO) jenkinsConnectorDto.getAuth().getCredentials();
      JenkinsConfig jenkinsConfig = JenkinsConfig.builder()
                                        .jenkinsUrl(jenkinsConnectorDto.getJenkinsUrl())
                                        .token(jenkinsBearerTokenDTO.getTokenRef().getDecryptedValue())
                                        .authMechanism(JenkinsUtils.TOKEN_FIELD)
                                        .build();
      jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
    }
    return jenkins;
  }

  private void validateJenkinsArtifact(AwsLambdaJenkinsArtifactConfig artifactConfig, LogCallback logCallback) {
    if (EmptyPredicate.isEmpty(artifactConfig.getArtifactPath())) {
      logCallback.saveExecutionLog("artifact Path is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, artifactConfig.getIdentifier()),
          new InvalidArgumentsException("not able to find artifact Path"));
    }
  }

  protected void decryptEntity(DecryptionHelper decryptionHelper, List<DecryptableEntity> decryptableEntities,
      List<EncryptedDataDetail> encryptedDataDetails) {
    if (isNotEmpty(decryptableEntities)) {
      for (DecryptableEntity decryptableEntity : decryptableEntities) {
        decryptionHelper.decrypt(decryptableEntity, encryptedDataDetails);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(decryptableEntity, encryptedDataDetails);
      }
    }
  }

  public DeleteFunctionResponse deleteFunction(AwsLambdaInfraConfig awsLambdaInfraConfig, String functionName,
      LogCallback logCallback) throws AwsLambdaException {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = (AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig;

    GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder().functionName(functionName).build();

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
            DeleteFunctionRequest.builder().functionName(functionName).build());
      } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
      }
    }
  }

  protected CreateFunctionResponse createFunction(AwsLambdaArtifactConfig awsLambdaArtifactConfig,
      LogCallback logCallback, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig,
      CreateFunctionRequest.Builder createFunctionRequestBuilder, String functionName) throws IOException {
    CreateFunctionResponse createFunctionResponse;
    FunctionCode functionCode;

    logCallback.saveExecutionLog(format("Creating Function: %s in region: %s %n", functionName,
        awsLambdaFunctionsInfraConfig.getRegion(), LogLevel.INFO));

    functionCode = prepareFunctionCode(awsLambdaArtifactConfig, logCallback);
    createFunctionRequestBuilder.code(functionCode);
    createFunctionRequestBuilder.publish(true);

    if (awsLambdaArtifactConfig instanceof AwsLambdaEcrArtifactConfig) {
      createFunctionRequestBuilder.packageType(PackageType.IMAGE);
    } else {
      createFunctionRequestBuilder.packageType(PackageType.ZIP);
    }

    createFunctionResponse =
        awsLambdaClient.createFunction(getAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(),
                                           awsLambdaFunctionsInfraConfig.getRegion()),
            createFunctionRequestBuilder.build());

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

    return CreateFunctionResponse.builder()
        .functionName(updateFunctionConfigurationResponse.functionName())
        .functionArn(updateFunctionConfigurationResponse.functionArn())
        .runtime(updateFunctionConfigurationResponse.runtimeAsString())
        .version(publishVersionResponse.version())
        .build();
  }

  protected void updateFunctionCode(String functionCode, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName, String funcConfiguration)
      throws IOException {
    FunctionCodeLocation.Builder codeLocBuilder =
        parseYamlAsObject(functionCode, FunctionCodeLocation.serializableBuilderClass());
    FunctionCodeLocation functionCodeLocation = codeLocBuilder.build();

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

  @VisibleForTesting
  protected UpdateFunctionCodeRequest getUpdateFunctionCodeRequest(String functionName,
      FunctionCodeLocation funcCodeLoc, String functionConfiguration, LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) throws IOException {
    UpdateFunctionCodeRequest.Builder builder = UpdateFunctionCodeRequest.builder();
    builder.functionName(functionName);
    if (funcCodeLoc.repositoryType().equals("S3")) {
      // Fetch Existing function to get functions code location
      GetFunctionResponse function =
          fetchExistingFunctionWithFunctionArn(functionConfiguration, awsLambdaFunctionsInfraConfig);
      builder.zipFile(awsLambdaTaskHelperBase.downloadArtifactZipAndPrepareSdkBytesForRollback(
          function.code().location(), logCallback));
    } else if (funcCodeLoc.repositoryType().equals("ECR")) {
      builder.imageUri(funcCodeLoc.imageUri());
    } else {
      String msg = format("Unsupported Repository Type %s", funcCodeLoc.repositoryType());
      log.warn(msg);
      throw new InvalidRequestException(msg);
    }
    return builder.build();
  }

  @VisibleForTesting
  protected GetFunctionResponse fetchExistingFunctionWithFunctionArn(
      String functionConfiguration, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig) {
    FunctionConfiguration.Builder funcConfigBuilder =
        parseYamlAsObject(functionConfiguration, FunctionConfiguration.serializableBuilderClass());
    FunctionConfiguration funcConfigObj = funcConfigBuilder.build();

    GetFunctionRequest getFunctionRequest =
        GetFunctionRequest.builder().functionName(funcConfigObj.functionArn()).build();

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
        updateFunctionConfigurationRequestBuilder.build());
  }

  @VisibleForTesting
  protected PublishVersionResponse getPublishVersionResponse(LogCallback logCallback,
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, String functionName, String codeSha256) {
    logCallback.saveExecutionLog("Publishing new version", INFO);

    PublishVersionRequest publishVersionRequest =
        PublishVersionRequest.builder().functionName(functionName).codeSha256(codeSha256).build();

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
              GetFunctionConfigurationRequest.builder().functionName(functionName).build();
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
    ListVersionsByFunctionResponse listVersionsByFunctionResult;
    try {
      ListVersionsByFunctionRequest listVersionsByFunctionRequest =
          ListVersionsByFunctionRequest.builder().functionName(functionName).build();
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
    ListAliasesResponse listAliasesResponse;
    try {
      ListAliasesRequest listAliasesRequest = ListAliasesRequest.builder().functionName(functionName).build();
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
    GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder().functionName(functionName).build();

    Optional<GetFunctionResponse> existingFunctionOptional;
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