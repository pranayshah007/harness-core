/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.common.TemplateConstants.PATH_DELIMITER;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.FileBucket;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FileCreationException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.AwsLambdaContextElement.FunctionMeta;
import software.wings.beans.AwsConfig;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionResponse.AwsLambdaExecuteFunctionResponseBuilder;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfResponse;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfResponse.AwsLambdaExecuteWfResponseBuilder;
import software.wings.service.impl.aws.model.AwsLambdaFunctionParams;
import software.wings.service.impl.aws.model.AwsLambdaFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResponse.AwsLambdaFunctionResponseBuilder;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResult;
import software.wings.service.impl.aws.model.AwsLambdaVpcConfig;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsResponse;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.CreateAliasRequest;
import com.amazonaws.services.lambda.model.CreateAliasResult;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.GetFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.ListFunctionsRequest;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.LogType;
import com.amazonaws.services.lambda.model.PublishVersionRequest;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.TagResourceRequest;
import com.amazonaws.services.lambda.model.UntagResourceRequest;
import com.amazonaws.services.lambda.model.UpdateAliasRequest;
import com.amazonaws.services.lambda.model.UpdateAliasResult;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.Strings;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsLambdaHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsLambdaHelperServiceDelegate {
  String REPOSITORY_DIR_PATH = "./repository";
  String LAMBDA_ARTIFACT_DOWNLOAD_DIR_PATH = "./repository/lambdaartifacts";
  String AWS_LAMBDA_LOG_PREFIX = "AWS_LAMBDA_LOG_PREFIX ";

  String ACTIVE_FUNCTION_STATE = "Active";
  String PENDING_FUNCTION_STATE = "Pending";
  String FAILED_FUNCTION_STATE = "Failed";

  String ACTIVE_LAST_UPDATE_STATUS = "Successful";
  String PENDING_LAST_UPDATE_STATUS = "InProgress";
  String FAILED_LAST_UPDATE_STATUS = "Failed";

  long TIMEOUT_IN_SECONDS = 60 * 60L;
  long WAIT_SLEEP_IN_SECONDS = 10L;

  @Inject private DelegateFileManager delegateFileManager;
  @Inject private TimeLimiter timeLimiter;

  @VisibleForTesting
  public AWSLambdaClient getAmazonLambdaClient(String region, AwsConfig awsConfig) {
    AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AWSLambdaClient) builder.build();
  }

  @Override
  public AwsLambdaExecuteFunctionResponse executeFunction(AwsLambdaExecuteFunctionRequest request) {
    AwsConfig awsConfig = request.getAwsConfig();
    List<EncryptedDataDetail> encryptionDetails = request.getEncryptionDetails();
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AWSLambdaClient> closeableAWSLambdaClient =
             new CloseableAmazonWebServiceClient(getAmazonLambdaClient(request.getRegion(), request.getAwsConfig()))) {
      InvokeRequest invokeRequest = new InvokeRequest()
                                        .withFunctionName(request.getFunctionName())
                                        .withQualifier(request.getQualifier())
                                        .withLogType(LogType.Tail);
      if (isNotEmpty(request.getPayload())) {
        invokeRequest.setPayload(request.getPayload());
      }
      tracker.trackLambdaCall("Invoke Function");
      InvokeResult invokeResult = closeableAWSLambdaClient.getClient().invoke(invokeRequest);
      log.info("Lambda invocation result: " + invokeResult.toString());
      AwsLambdaExecuteFunctionResponseBuilder responseBuilder = AwsLambdaExecuteFunctionResponse.builder();
      responseBuilder.statusCode(invokeResult.getStatusCode());
      responseBuilder.functionError(invokeResult.getFunctionError());
      String logResult = invokeResult.getLogResult();
      if (logResult != null) {
        try {
          logResult = new String(Base64.decodeBase64(logResult), "UTF-8");
          responseBuilder.logResult(logResult);
        } catch (UnsupportedEncodingException ex) {
          throw new WingsException(ex);
        }
      }
      responseBuilder.payload(StandardCharsets.UTF_8.decode(invokeResult.getPayload()).toString());
      responseBuilder.awsLambdaExecutionData(request.getAwsLambdaExecutionData())
          .lambdaTestEvent(request.getLambdaTestEvent())
          .executionStatus(SUCCESS);
      return responseBuilder.build();
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  @Override
  public AwsLambdaFunctionResponse getLambdaFunctions(AwsLambdaFunctionRequest request) {
    AwsConfig awsConfig = request.getAwsConfig();
    List<EncryptedDataDetail> encryptionDetails = request.getEncryptionDetails();
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AWSLambdaClient> closeableAWSLambdaClient =
             new CloseableAmazonWebServiceClient(getAmazonLambdaClient(request.getRegion(), request.getAwsConfig()))) {
      AwsLambdaFunctionResponseBuilder response = AwsLambdaFunctionResponse.builder();
      List<String> lambdaFunctions = new ArrayList<>();
      List<FunctionConfiguration> functionConfigurations = new ArrayList<>();

      ListFunctionsResult listFunctionsResult = null;
      String nextMarker = null;
      do {
        tracker.trackLambdaCall("List Functions");
        listFunctionsResult = closeableAWSLambdaClient.getClient().listFunctions(
            new ListFunctionsRequest().withMaxItems(100).withMarker(nextMarker));
        functionConfigurations.addAll(listFunctionsResult.getFunctions());
        nextMarker = listFunctionsResult.getNextMarker();
      } while (nextMarker != null);

      functionConfigurations.forEach(
          functionConfiguration -> lambdaFunctions.add(functionConfiguration.getFunctionName()));
      return response.lambdaFunctions(lambdaFunctions).executionStatus(SUCCESS).build();
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  @Override
  public AwsLambdaExecuteWfResponse executeWf(AwsLambdaExecuteWfRequest request, ExecutionLogCallback logCallback) {
    AwsLambdaExecuteWfResponseBuilder responseBuilder = AwsLambdaExecuteWfResponse.builder();

    AwsConfig awsConfig = request.getAwsConfig();
    responseBuilder.awsConfig(awsConfig);
    responseBuilder.region(request.getRegion());
    List<EncryptedDataDetail> encryptionDetails = request.getEncryptionDetails();
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);

    try (CloseableAmazonWebServiceClient<AWSLambdaClient> closeableAWSLambdaClient =
             new CloseableAmazonWebServiceClient(getAmazonLambdaClient(request.getRegion(), request.getAwsConfig()))) {
      String roleArn = request.getRoleArn();
      List<String> evaluatedAliases = request.getEvaluatedAliases();
      Map<String, String> serviceVariables = request.getServiceVariables();
      AwsLambdaVpcConfig lambdaVpcConfig = request.getLambdaVpcConfig();
      List<AwsLambdaFunctionParams> functionParamsList = request.getFunctionParams();
      List<AwsLambdaFunctionResult> functionResultList = new ArrayList<>();
      File workingDirectory = generateWorkingDirectoryOnDelegate();

      ExecutionStatus status = SUCCESS;
      for (AwsLambdaFunctionParams functionParams : functionParamsList) {
        try {
          functionResultList.add(
              executeFunctionDeployment(closeableAWSLambdaClient.getClient(), roleArn, evaluatedAliases,
                  serviceVariables, lambdaVpcConfig, functionParams, request, workingDirectory, logCallback));
        } catch (Exception ex) {
          logCallback.saveExecutionLog(
              "Exception: " + ex.getMessage() + " while deploying function: " + functionParams.getFunctionName(),
              ERROR);
          status = FAILED;
          functionResultList.add(
              AwsLambdaFunctionResult.builder()
                  .success(false)
                  .errorMessage(ex.getMessage())
                  .functionMeta(FunctionMeta.builder().functionName(functionParams.getFunctionName()).build())
                  .build());
        }
      }
      responseBuilder.executionStatus(status);
      responseBuilder.functionResults(functionResultList);

      String message = "Successfully completed Aws Lambda Deploy step";
      CommandExecutionStatus finalStatus = CommandExecutionStatus.SUCCESS;
      LogLevel level = INFO;
      if (FAILED == status) {
        message = "Failed while deploying Lambda functions";
        finalStatus = CommandExecutionStatus.FAILURE;
        level = ERROR;
      }
      logCallback.saveExecutionLog(message, level, finalStatus);

    } catch (AmazonEC2Exception amazonEC2Exception) {
      handleAmazonServiceException(amazonEC2Exception);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (IOException ioException) {
      log.error(AWS_LAMBDA_LOG_PREFIX + "Exception in processing Lambda Setup task [{}]", request, ioException);
      logCallback.saveExecutionLog("\n\n ----------  AWS LAMBDA Setup process failed to complete successfully", ERROR,
          CommandExecutionStatus.FAILURE);
      return AwsLambdaExecuteWfResponse.builder()
          .executionStatus(FAILED)
          .errorMessage(ExceptionUtils.getMessage(ioException))
          .build();
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return responseBuilder.build();
  }

  private File generateWorkingDirectoryOnDelegate() throws IOException {
    // This path represents location where artifact will be downloaded
    return generateWorkingDirectoryForDeployment();
  }

  public File generateWorkingDirectoryForDeployment() throws IOException {
    String workingDirecotry = UUIDGenerator.generateUuid();
    FileIo.createDirectoryIfDoesNotExist(REPOSITORY_DIR_PATH);
    FileIo.createDirectoryIfDoesNotExist(LAMBDA_ARTIFACT_DOWNLOAD_DIR_PATH);
    String workingDir = LAMBDA_ARTIFACT_DOWNLOAD_DIR_PATH + "/" + workingDirecotry;
    FileIo.createDirectoryIfDoesNotExist(workingDir);
    return new File(workingDir);
  }

  private VpcConfig getVpcConfig(AwsLambdaVpcConfig awsLambdaVpcConfig) {
    String vpcId = awsLambdaVpcConfig.getVpcId();
    VpcConfig vpcConfig = new VpcConfig();
    if (vpcId != null) {
      List<String> subnetIds = awsLambdaVpcConfig.getSubnetIds();
      List<String> securityGroupIds = awsLambdaVpcConfig.getSecurityGroupIds();
      if (!securityGroupIds.isEmpty() && !subnetIds.isEmpty()) {
        vpcConfig.setSubnetIds(subnetIds);
        vpcConfig.setSecurityGroupIds(securityGroupIds);
      } else {
        throw new InvalidRequestException("At least one security group and one subnet must be provided");
      }
    }
    return vpcConfig;
  }

  private void createFunctionAlias(AWSLambdaClient lambdaClient, String functionName, String functionVersion,
      List<String> evaluatedAliases, ExecutionLogCallback logCallback) {
    if (isNotEmpty(evaluatedAliases)) {
      evaluatedAliases.forEach(alias -> {
        logCallback.saveExecutionLog(format("Creating Function Alias: [%s]", alias));
        tracker.trackLambdaCall("Create Function Alias");
        CreateAliasResult createAliasResult = lambdaClient.createAlias(new CreateAliasRequest()
                                                                           .withFunctionName(functionName)
                                                                           .withFunctionVersion(functionVersion)
                                                                           .withName(alias));
        logCallback.saveExecutionLog(format("Created Function Alias with name:[%s], arn:[%s]",
            createAliasResult.getName(), createAliasResult.getAliasArn()));
      });
    }
  }

  private void updateFunctionAlias(AWSLambdaClient lambdaClient, String functionName, String functionArn,
      List<String> updateAlias, ExecutionLogCallback logCallback) {
    updateAlias.forEach(alias -> {
      logCallback.saveExecutionLog(format("Updating Function Alias: [%s]", alias));
      tracker.trackLambdaCall("Update Function Alias");
      UpdateAliasResult updateAliasResult = lambdaClient.updateAlias(
          new UpdateAliasRequest().withFunctionName(functionName).withFunctionVersion(functionArn).withName(alias));
      logCallback.saveExecutionLog(format("Updated Function Alias with name:[%s], arn:[%s]",
          updateAliasResult.getName(), updateAliasResult.getAliasArn()));
    });
  }

  /**
   * Intially Aws Lambda did not allow for _ to be in the function name. So on the
   * Harness side we were normalizing it to -. But now _ is allowed. So now we need
   * special handling in case we may be updating existing name where _ may have
   * been normalized to -.
   */
  @VisibleForTesting
  String getAlternateNormalizedFunctionName(String functionName) {
    return functionName.replace('_', '-');
  }

  private AwsLambdaFunctionResult executeFunctionDeployment(AWSLambdaClient lambdaClient, String roleArn,
      List<String> evaluatedAliases, Map<String, String> serviceVariables, AwsLambdaVpcConfig lambdaVpcConfig,
      AwsLambdaFunctionParams functionParams, AwsLambdaExecuteWfRequest awsLambdaExecuteWfRequest,
      File workingDirectory, ExecutionLogCallback logCallback) throws IOException, ExecutionException {
    if (awsLambdaExecuteWfRequest.getArtifactStreamAttributes().getArtifactStreamType().equalsIgnoreCase(
            ArtifactStreamType.AMAZON_S3.name())
        || ArtifactStreamType.CUSTOM.name().equalsIgnoreCase(
            awsLambdaExecuteWfRequest.getArtifactStreamAttributes().getArtifactStreamType())) {
      return executeFunctionDeploymentFromS3OrCustomRepository(
          lambdaClient, roleArn, evaluatedAliases, serviceVariables, lambdaVpcConfig, functionParams, logCallback);
    } else {
      return executeFunctionDeploymentAfterDownloadingArtifact(lambdaClient, roleArn, evaluatedAliases,
          serviceVariables, lambdaVpcConfig, functionParams, awsLambdaExecuteWfRequest, workingDirectory, logCallback);
    }
  }

  private GetFunctionResult getFunctionWithName(
      AWSLambdaClient lambdaClient, String functionName, ExecutionLogCallback executionLogCallback) {
    GetFunctionResult functionResult = null;
    try {
      executionLogCallback.saveExecutionLog(format("Testing existence of function with name: [%s]", functionName));
      tracker.trackLambdaCall("Get Function");
      functionResult = lambdaClient.getFunction(new GetFunctionRequest().withFunctionName(functionName));
    } catch (ResourceNotFoundException exception) {
      // Function does not exist
      executionLogCallback.saveExecutionLog(format("Function: [%s] not found.", functionName));
    }
    return functionResult;
  }

  private FunctionMeta createLambdaFunction(AWSLambdaClient lambdaClient, Map<String, String> serviceVariables,
      AwsLambdaFunctionParams functionParams, String functionName, FunctionCode functionCode, VpcConfig vpcConfig,
      String roleArn, List<String> evaluatedAliases, ExecutionLogCallback executionLogCallback) {
    FunctionMeta functionMeta;
    CreateFunctionRequest createFunctionRequest =
        new CreateFunctionRequest()
            .withEnvironment(new Environment().withVariables(serviceVariables))
            .withRuntime(functionParams.getRuntime())
            .withFunctionName(functionName)
            .withHandler(functionParams.getHandler())
            .withRole(roleArn)
            .withCode(functionCode)
            .withPublish(true)
            .withTags(functionParams.getFunctionTags())
            .withTimeout(functionParams.getTimeout())
            .withMemorySize(functionParams.getMemory())
            .withVpcConfig(vpcConfig);
    tracker.trackLambdaCall("Create Function");
    CreateFunctionResult createFunctionResult = lambdaClient.createFunction(createFunctionRequest);
    waitForFunctionToCreate(lambdaClient, functionName, executionLogCallback);
    executionLogCallback.saveExecutionLog(format("Function [%s] published with version [%s] successfully", functionName,
                                              createFunctionResult.getVersion()),
        INFO);
    executionLogCallback.saveExecutionLog(
        format("Created Function Code Sha256: [%s]", createFunctionResult.getCodeSha256()), INFO);
    executionLogCallback.saveExecutionLog(
        format("Created Function ARN: [%s]", createFunctionResult.getFunctionArn()), INFO);
    createFunctionAlias(
        lambdaClient, functionName, createFunctionResult.getVersion(), evaluatedAliases, executionLogCallback);
    functionMeta = FunctionMeta.builder()
                       .functionArn(createFunctionResult.getFunctionArn())
                       .functionName(createFunctionResult.getFunctionName())
                       .version(createFunctionResult.getVersion())
                       .build();
    return functionMeta;
  }

  private FunctionMeta updateLambdaFunction(AWSLambdaClient lambdaClient, String functionName,
      UpdateFunctionCodeRequest updateFunctionCodeRequest, String roleArn, List<String> evaluatedAliases,
      AwsLambdaFunctionParams functionParams, VpcConfig vpcConfig, Map<String, String> serviceVariables,
      GetFunctionResult functionResult, ExecutionLogCallback executionLogCallback) {
    FunctionMeta functionMeta;
    executionLogCallback.saveExecutionLog(
        format("Existing Lambda Function Code Sha256: [%s].", functionResult.getConfiguration().getCodeSha256()));
    tracker.trackLambdaCall("Update Function Code");

    UpdateFunctionCodeResult updateFunctionCodeResultDryRun =
        lambdaClient.updateFunctionCode(updateFunctionCodeRequest.withDryRun(true));
    executionLogCallback.saveExecutionLog(
        format("New Lambda function code Sha256: [%s]", updateFunctionCodeResultDryRun.getCodeSha256()));
    if (updateFunctionCodeResultDryRun.getCodeSha256().equals(functionResult.getConfiguration().getCodeSha256())) {
      executionLogCallback.saveExecutionLog("Function code didn't change. Skip function code update", INFO);
    } else {
      tracker.trackLambdaCall("Update Function code");
      UpdateFunctionCodeResult updateFunctionCodeResult =
          lambdaClient.updateFunctionCode(updateFunctionCodeRequest.withDryRun(false));
      executionLogCallback.saveExecutionLog("Function code updated successfully", INFO);
      executionLogCallback.saveExecutionLog(
          format("Updated Function Code Sha256: [%s]", updateFunctionCodeResult.getCodeSha256()));
      executionLogCallback.saveExecutionLog(
          format("Updated Function ARN: [%s]", updateFunctionCodeResult.getFunctionArn()));
      waitForFunctionToUpdate(lambdaClient, functionName, executionLogCallback);
    }

    UpdateFunctionConfigurationResult updateFunctionConfigurationResult = updateFunctionConfiguration(
        lambdaClient, functionName, roleArn, functionParams, vpcConfig, serviceVariables, executionLogCallback);
    executionLogCallback.saveExecutionLog("Publishing new version", INFO);
    PublishVersionRequest publishVersionRequest =
        new PublishVersionRequest()
            .withFunctionName(updateFunctionConfigurationResult.getFunctionName())
            .withCodeSha256(updateFunctionConfigurationResult.getCodeSha256());
    tracker.trackLambdaCall("Publish Function Version");
    PublishVersionResult publishVersionResult = lambdaClient.publishVersion(publishVersionRequest);
    executionLogCallback.saveExecutionLog(format("Published new version: [%s]", publishVersionResult.getVersion()));
    executionLogCallback.saveExecutionLog(
        format("Published function ARN: [%s]", publishVersionResult.getFunctionArn()));
    ListAliasesResult listAliasesResult =
        lambdaClient.listAliases(new ListAliasesRequest().withFunctionName(functionName));

    List<String> newAliases = new ArrayList<>();
    if (isNotEmpty(evaluatedAliases)) {
      newAliases.addAll(evaluatedAliases.stream()
                            .filter(alias
                                -> listAliasesResult.getAliases().stream().noneMatch(
                                    aliasConfiguration -> aliasConfiguration.getName().equals(alias)))
                            .collect(toList()));
    }
    if (isNotEmpty(newAliases)) {
      createFunctionAlias(
          lambdaClient, functionName, publishVersionResult.getVersion(), newAliases, executionLogCallback);
    }

    List<String> updateAlias = new ArrayList<>();
    if (isNotEmpty(evaluatedAliases)) {
      updateAlias.addAll(evaluatedAliases.stream()
                             .filter(alias -> newAliases != null && newAliases.stream().noneMatch(s -> s.equals(alias)))
                             .collect(toList()));
    }
    if (isNotEmpty(updateAlias)) {
      updateFunctionAlias(
          lambdaClient, functionName, publishVersionResult.getVersion(), updateAlias, executionLogCallback);
    }
    tagExistingFunction(functionResult, functionParams.getFunctionTags(), executionLogCallback, lambdaClient);

    functionMeta = FunctionMeta.builder()
                       .functionArn(publishVersionResult.getFunctionArn())
                       .functionName(publishVersionResult.getFunctionName())
                       .version(publishVersionResult.getVersion())
                       .build();

    return functionMeta;
  }

  private UpdateFunctionConfigurationResult updateFunctionConfiguration(AWSLambdaClient lambdaClient,
      String functionName, String roleArn, AwsLambdaFunctionParams functionParams, VpcConfig vpcConfig,
      Map<String, String> serviceVariables, LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Updating function configuration", INFO);
    UpdateFunctionConfigurationRequest updateFunctionConfigurationRequest =
        new UpdateFunctionConfigurationRequest()
            .withEnvironment(new Environment().withVariables(serviceVariables))
            .withRuntime(functionParams.getRuntime())
            .withFunctionName(functionName)
            .withHandler(functionParams.getHandler())
            .withRole(roleArn)
            .withTimeout(functionParams.getTimeout())
            .withMemorySize(functionParams.getMemory())
            .withVpcConfig(vpcConfig);
    tracker.trackLambdaCall("Update Function Configuration");
    UpdateFunctionConfigurationResult updateFunctionConfigurationResult =
        lambdaClient.updateFunctionConfiguration(updateFunctionConfigurationRequest);

    waitForFunctionToUpdate(lambdaClient, functionName, executionLogCallback);
    executionLogCallback.saveExecutionLog("Function configuration updated successfully", INFO);
    return updateFunctionConfigurationResult;
  }

  /**
   * successOnResponse -> "State" = "Active"
   * errorOnResponse -> "State" = "Failed"
   * retryOnResponse -> "State" = "Pending"
   */
  public void waitForFunctionToCreate(
      AWSLambdaClient lambdaClient, String functionName, LogCallback executionLogCallback) {
    try {
      executionLogCallback.saveExecutionLog("Verifying if state of function is " + ACTIVE_FUNCTION_STATE);
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(TIMEOUT_IN_SECONDS), () -> {
        while (true) {
          GetFunctionConfigurationResult result =
              getFunctionConfiguration(lambdaClient, functionName, executionLogCallback);
          String state = result.getState();
          if (ACTIVE_FUNCTION_STATE.equalsIgnoreCase(state)) {
            break;
          } else if (FAILED_FUNCTION_STATE.equalsIgnoreCase(state)) {
            throw new InvalidRequestException(
                "Function failed to reach " + ACTIVE_FUNCTION_STATE + " state", WingsException.SRE);
          } else {
            executionLogCallback.saveExecutionLog(
                format("function: [%s], state: [%s], reason: [%s]", functionName, state, result.getStateReason()));
          }
          sleep(ofSeconds(WAIT_SLEEP_IN_SECONDS));
        }
        return true;
      });
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timed out waiting for function to reach " + ACTIVE_FUNCTION_STATE + " state",
          "Timeout", ExceptionMessageSanitizer.sanitizeException(e), WingsException.SRE);
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException("Error while waiting for function to reach " + ACTIVE_FUNCTION_STATE + " state",
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  /**
   * successOnResponse -> "LastUpdateStatus" = "Successful"
   * errorOnResponse -> "LastUpdateStatus" = "Failed"
   * retryOnResponse -> "LastUpdateStatus" = "InProgress"
   */
  public void waitForFunctionToUpdate(
      AWSLambdaClient lambdaClient, String functionName, LogCallback executionLogCallback) {
    try {
      executionLogCallback.saveExecutionLog("Verifying if status of function to be " + ACTIVE_LAST_UPDATE_STATUS);
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(TIMEOUT_IN_SECONDS), () -> {
        while (true) {
          GetFunctionConfigurationResult result =
              getFunctionConfiguration(lambdaClient, functionName, executionLogCallback);
          String status = result.getLastUpdateStatus();
          if (ACTIVE_LAST_UPDATE_STATUS.equalsIgnoreCase(status)) {
            break;
          } else if (FAILED_LAST_UPDATE_STATUS.equalsIgnoreCase(status)) {
            throw new InvalidRequestException(
                "Function failed to reach " + ACTIVE_LAST_UPDATE_STATUS + " status", WingsException.SRE);
          } else {
            executionLogCallback.saveExecutionLog(format("function: [%s], status: [%s], reason: [%s]", functionName,
                status, result.getLastUpdateStatusReason()));
          }
          sleep(ofSeconds(WAIT_SLEEP_IN_SECONDS));
        }
        return true;
      });
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timed out waiting for function to reach " + ACTIVE_LAST_UPDATE_STATUS + " status",
          "Timeout", ExceptionMessageSanitizer.sanitizeException(e), WingsException.SRE);
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Error while waiting for function to reach " + ACTIVE_LAST_UPDATE_STATUS + " status", e);
    }
  }

  private GetFunctionConfigurationResult getFunctionConfiguration(
      AWSLambdaClient lambdaClient, String functionName, LogCallback executionLogCallback) {
    try {
      tracker.trackLambdaCall("Get Function configuration");
      return lambdaClient.getFunctionConfiguration(
          new GetFunctionConfigurationRequest().withFunctionName(functionName));
    } catch (ResourceNotFoundException exception) {
      // Function does not exist
      executionLogCallback.saveExecutionLog(format("Function: [%s] not found.", functionName));
    }
    throw new InvalidRequestException(format("Function: [%s] not found.", functionName));
  }

  private AwsLambdaFunctionResult executeFunctionDeploymentAfterDownloadingArtifact(AWSLambdaClient lambdaClient,
      String roleArn, List<String> evaluatedAliases, Map<String, String> serviceVariables,
      AwsLambdaVpcConfig lambdaVpcConfig, AwsLambdaFunctionParams functionParams,
      AwsLambdaExecuteWfRequest awsLambdaExecuteWfRequest, File workingDirectory, ExecutionLogCallback logCallback)
      throws IOException, ExecutionException {
    File artifactFile = fetchArtifactFileForDeployment(awsLambdaExecuteWfRequest, workingDirectory, logCallback);
    ByteBuffer fileByteBuffer = ByteBuffer.wrap(IOUtils.toByteArray(new FileInputStream(artifactFile)));
    printLambdaDetails(functionParams, roleArn, lambdaVpcConfig, evaluatedAliases, logCallback);
    String functionName = functionParams.getFunctionName();

    FunctionMeta functionMeta;
    VpcConfig vpcConfig = getVpcConfig(lambdaVpcConfig);
    GetFunctionResult functionResult = getFunctionWithName(lambdaClient, functionName, logCallback);

    if (functionResult == null) {
      String alternateNormalizedFunctionName = getAlternateNormalizedFunctionName(functionName);
      if (!alternateNormalizedFunctionName.equals(functionName)) {
        logCallback.saveExecutionLog(
            format("Testing alternate function name: [%s] for existence", alternateNormalizedFunctionName));
        functionResult = getFunctionWithName(lambdaClient, alternateNormalizedFunctionName, logCallback);
        if (functionResult != null) {
          logCallback.saveExecutionLog(
              format("Found existing function with name: [%s]. Using this.", alternateNormalizedFunctionName));
          functionName = alternateNormalizedFunctionName;
        }
      }
    }

    if (functionResult == null) {
      logCallback.saveExecutionLog("Function: " + functionName + " does not exist.", INFO);
      FunctionCode functionCode = new FunctionCode().withZipFile(fileByteBuffer);
      functionMeta = createLambdaFunction(lambdaClient, serviceVariables, functionParams, functionName, functionCode,
          vpcConfig, roleArn, evaluatedAliases, logCallback);
    } else {
      logCallback.saveExecutionLog(format("Function: [%s] exists. Update and Publish", functionName));
      UpdateFunctionCodeRequest updateFunctionCodeRequest =
          new UpdateFunctionCodeRequest().withFunctionName(functionName).withZipFile(fileByteBuffer);
      functionMeta = updateLambdaFunction(lambdaClient, functionName, updateFunctionCodeRequest, roleArn,
          evaluatedAliases, functionParams, vpcConfig, serviceVariables, functionResult, logCallback);
    }
    logCallback.saveExecutionLog(format("Successfully deployed lambda function: [%s]", functionName));
    logCallback.saveExecutionLog("=================");
    return AwsLambdaFunctionResult.builder().success(true).functionMeta(functionMeta).build();
  }

  private void printLambdaDetails(AwsLambdaFunctionParams functionParams, String roleArn,
      AwsLambdaVpcConfig lambdaVpcConfig, List<String> evaluatedAliases, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Deploying Lambda with following configuration", INFO);

    if (isNotEmpty(functionParams.getBucket())) {
      executionLogCallback.saveExecutionLog("S3 Bucket: " + functionParams.getBucket(), INFO);
    }
    if (isNotEmpty(functionParams.getKey())) {
      executionLogCallback.saveExecutionLog("Bucket Key: " + functionParams.getKey(), INFO);
    }

    executionLogCallback.saveExecutionLog("Function Name: " + functionParams.getFunctionName(), INFO);
    executionLogCallback.saveExecutionLog("Function handler: " + functionParams.getHandler(), INFO);
    executionLogCallback.saveExecutionLog("Function runtime: " + functionParams.getRuntime(), INFO);
    executionLogCallback.saveExecutionLog("Function memory: " + functionParams.getMemory(), INFO);
    executionLogCallback.saveExecutionLog("Function execution timeout: " + functionParams.getTimeout(), INFO);
    executionLogCallback.saveExecutionLog("IAM role ARN: " + roleArn, INFO);
    executionLogCallback.saveExecutionLog("VPC: " + lambdaVpcConfig.getVpcId(), INFO);

    if (isNotEmpty(lambdaVpcConfig.getSubnetIds())) {
      executionLogCallback.saveExecutionLog("Subnet: " + Joiner.on(",").join(lambdaVpcConfig.getSubnetIds(), INFO));
    }

    if (isNotEmpty(lambdaVpcConfig.getSecurityGroupIds())) {
      executionLogCallback.saveExecutionLog(
          "Security Groups: " + Joiner.on(",").join(lambdaVpcConfig.getSecurityGroupIds()), INFO);
    }

    if (isNotEmpty(evaluatedAliases)) {
      executionLogCallback.saveExecutionLog("Function Aliases: " + Joiner.on(",").join(evaluatedAliases), INFO);
    }
  }

  private AwsLambdaFunctionResult executeFunctionDeploymentFromS3OrCustomRepository(AWSLambdaClient lambdaClient,
      String roleArn, List<String> evaluatedAliases, Map<String, String> serviceVariables,
      AwsLambdaVpcConfig lambdaVpcConfig, AwsLambdaFunctionParams functionParams, ExecutionLogCallback logCallback) {
    printLambdaDetails(functionParams, roleArn, lambdaVpcConfig, evaluatedAliases, logCallback);

    String functionName = functionParams.getFunctionName();
    FunctionMeta functionMeta;
    VpcConfig vpcConfig = getVpcConfig(lambdaVpcConfig);
    GetFunctionResult functionResult = getFunctionWithName(lambdaClient, functionName, logCallback);

    if (functionResult == null) {
      String alternateNormalizedFunctionName = getAlternateNormalizedFunctionName(functionName);
      if (!alternateNormalizedFunctionName.equals(functionName)) {
        logCallback.saveExecutionLog(
            format("Testing alternate function name: [%s] for existence", alternateNormalizedFunctionName));
        functionResult = getFunctionWithName(lambdaClient, functionName, logCallback);
        if (functionResult != null) {
          logCallback.saveExecutionLog(
              format("Found existing function with name: [%s]. Using this.", alternateNormalizedFunctionName));
          functionName = alternateNormalizedFunctionName;
        }
      }
    }

    if (functionResult == null) {
      logCallback.saveExecutionLog("Function: " + functionName + " does not exist.", INFO);
      FunctionCode functionCode =
          new FunctionCode().withS3Bucket(functionParams.getBucket()).withS3Key(functionParams.getKey());
      functionMeta = createLambdaFunction(lambdaClient, serviceVariables, functionParams, functionName, functionCode,
          vpcConfig, roleArn, evaluatedAliases, logCallback);
    } else {
      UpdateFunctionCodeRequest updateFunctionCodeRequest = new UpdateFunctionCodeRequest()
                                                                .withFunctionName(functionName)
                                                                .withS3Bucket(functionParams.getBucket())
                                                                .withS3Key(functionParams.getKey());

      functionMeta = updateLambdaFunction(lambdaClient, functionName, updateFunctionCodeRequest, roleArn,
          evaluatedAliases, functionParams, vpcConfig, serviceVariables, functionResult, logCallback);
    }

    logCallback.saveExecutionLog(format("Successfully deployed lambda function: [%s]", functionName));
    logCallback.saveExecutionLog("=================");
    return AwsLambdaFunctionResult.builder().success(true).functionMeta(functionMeta).build();
  }

  public File downloadArtifact(List<ArtifactFile> artifactFiles, String accountId, File workingDirecotry)
      throws IOException, ExecutionException {
    List<Pair<String, String>> fileIds = Lists.newArrayList();

    if (isEmpty(artifactFiles)) {
      throw new InvalidArgumentsException(Pair.of("Artifact", "is not available"));
    }

    artifactFiles.forEach(artifactFile -> fileIds.add(Pair.of(artifactFile.getFileUuid(), null)));
    try (InputStream inputStream =
             delegateFileManager.downloadArtifactByFileId(FileBucket.ARTIFACTS, fileIds.get(0).getKey(), accountId)) {
      String fileName = System.currentTimeMillis() + artifactFiles.get(0).getName();
      File artifactFile = new File(workingDirecotry.getAbsolutePath() + "/" + fileName);

      if (!artifactFile.createNewFile()) {
        throw new WingsException(ErrorCode.GENERAL_ERROR)
            .addParam("message", "Failed to create file " + artifactFile.getCanonicalPath());
      }
      IOUtils.copy(inputStream, new FileOutputStream(artifactFile));
      return artifactFile;
    }
  }

  private File fetchArtifactFileForDeployment(AwsLambdaExecuteWfRequest awsLambdaExecuteWfRequest,
      File workingDirectory, ExecutionLogCallback executionLogCallback) throws IOException, ExecutionException {
    File artifactFile;
    if (awsLambdaExecuteWfRequest.getArtifactStreamAttributes().isMetadataOnly()) {
      executionLogCallback.saveExecutionLog(
          color("--------- artifact will be downloaded for only-meta feature", White));
      artifactFile = downloadArtifact(awsLambdaExecuteWfRequest, workingDirectory);
    } else {
      // Download artifact on delegate from manager
      artifactFile = downloadArtifact(
          awsLambdaExecuteWfRequest.getArtifactFiles(), awsLambdaExecuteWfRequest.getAccountId(), workingDirectory);
    }

    return artifactFile;
  }

  public File downloadArtifact(AwsLambdaExecuteWfRequest awsLambdaExecuteWfRequest, File workingDirectory)
      throws IOException, ExecutionException {
    InputStream artifactFileStream =
        delegateFileManager.downloadArtifactAtRuntime(awsLambdaExecuteWfRequest.getArtifactStreamAttributes(),
            awsLambdaExecuteWfRequest.getAccountId(), awsLambdaExecuteWfRequest.getAppId(),
            awsLambdaExecuteWfRequest.getActivityId(), awsLambdaExecuteWfRequest.getCommandName(),
            awsLambdaExecuteWfRequest.getArtifactStreamAttributes().getRegistryHostName());
    String fileName =
        System.currentTimeMillis() + awsLambdaExecuteWfRequest.getArtifactStreamAttributes().getArtifactName();

    File artifactFile = new File(workingDirectory.getAbsolutePath() + PATH_DELIMITER + FilenameUtils.getName(fileName));

    if (!artifactFile.createNewFile()) {
      throw new FileCreationException("Failed to create file " + artifactFile.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }
    IOUtils.copy(artifactFileStream, new FileOutputStream(artifactFile));

    return artifactFile;
  }

  @VisibleForTesting
  void tagExistingFunction(GetFunctionResult functionResult, Map<String, String> functionTags,
      ExecutionLogCallback logCallback, AWSLambdaClient lambdaClient) {
    String functionArn = functionResult.getConfiguration().getFunctionArn();
    Map<String, String> existingTags = functionResult.getTags();
    if (isNotEmpty(existingTags)) {
      List<String> keysToRemove =
          existingTags.entrySet().stream().map(Entry::getKey).filter(key -> !key.startsWith("aws:")).collect(toList());
      if (isNotEmpty(keysToRemove)) {
        logCallback.saveExecutionLog(format("Untagging existing tags from the function: [%s]", functionArn));
        tracker.trackLambdaCall("Untag Function");
        lambdaClient.untagResource(new UntagResourceRequest().withResource(functionArn).withTagKeys(keysToRemove));
      }
    }
    if (isEmpty(functionTags)) {
      logCallback.saveExecutionLog("No new tags to be put.");
      return;
    }
    logCallback.saveExecutionLog(format("Executing tagging for function: [%s]", functionArn));
    tracker.trackLambdaCall("Tag Function");
    lambdaClient.tagResource(new TagResourceRequest().withResource(functionArn).withTags(functionTags));
  }

  @Override
  public AwsLambdaDetailsResponse getFunctionDetails(AwsLambdaDetailsRequest request, boolean isInstanceSync) {
    GetFunctionResult getFunctionResult = null;
    final AwsConfig awsConfig = request.getAwsConfig();
    final List<EncryptedDataDetail> encryptionDetails = request.getEncryptionDetails();
    encryptionService.decrypt(awsConfig, encryptionDetails, isInstanceSync);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);

    try (CloseableAmazonWebServiceClient<AWSLambdaClient> closeableAWSLambdaClient =
             new CloseableAmazonWebServiceClient(getAmazonLambdaClient(request.getRegion(), request.getAwsConfig()))) {
      try {
        tracker.trackLambdaCall("Get Function");
        getFunctionResult = closeableAWSLambdaClient.getClient().getFunction(
            new GetFunctionRequest().withFunctionName(request.getFunctionName()).withQualifier(request.getQualifier()));
      } catch (ResourceNotFoundException rnfe) {
        log.info("No function found with name =[{}], qualifier =[{}]. Error Msg is [{}]", request.getFunctionName(),
            request.getQualifier(), rnfe.getMessage());
        return AwsLambdaDetailsResponse.builder().executionStatus(SUCCESS).details(null).build();
      }
      ListAliasesResult listAliasesResult = null;
      if (Boolean.TRUE.equals(request.getLoadAliases())) {
        final ListAliasesRequest listAliasRequest =
            new ListAliasesRequest().withFunctionName(request.getFunctionName());
        if (Strings.isNotEmpty(getFunctionResult.getConfiguration().getVersion())) {
          listAliasRequest.withFunctionVersion(getFunctionResult.getConfiguration().getVersion());
        }
        tracker.trackLambdaCall("List Function Aliases");
        listAliasesResult = closeableAWSLambdaClient.getClient().listAliases(listAliasRequest);
      }
      return AwsLambdaDetailsResponse.from(getFunctionResult, listAliasesResult);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }
}
