/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsS3FetchFileConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsS3FetchRequest;
import io.harness.delegate.task.ecs.request.EcsS3FetchRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsS3FetchResponse;
import io.harness.delegate.task.ecs.response.EcsS3FetchRunTaskResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.S3Object;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsS3FetchCommandTaskHandler {
  @Inject private AwsApiHelperService awsApiHelperService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private DecryptionHelper decryptionHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;

  public DelegateResponseData getS3FetchResponse(
      TaskParameters parameters, ILogStreamingTaskClient iLogStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = null;
    DelegateResponseData responseData = null;
    if (parameters instanceof EcsS3FetchRequest) {
      EcsS3FetchRequest ecsS3FetchRequest = (EcsS3FetchRequest) parameters;
      commandUnitsProgress = ecsS3FetchRequest.getCommandUnitsProgress() != null
          ? ecsS3FetchRequest.getCommandUnitsProgress()
          : CommandUnitsProgress.builder().build();
      responseData = getEcsS3FetchResponse(ecsS3FetchRequest, commandUnitsProgress, iLogStreamingTaskClient);
    } else if (parameters instanceof EcsS3FetchRunTaskRequest) {
      EcsS3FetchRunTaskRequest ecsS3FetchRunTaskRequest = (EcsS3FetchRunTaskRequest) parameters;
      commandUnitsProgress = ecsS3FetchRunTaskRequest.getCommandUnitsProgress() != null
          ? ecsS3FetchRunTaskRequest.getCommandUnitsProgress()
          : CommandUnitsProgress.builder().build();
      responseData = getRunTaskS3FetchResponse(ecsS3FetchRunTaskRequest, commandUnitsProgress, iLogStreamingTaskClient);
    }
    log.info("Completed ECS S3 task execution");
    return responseData;
  }

  private DelegateResponseData getEcsS3FetchResponse(EcsS3FetchRequest ecsS3FetchRequest,
      CommandUnitsProgress commandUnitsProgress, ILogStreamingTaskClient iLogStreamingTaskClient) {
    log.info("Running Ecs S3 Fetch Task for activityId {}", ecsS3FetchRequest.getActivityId());
    LogCallback executionLogCallback =
        ecsTaskHelperBase.getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.fetchManifests.toString(),
            ecsS3FetchRequest.isShouldOpenLogStream(), commandUnitsProgress);
    try {
      executionLogCallback.saveExecutionLog(
          format("Started Fetching S3 Manifest files... ", LogColor.White, LogWeight.Bold));

      String ecsS3TaskDefinitionContent = null;
      if (ecsS3FetchRequest.getEcsTaskDefinitionS3FetchFileConfig() != null) {
        ecsS3TaskDefinitionContent =
            fetchS3ManifestsContent(ecsS3FetchRequest.getEcsTaskDefinitionS3FetchFileConfig(), executionLogCallback);
      }

      String ecsS3ServiceDefinitionContent = null;
      if (ecsS3FetchRequest.getEcsServiceDefinitionS3FetchFileConfig() != null) {
        ecsS3ServiceDefinitionContent =
            fetchS3ManifestsContent(ecsS3FetchRequest.getEcsServiceDefinitionS3FetchFileConfig(), executionLogCallback);
      }

      List<String> ecsS3ScalableTargetContents = new ArrayList<>();
      if (CollectionUtils.isNotEmpty(ecsS3FetchRequest.getEcsScalableTargetS3FetchFileConfigs())) {
        for (EcsS3FetchFileConfig ecsS3FetchFileConfig : ecsS3FetchRequest.getEcsScalableTargetS3FetchFileConfigs()) {
          ecsS3ScalableTargetContents.add(fetchS3ManifestsContent(ecsS3FetchFileConfig, executionLogCallback));
        }
      }

      List<String> ecsS3ScalingPolicyContents = new ArrayList<>();
      if (CollectionUtils.isNotEmpty(ecsS3FetchRequest.getEcsScalingPolicyS3FetchFileConfigs())) {
        for (EcsS3FetchFileConfig ecsS3FetchFileConfig : ecsS3FetchRequest.getEcsScalingPolicyS3FetchFileConfigs()) {
          ecsS3ScalingPolicyContents.add(fetchS3ManifestsContent(ecsS3FetchFileConfig, executionLogCallback));
        }
      }

      executionLogCallback.saveExecutionLog(
          color(format("%nFetched all S3 manifests successfully..%n"), LogColor.White, LogWeight.Bold), INFO,
          CommandExecutionStatus.SUCCESS);

      return EcsS3FetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .ecsS3TaskDefinitionContent(ecsS3TaskDefinitionContent)
          .ecsS3ServiceDefinitionContent(ecsS3ServiceDefinitionContent)
          .ecsS3ScalableTargetContents(ecsS3ScalableTargetContents)
          .ecsS3ScalingPolicyContents(ecsS3ScalingPolicyContents)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(format("error while fetching s3 manifest for Ecs"), sanitizedException);
      executionLogCallback.saveExecutionLog(
          color(format("%n File fetch failed with error: %s", ExceptionUtils.getMessage(sanitizedException)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      if (e instanceof HintException) {
        throw new TaskNGDataException(
            UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
      } else {
        return EcsS3FetchResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      }
    }
  }

  private DelegateResponseData getRunTaskS3FetchResponse(EcsS3FetchRunTaskRequest ecsS3FetchRunTaskRequest,
      CommandUnitsProgress commandUnitsProgress, ILogStreamingTaskClient iLogStreamingTaskClient) {
    log.info("Running Ecs S3 Fetch Task for activityId {}", ecsS3FetchRunTaskRequest.getActivityId());
    LogCallback executionLogCallback =
        ecsTaskHelperBase.getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.fetchManifests.toString(),
            ecsS3FetchRunTaskRequest.isShouldOpenLogStream(), commandUnitsProgress);
    try {
      executionLogCallback.saveExecutionLog(
          format("Started Fetching Run Task S3 Manifest files... ", LogColor.White, LogWeight.Bold));

      String runTaskS3TaskDefinitionContent = null;
      if (ecsS3FetchRunTaskRequest.getRunTaskDefinitionS3FetchFileConfig() != null) {
        runTaskS3TaskDefinitionContent = fetchS3ManifestsContent(
            ecsS3FetchRunTaskRequest.getRunTaskDefinitionS3FetchFileConfig(), executionLogCallback);
      }

      String runTaskS3TaskRequestDefinitionContent = null;
      if (ecsS3FetchRunTaskRequest.getRunTaskRequestDefinitionS3FetchFileConfig() != null) {
        runTaskS3TaskRequestDefinitionContent = fetchS3ManifestsContent(
            ecsS3FetchRunTaskRequest.getRunTaskRequestDefinitionS3FetchFileConfig(), executionLogCallback);
      }

      executionLogCallback.saveExecutionLog(
          color(format("%nFetched Run Task S3 manifests successfully..%n"), LogColor.White, LogWeight.Bold), INFO,
          CommandExecutionStatus.SUCCESS);

      return EcsS3FetchRunTaskResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .runTaskDefinitionFileContent(runTaskS3TaskDefinitionContent)
          .runTaskRequestDefinitionFileContent(runTaskS3TaskRequestDefinitionContent)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(format("error while fetching s3 manifest for Ecs Run Task"), sanitizedException);
      executionLogCallback.saveExecutionLog(
          color(format("%n File fetch failed with error: %s", ExceptionUtils.getMessage(sanitizedException)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      if (e instanceof HintException) {
        throw new TaskNGDataException(
            UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
      } else {
        return EcsS3FetchResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      }
    }
  }

  private String fetchS3ManifestsContent(EcsS3FetchFileConfig ecsS3FetchFileConfig, LogCallback executionLogCallback)
      throws IOException {
    executionLogCallback.saveExecutionLog(format("Fetching %s config file with identifier: %s",
        ecsS3FetchFileConfig.getManifestType(), ecsS3FetchFileConfig.getIdentifier(), White, Bold));
    S3StoreDelegateConfig s3StoreConfig = ecsS3FetchFileConfig.getS3StoreDelegateConfig();
    String filePath = s3StoreConfig.getPaths().get(0);
    executionLogCallback.saveExecutionLog(
        format("bucketName: %s, filePath: %s", s3StoreConfig.getBucketName(), filePath, White, Bold));

    decrypt(s3StoreConfig);
    AwsInternalConfig awsConfig = awsNgConfigMapper.createAwsInternalConfig(s3StoreConfig.getAwsConnector());
    return getS3Content(
        awsConfig, s3StoreConfig.getRegion(), s3StoreConfig.getBucketName(), filePath, executionLogCallback);
  }

  private void decrypt(S3StoreDelegateConfig s3StoreConfig) {
    List<DecryptableEntity> s3DecryptableEntityList = s3StoreConfig.getAwsConnector().getDecryptableEntities();
    if (isNotEmpty(s3DecryptableEntityList)) {
      for (DecryptableEntity decryptableEntity : s3DecryptableEntityList) {
        decryptionHelper.decrypt(decryptableEntity, s3StoreConfig.getEncryptedDataDetails());
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
            decryptableEntity, s3StoreConfig.getEncryptedDataDetails());
      }
    }
  }

  private String getS3Content(AwsInternalConfig awsConfig, String region, String bucketName, String key,
      LogCallback executionLogCallback) throws IOException {
    S3Object object = null;
    try {
      object = awsApiHelperService.getObjectFromS3(awsConfig, region, bucketName, key);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in fetching file from S3", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download manifest file from S3. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Please check the following Harness S3 Manifest Inputs\n"
              + " Aws Credentials\n"
              + " S3 Bucket Name\n"
              + " Region\n"
              + " File Path"),
          format("Failed while fetching the file [%s] from S3 bucket [%s] in region [%s]", key, bucketName, region),
          new ServerlessCommandExecutionException("Failed while fetching files from S3", sanitizedException));
    }
    InputStream inputStream = object.getObjectContent();
    return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
  }
}
