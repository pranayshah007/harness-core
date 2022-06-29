/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_COMMAND_FAILURE;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_COMMAND_FAILURE_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_COMMAND_FAILURE_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_MANIFEST_PROCESSING_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_MANIFEST_PROCESSING_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_MANIFEST_PROCESSING_HINT;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaCloudFormationSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction.ServerlessAwsLambdaFunctionBuilder;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessPrepareRollbackDataRequest;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessAwsLambdaRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.YamlUtils;
import io.harness.serverless.AbstractExecutable;
import io.harness.serverless.ConfigCredentialCommand;
import io.harness.serverless.DeployCommand;
import io.harness.serverless.DeployListCommand;
import io.harness.serverless.RemoveCommand;
import io.harness.serverless.RollbackCommand;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandTaskHelper;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ServerlessAwsCommandTaskHelper {
  @Inject private ServerlessTaskPluginHelper serverlessTaskPluginHelper;
  @Inject protected AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Inject protected AwsApiHelperService awsApiHelperService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  private static String AWS_LAMBDA_FUNCTION_RESOURCE_TYPE = "AWS::Lambda::Function";
  private static String AWS_LAMBDA_FUNCTION_NAME_PROPERTY_KEY = "FunctionName";
  private static String AWS_LAMBDA_FUNCTION_HANDLER_PROPERTY_KEY = "Handler";
  private static String AWS_LAMBDA_FUNCTION_MEMORY_PROPERTY_KEY = "MemorySize";
  private static String AWS_LAMBDA_FUNCTION_RUNTIME_PROPERTY_KEY = "Runtime";
  private static String AWS_LAMBDA_FUNCTION_TIMEOUT_PROPERTY_KEY = "Timeout";
  private static String CLOUDFORMATION_UPDATE_FILE = "cloudformation-template-update-stack.json";
  private static String NEW_LINE_REGEX = "\\r?\\n";
  private static String WHITESPACE_REGEX = "[\\s]";
  private static String DEPLOY_TIMESTAMP_REGEX = ".*Timestamp:\\s([0-9])*";
  private static String SERVICE_NAME_REGEX = ".*service:\\s.*";
  private static String LOGICAL_ID_FOR_SERVERLESS_DEPLOYMENT_BUCKET_RESOURCE = "ServerlessDeploymentBucket";

  public ServerlessCliResponse configCredential(ServerlessClient serverlessClient,
      ServerlessAwsLambdaConfig serverlessAwsLambdaConfig, ServerlessDelegateTaskParams serverlessDelegateTaskParams,
      LogCallback executionLogCallback, boolean overwrite, long timeoutInMillis)
      throws InterruptedException, IOException, TimeoutException {
    executionLogCallback.saveExecutionLog("Setting up AWS config credentials..\n");
    ConfigCredentialCommand command = serverlessClient.configCredential()
                                          .provider(serverlessAwsLambdaConfig.getProvider())
                                          .key(serverlessAwsLambdaConfig.getAccessKey())
                                          .secret(serverlessAwsLambdaConfig.getSecretKey())
                                          .overwrite(overwrite);
    return ServerlessCommandTaskHelper.executeCommand(
        command, serverlessDelegateTaskParams.getWorkingDirectory(), executionLogCallback, false, timeoutInMillis);
  }

  public ServerlessCliResponse deploy(ServerlessClient serverlessClient,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, LogCallback executionLogCallback,
      ServerlessAwsLambdaDeployConfig serverlessAwsLambdaDeployConfig,
      ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig, long timeoutInMillis,
      ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig)
      throws InterruptedException, IOException, TimeoutException {
    executionLogCallback.saveExecutionLog("Serverless Deployment Starting..\n");
    DeployCommand command = serverlessClient.deploy()
                                .options(serverlessAwsLambdaDeployConfig.getCommandOptions())
                                .region(serverlessAwsLambdaInfraConfig.getRegion())
                                .stage(serverlessAwsLambdaInfraConfig.getStage());
    if (EmptyPredicate.isNotEmpty(serverlessAwsLambdaManifestConfig.getConfigOverridePath())) {
      command.config(serverlessAwsLambdaManifestConfig.getConfigOverridePath());
    }
    return ServerlessCommandTaskHelper.executeCommand(
        command, serverlessDelegateTaskParams.getWorkingDirectory(), executionLogCallback, true, timeoutInMillis);
  }

  public boolean cloudFormationStackExists(
      LogCallback executionLogCallback, ServerlessCommandRequest serverlessCommandRequest, String manifestContent) {
    ServerlessAwsLambdaManifestSchema serverlessManifestSchema =
        parseServerlessManifest(executionLogCallback, manifestContent);
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessCommandRequest.getServerlessInfraConfig();
    String cloudFormationStackName =
        serverlessManifestSchema.getService() + "-" + serverlessAwsLambdaInfraConfig.getStage();
    String region = serverlessAwsLambdaInfraConfig.getRegion();

    return awsCFHelperServiceDelegate.stackExists(
        awsNgConfigMapper.createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO()), region,
        cloudFormationStackName);
  }

  public String getCurrentCloudFormationTemplate(
      LogCallback executionLogCallback, ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest) {
    ServerlessAwsLambdaManifestSchema serverlessManifestSchema =
        parseServerlessManifest(executionLogCallback, serverlessPrepareRollbackDataRequest.getManifestContent());
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    String cloudFormationStackName =
        serverlessManifestSchema.getService() + "-" + serverlessAwsLambdaInfraConfig.getStage();
    String region = serverlessAwsLambdaInfraConfig.getRegion();

    return awsCFHelperServiceDelegate.getStackBody(
        awsNgConfigMapper.createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO()), region,
        cloudFormationStackName);
  }

  public ServerlessCliResponse deployList(ServerlessClient serverlessClient,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, LogCallback executionLogCallback,
      ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig, long timeoutInMillis,
      ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig)
      throws InterruptedException, IOException, TimeoutException {
    executionLogCallback.saveExecutionLog("Fetching previous successful deployments..\n");
    DeployListCommand command = serverlessClient.deployList()
                                    .region(serverlessAwsLambdaInfraConfig.getRegion())
                                    .stage(serverlessAwsLambdaInfraConfig.getStage());
    if (EmptyPredicate.isNotEmpty(serverlessAwsLambdaManifestConfig.getConfigOverridePath())) {
      command.config(serverlessAwsLambdaManifestConfig.getConfigOverridePath());
    }
    return ServerlessCommandTaskHelper.executeCommand(
        command, serverlessDelegateTaskParams.getWorkingDirectory(), executionLogCallback, true, timeoutInMillis);
  }

  public ServerlessCliResponse remove(ServerlessClient serverlessClient,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, LogCallback executionLogCallback, long timeoutInMillis,
      ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig,
      ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig)
      throws InterruptedException, IOException, TimeoutException {
    executionLogCallback.saveExecutionLog("Serverless Remove Starting..\n");
    RemoveCommand command = serverlessClient.remove()
                                .stage(serverlessAwsLambdaInfraConfig.getStage())
                                .region(serverlessAwsLambdaInfraConfig.getRegion());
    if (EmptyPredicate.isNotEmpty(serverlessAwsLambdaManifestConfig.getConfigOverridePath())) {
      command.config(serverlessAwsLambdaManifestConfig.getConfigOverridePath());
    }
    return ServerlessCommandTaskHelper.executeCommand(
        command, serverlessDelegateTaskParams.getWorkingDirectory(), executionLogCallback, true, timeoutInMillis);
  }

  public ServerlessCliResponse rollback(ServerlessClient serverlessClient,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, LogCallback executionLogCallback,
      ServerlessAwsLambdaRollbackConfig serverlessAwsLambdaRollbackConfig, long timeoutInMillis,
      ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig,
      ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig)
      throws InterruptedException, IOException, TimeoutException {
    executionLogCallback.saveExecutionLog("Serverless Rollback Starting..\n");
    RollbackCommand command = serverlessClient.rollback()
                                  .timeStamp(serverlessAwsLambdaRollbackConfig.getPreviousVersionTimeStamp())
                                  .stage(serverlessAwsLambdaInfraConfig.getStage())
                                  .region(serverlessAwsLambdaInfraConfig.getRegion());
    if (EmptyPredicate.isNotEmpty(serverlessAwsLambdaManifestConfig.getConfigOverridePath())) {
      command.config(serverlessAwsLambdaManifestConfig.getConfigOverridePath());
    }
    return ServerlessCommandTaskHelper.executeCommand(
        command, serverlessDelegateTaskParams.getWorkingDirectory(), executionLogCallback, true, timeoutInMillis);
  }

  public ServerlessAwsLambdaManifestSchema parseServerlessManifest(
      LogCallback executionLogCallback, String manifestContent) {
    YamlUtils yamlUtils = new YamlUtils();
    try {
      return yamlUtils.read(manifestContent, ServerlessAwsLambdaManifestSchema.class);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in processing manifest file", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to process manifest file with error: " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(SERVERLESS_MANIFEST_PROCESSING_HINT,
          SERVERLESS_MANIFEST_PROCESSING_EXPLANATION,
          new ServerlessAwsLambdaRuntimeException(SERVERLESS_MANIFEST_PROCESSING_FAILED));
    }
  }

  public void installPlugins(ServerlessAwsLambdaManifestSchema serverlessAwsLambdaManifestSchema,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, LogCallback executionLogCallback,
      ServerlessClient serverlessClient, long timeoutInMillis,
      ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig)
      throws InterruptedException, TimeoutException, IOException {
    ServerlessCliResponse response;
    if (EmptyPredicate.isNotEmpty(serverlessAwsLambdaManifestSchema.getPlugins())) {
      executionLogCallback.saveExecutionLog("Plugin Installation starting..\n");
      List<String> plugins = serverlessAwsLambdaManifestSchema.getPlugins();
      for (String plugin : plugins) {
        response = serverlessTaskPluginHelper.installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient,
            plugin, executionLogCallback, timeoutInMillis, serverlessAwsLambdaManifestConfig.getConfigOverridePath());
        if (response.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
          executionLogCallback.saveExecutionLog(
              format("%nPlugin Installation failed.. "), ERROR, CommandExecutionStatus.FAILURE);
          handleCommandExecutionFailure(response, serverlessClient.plugin());
        }
      }
      executionLogCallback.saveExecutionLog(
          color(format("%nInstalled all required plugins successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
    } else {
      executionLogCallback.saveExecutionLog(
          color(
              format("%nSkipping plugin installation, found no plugins in config..%n"), LogColor.White, LogWeight.Bold),
          INFO);
    }
  }

  public void setUpConfigureCredential(ServerlessAwsLambdaConfig serverlessAwsLambdaConfig,
      LogCallback executionLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams,
      String serverlessAwsLambdaCredentialType, ServerlessClient serverlessClient, long timeoutInMillis)
      throws Exception {
    try {
      if (serverlessAwsLambdaCredentialType.equals(AwsCredentialType.MANUAL_CREDENTIALS.name())) {
        ServerlessCliResponse response = configCredential(serverlessClient, serverlessAwsLambdaConfig,
            serverlessDelegateTaskParams, executionLogCallback, true, timeoutInMillis);

        if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
          executionLogCallback.saveExecutionLog(
              color(format("%nConfig Credential command executed successfully..%n"), LogColor.White, LogWeight.Bold),
              INFO);
          executionLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        } else {
          executionLogCallback.saveExecutionLog(
              color(format("%nConfig Credential command failed..%n"), LogColor.Red, LogWeight.Bold), ERROR,
              CommandExecutionStatus.FAILURE);
          handleCommandExecutionFailure(response, serverlessClient.configCredential());
        }
      } else {
        executionLogCallback.saveExecutionLog(
            format("skipping configure credentials command..%n%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      }
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(
          color(format("%n configure credential failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw ex;
    }
  }

  public void handleCommandExecutionFailure(ServerlessCliResponse response, AbstractExecutable command) {
    Optional<String> commandOptional = AbstractExecutable.getPrintableCommand(command.command());
    String printCommand = command.command();
    if (commandOptional.isPresent()) {
      printCommand = commandOptional.get();
    }
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(
        new ServerlessAwsLambdaRuntimeException(format(SERVERLESS_COMMAND_FAILURE, printCommand)));
    throw NestedExceptionUtils.hintWithExplanationException(format(SERVERLESS_COMMAND_FAILURE_HINT, printCommand),
        format(SERVERLESS_COMMAND_FAILURE_EXPLANATION, printCommand), sanitizedException);
  }

  public List<ServerlessAwsLambdaFunction> fetchFunctionOutputFromCloudFormationTemplate(
      String cloudFormationTemplateDirectory) throws IOException {
    String cloudFormationTemplatePath =
        Paths.get(cloudFormationTemplateDirectory, CLOUDFORMATION_UPDATE_FILE).toString();
    String cloudFormationTemplateContent =
        FileIo.getFileContentsWithSharedLockAcrossProcesses(cloudFormationTemplatePath);
    if (EmptyPredicate.isEmpty(cloudFormationTemplateContent)) {
      return Collections.emptyList();
    }
    YamlUtils yamlUtils = new YamlUtils();
    ServerlessAwsLambdaCloudFormationSchema serverlessAwsCloudFormationTemplate =
        yamlUtils.read(cloudFormationTemplateContent, ServerlessAwsLambdaCloudFormationSchema.class);
    Collection<ServerlessAwsLambdaCloudFormationSchema.Resource> resources =
        serverlessAwsCloudFormationTemplate.getResources().values();
    List<Map<String, Object>> functionPropertyMaps =
        resources.stream()
            .filter(resource -> resource.getType().equals(AWS_LAMBDA_FUNCTION_RESOURCE_TYPE))
            .map(ServerlessAwsLambdaCloudFormationSchema.Resource::getProperties)
            .collect(Collectors.toList());
    List<ServerlessAwsLambdaFunction> serverlessAwsLambdaFunctions = new ArrayList<>();
    for (Map<String, Object> functionPropertyMap : functionPropertyMaps) {
      if (!functionPropertyMap.containsKey(AWS_LAMBDA_FUNCTION_NAME_PROPERTY_KEY)) {
        continue;
      }
      ServerlessAwsLambdaFunctionBuilder serverlessAwsLambdaFunctionBuilder = ServerlessAwsLambdaFunction.builder();
      serverlessAwsLambdaFunctionBuilder.functionName(
          functionPropertyMap.get(AWS_LAMBDA_FUNCTION_NAME_PROPERTY_KEY).toString());
      if (functionPropertyMap.containsKey(AWS_LAMBDA_FUNCTION_MEMORY_PROPERTY_KEY)) {
        serverlessAwsLambdaFunctionBuilder.memorySize(
            functionPropertyMap.get(AWS_LAMBDA_FUNCTION_MEMORY_PROPERTY_KEY).toString());
      }
      if (functionPropertyMap.containsKey(AWS_LAMBDA_FUNCTION_HANDLER_PROPERTY_KEY)) {
        serverlessAwsLambdaFunctionBuilder.handler(
            functionPropertyMap.get(AWS_LAMBDA_FUNCTION_HANDLER_PROPERTY_KEY).toString());
      }
      if (functionPropertyMap.containsKey(AWS_LAMBDA_FUNCTION_RUNTIME_PROPERTY_KEY)) {
        serverlessAwsLambdaFunctionBuilder.runTime(
            functionPropertyMap.get(AWS_LAMBDA_FUNCTION_RUNTIME_PROPERTY_KEY).toString());
      }
      if (functionPropertyMap.containsKey(AWS_LAMBDA_FUNCTION_TIMEOUT_PROPERTY_KEY)) {
        serverlessAwsLambdaFunctionBuilder.timeout(
            Integer.parseInt(functionPropertyMap.get(AWS_LAMBDA_FUNCTION_TIMEOUT_PROPERTY_KEY).toString()));
      }
      serverlessAwsLambdaFunctions.add(serverlessAwsLambdaFunctionBuilder.build());
    }
    return serverlessAwsLambdaFunctions;
  }

  public List<String> getDeployListTimeStamps(String deployListOutput) {
    List<String> timeStamps = new ArrayList<>();
    if (EmptyPredicate.isEmpty(deployListOutput)) {
      return timeStamps;
    }
    Pattern deployTimeOutPattern = Pattern.compile(DEPLOY_TIMESTAMP_REGEX);
    List<String> outputLines = Arrays.asList(deployListOutput.split(NEW_LINE_REGEX));
    List<String> filteredOutputLines = outputLines.stream()
                                           .filter(outputLine -> deployTimeOutPattern.matcher(outputLine).matches())
                                           .collect(Collectors.toList());
    timeStamps =
        filteredOutputLines.stream()
            .map(filteredOutputLine -> Iterables.getLast(Arrays.asList(filteredOutputLine.split(WHITESPACE_REGEX)), ""))
            .collect(Collectors.toList());

    return timeStamps;
  }

  public String getServiceName(String inputFile) {
    String serviceName = "";
    if (EmptyPredicate.isEmpty(inputFile)) {
      return serviceName;
    }
    Pattern deployTimeOutPattern = Pattern.compile(SERVICE_NAME_REGEX);
    List<String> outputLines = Arrays.asList(inputFile.split(NEW_LINE_REGEX));
    List<String> filteredOutputLines = outputLines.stream()
                                           .filter(outputLine -> deployTimeOutPattern.matcher(outputLine).matches())
                                           .collect(Collectors.toList());
    serviceName =
        filteredOutputLines.stream()
            .map(filteredOutputLine -> Iterables.getLast(Arrays.asList(filteredOutputLine.split(WHITESPACE_REGEX)), ""))
            .collect(Collectors.toList())
            .get(0);

    return serviceName.trim();
  }

  public Optional<String> getServerlessDeploymentBucketName(LogCallback executionLogCallback,
      ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest, String manifestContent) {
    ServerlessAwsLambdaManifestSchema serverlessManifestSchema =
        parseServerlessManifest(executionLogCallback, manifestContent);
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    String cloudFormationStackName =
        serverlessManifestSchema.getService() + "-" + serverlessAwsLambdaInfraConfig.getStage();
    String region = serverlessAwsLambdaInfraConfig.getRegion();

    return Optional.of(awsCFHelperServiceDelegate.getPhysicalIdBasedOnLogicalId(
        awsNgConfigMapper.createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO()), region,
        cloudFormationStackName, LOGICAL_ID_FOR_SERVERLESS_DEPLOYMENT_BUCKET_RESOURCE));
  }

  public Optional<String> getLastDeployedTimestamp(LogCallback executionLogCallback, List<String> timeStampsList,
      ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest) throws IOException {
    Optional<String> optionalBucketName = getServerlessDeploymentBucketName(executionLogCallback,
        serverlessPrepareRollbackDataRequest, serverlessPrepareRollbackDataRequest.getManifestContent());
    String serviceName = getServiceName(serverlessPrepareRollbackDataRequest.getManifestContent());
    String cloudFormationTemplate =
        getCurrentCloudFormationTemplate(executionLogCallback, serverlessPrepareRollbackDataRequest);

    if (!optionalBucketName.isPresent() || serviceName.isEmpty() || cloudFormationTemplate.isEmpty()) {
      return Optional.empty();
    }

    String bucketName = optionalBucketName.get();
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    String region = serverlessAwsLambdaInfraConfig.getRegion();
    AwsInternalConfig awsConfig =
        awsNgConfigMapper.createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO());

    ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
    String objectKeyPrefix = "serverless/" + serviceName + "/" + serverlessAwsLambdaInfraConfig.getStage() + "/";
    listObjectsV2Request.setPrefix(objectKeyPrefix);
    listObjectsV2Request.withBucketName(bucketName).withMaxKeys(500);

    List<String> objectKeyList = Lists.newArrayList();
    ListObjectsV2Result result;
    do {
      result = awsApiHelperService.listObjectsInS3(awsConfig, region, listObjectsV2Request);
      List<S3ObjectSummary> objectSummaryList = result.getObjectSummaries();
      // in descending order. The most recent one comes first
      objectSummaryList.sort((o1, o2) -> o2.getLastModified().compareTo(o1.getLastModified()));

      List<String> objectKeyListForCurrentBatch =
          objectSummaryList.stream()
              .filter(objectSummary
                  -> !objectSummary.getKey().endsWith("/")
                      && objectSummary.getKey().contains("compiled-cloudformation-template.json"))
              .map(S3ObjectSummary::getKey)
              .collect(toList());
      objectKeyList.addAll(objectKeyListForCurrentBatch);
      listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
    } while (result.isTruncated());

    // We are not using stream here since addDataToResponse throws a bunch of exceptions and we want to throw them back
    // to the caller.
    for (String objectKey : objectKeyList) {
      Pair<String, InputStream> stringInputStreamPair = downloadArtifact(awsConfig, region, bucketName, objectKey);
      if (stringInputStreamPair != null) {
        String object = IOUtils.toString(stringInputStreamPair.getValue(), StandardCharsets.UTF_8);
        if (object.equals(cloudFormationTemplate)) {
          String trimmedObjectKey = objectKey.replaceFirst(objectKeyPrefix, "");
          String timeStamp = trimmedObjectKey.substring(0, trimmedObjectKey.indexOf('-'));
          if (timeStampsList.contains(timeStamp)) {
            return Optional.of(timeStamp);
          }
        }
      }
    }

    return Optional.empty();
  }

  private Pair<String, InputStream> downloadArtifact(
      AwsInternalConfig awsInternalConfig, String region, String bucketName, String key) {
    S3Object object = awsApiHelperService.getObjectFromS3(awsInternalConfig, region, bucketName, key);
    if (object != null) {
      return Pair.of(object.getKey(), object.getObjectContent());
    }
    return null;
  }
}
