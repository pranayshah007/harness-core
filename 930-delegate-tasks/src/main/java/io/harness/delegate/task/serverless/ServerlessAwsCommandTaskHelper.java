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

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaCloudFormationSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction.ServerlessAwsLambdaFunctionBuilder;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessAwsLambdaRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.serializer.YamlUtils;
import io.harness.serverless.AbstractExecutable;
import io.harness.serverless.ConfigCredentialCommand;
import io.harness.serverless.DeployCommand;
import io.harness.serverless.DeployListCommand;
import io.harness.serverless.RollbackCommand;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandTaskHelper;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ServerlessAwsCommandTaskHelper {
  @Inject private ServerlessTaskPluginHelper serverlessTaskPluginHelper;
  @Inject protected AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
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

  public String getCurrentCloudFormationTemplate(
      LogCallback executionLogCallback, ServerlessDeployRequest serverlessDeployRequest) {
    ServerlessAwsLambdaManifestSchema serverlessManifestSchema =
        parseServerlessManifest(executionLogCallback, serverlessDeployRequest.getManifestContent());
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessDeployRequest.getServerlessInfraConfig();
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

  public Optional<String> getPreviousVersionTimeStamp(
      List<String> timeStamps, LogCallback executionLogCallback, ServerlessDeployRequest serverlessDeployRequest) {
    if (!CollectionUtils.isEmpty(timeStamps)) {
      String currentCloudFormationTemplate =
          getCurrentCloudFormationTemplate(executionLogCallback, serverlessDeployRequest);

      int timeStampsCount = timeStamps.size();
      for (int index = timeStampsCount - 1; index >= 0; index--) {
        String timeStamp = timeStamps.get(index);
        // Below code uses Boyer-Moore-Algorithm which is faster
        Pattern pattern = Pattern.compile(timeStamp);
        Matcher matcher = pattern.matcher(currentCloudFormationTemplate);
        if (matcher.find()) {
          return Optional.of(timeStamp);
        }
      }
    }

    return Optional.empty();
  }
}
