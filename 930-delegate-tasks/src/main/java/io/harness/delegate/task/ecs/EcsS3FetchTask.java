/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.ecs.request.EcsS3FetchRequest;
import io.harness.delegate.task.ecs.request.EcsS3FetchRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsS3FetchResponse;
import io.harness.delegate.task.ecs.response.EcsS3FetchRunTaskResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.secret.SecretSanitizerThreadLocal;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.S3Object;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class EcsS3FetchTask extends AbstractDelegateRunnableTask {
  @Inject private AwsApiHelperService awsApiHelperService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject DecryptionHelper decryptionHelper;

  public EcsS3FetchTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      DelegateResponseData responseData = null;
      if (parameters instanceof EcsS3FetchRequest) {
        responseData = getEcsS3CommandResponse(parameters, commandUnitsProgress);
      } else {
        responseData = getRunTaskS3CommandResponse(parameters, commandUnitsProgress);
      }
      return responseData;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in S3 Fetch Files Task", sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  private DelegateResponseData getEcsS3CommandResponse(
      TaskParameters parameters, CommandUnitsProgress commandUnitsProgress) throws Exception {
    EcsS3FetchRequest ecsS3FetchRequest = (EcsS3FetchRequest) parameters;
    log.info("Running Ecs S3 Fetch Task for activityId {}", ecsS3FetchRequest.getActivityId());

    LogCallback executionLogCallback =
        new NGDelegateLogCallback(getLogStreamingTaskClient(), EcsCommandUnitConstants.fetchManifests.toString(),
            ecsS3FetchRequest.isShouldOpenLogStream(), commandUnitsProgress);
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
  }

  private DelegateResponseData getRunTaskS3CommandResponse(
      TaskParameters parameters, CommandUnitsProgress commandUnitsProgress) throws Exception {
    EcsS3FetchRunTaskRequest ecsS3FetchRunTaskRequest = (EcsS3FetchRunTaskRequest) parameters;

    log.info("Running Ecs S3 Fetch Task for activityId {}", ecsS3FetchRunTaskRequest.getActivityId());

    LogCallback executionLogCallback =
        new NGDelegateLogCallback(getLogStreamingTaskClient(), EcsCommandUnitConstants.fetchManifests.toString(),
            ecsS3FetchRunTaskRequest.isShouldOpenLogStream(), commandUnitsProgress);
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
  }

  private String fetchS3ManifestsContent(EcsS3FetchFileConfig ecsS3FetchFileConfig, LogCallback executionLogCallback)
      throws Exception {
    executionLogCallback.saveExecutionLog(format("Fetching %s config file with identifier: %s",
        ecsS3FetchFileConfig.getManifestType(), ecsS3FetchFileConfig.getIdentifier(), White, Bold));
    S3StoreDelegateConfig s3StoreConfig = ecsS3FetchFileConfig.getS3StoreDelegateConfig();
    String filePath = s3StoreConfig.getPaths().get(0);
    executionLogCallback.saveExecutionLog(
        format("bucketName: %s, filePath: %s", s3StoreConfig.getBucketName(), filePath, White, Bold));

    decrypt(s3StoreConfig);
    AwsInternalConfig awsConfig = awsNgConfigMapper.createAwsInternalConfig(s3StoreConfig.getAwsConnector());
    try {
      return getS3Content(awsConfig, s3StoreConfig.getRegion(), s3StoreConfig.getBucketName(), filePath);
    } catch (Exception ex) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Please checks inputs configured in Manifest section in Harness Service are correct. "
                  + "Check if AWS credentials are correct."
                  + "Check if s3 bucket %s and file %s exists in region %s",
              s3StoreConfig.getBucketName(), filePath, s3StoreConfig.getRegion()),
          format("Error while fetching file from s3 bucket %s with filePath %s in region %s",
              s3StoreConfig.getBucketName(), filePath, s3StoreConfig.getRegion()),
          ex);
    }
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

  private String getS3Content(AwsInternalConfig awsConfig, String region, String bucketName, String key)
      throws IOException {
    S3Object object = awsApiHelperService.getObjectFromS3(awsConfig, region, bucketName, key);
    InputStream inputStream = object.getObjectContent();
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    String message = org.apache.commons.io.IOUtils.toString(reader);
    return message;
  }

  public boolean isSupportingErrorFramework() {
    return true;
  }
}
