/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFileDelegateConfig;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesResponse;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesTaskParams;
import io.harness.delegate.beans.aws.s3.S3FileDetailRequest;
import io.harness.delegate.beans.aws.s3.S3FileDetailResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.WingsException;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.S3Object;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.util.StreamUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class S3FetchFilesTaskNG extends AbstractDelegateRunnableTask {
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
  @Inject private AwsApiHelperService awsApiHelperService;

  public S3FetchFilesTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final AwsS3FetchFilesTaskParams params = (AwsS3FetchFilesTaskParams) parameters;
    CommandUnitsProgress commandUnitsProgress = params.getCommandUnitsProgress() != null
        ? params.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    LogCallback executionLogCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(),
        K8sCommandUnitConstants.FetchFiles, params.isShouldOpenLogStream(), commandUnitsProgress);

    try {
      executionLogCallback.saveExecutionLog(color(format("%nStarting S3 Fetch Files"), LogColor.White, LogWeight.Bold));

      Map<String, Map<String, String>> keyVersionMap = new HashMap<>();
      Map<String, List<S3FileDetailResponse>> s3filesDetails = new HashMap<>();
      for (AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig : params.getFetchFileDelegateConfigs()) {
        s3filesDetails.put(awsS3FetchFileDelegateConfig.getIdentifier(),
            getContentFromFromS3Bucket(awsS3FetchFileDelegateConfig, executionLogCallback, keyVersionMap));
      }

      if (params.isCloseLogStream()) {
        executionLogCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);
      }

      return AwsS3FetchFilesResponse.builder()
          .s3filesDetails(s3filesDetails)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .keyVersionMap(keyVersionMap)
          .build();
    } catch (Exception e) {
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), e);
    }
  }

  private List<S3FileDetailResponse> getContentFromFromS3Bucket(
      AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig, LogCallback executionLogCallback,
      Map<String, Map<String, String>> keyVersionMap) throws IOException {
    awsS3DelegateTaskHelper.decryptRequestDTOs(
        awsS3FetchFileDelegateConfig.getAwsConnector(), awsS3FetchFileDelegateConfig.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsS3FetchFileDelegateConfig);
    List<S3FileDetailResponse> s3FileDetailResponses = new ArrayList<>();

    Map<String, String> versionMap = keyVersionMap.get(awsS3FetchFileDelegateConfig.getIdentifier()) == null
        ? new HashMap<>()
        : keyVersionMap.get(awsS3FetchFileDelegateConfig.getIdentifier());

    for (S3FileDetailRequest s3FileDetailRequest : awsS3FetchFileDelegateConfig.getFileDetails()) {
      String fileKey = s3FileDetailRequest.getFileKey();
      String bucketName = s3FileDetailRequest.getBucketName();

      executionLogCallback.saveExecutionLog(format("%nFetching %s file from s3 bucket: %s", fileKey, bucketName));
      try {
        boolean versioningEnabled = awsApiHelperService.isVersioningEnabledForBucket(
            awsInternalConfig, bucketName, awsS3FetchFileDelegateConfig.getRegion());

        String version = isEmpty(awsS3FetchFileDelegateConfig.getVersions())
            ? null
            : awsS3FetchFileDelegateConfig.getVersions().get(fileKey);

        S3Object s3Object = version != null && versioningEnabled
            ? awsApiHelperService.getVersionedObjectFromS3(awsInternalConfig, awsS3FetchFileDelegateConfig.getRegion(),
                bucketName, fileKey, awsS3FetchFileDelegateConfig.getVersions().get(fileKey))
            : awsApiHelperService.getObjectFromS3(
                awsInternalConfig, awsS3FetchFileDelegateConfig.getRegion(), bucketName, fileKey);

        if (versioningEnabled) {
          versionMap.put(fileKey, s3Object.getObjectMetadata().getVersionId());
        }

        InputStream is = s3Object.getObjectContent();
        S3FileDetailResponse s3FileDetailResponse =
            S3FileDetailResponse.builder()
                .fileKey(fileKey)
                .bucketName(bucketName)
                .fileContent(StreamUtils.copyToString(is, StandardCharsets.UTF_8))
                .build();
        is.close();
        s3FileDetailResponses.add(s3FileDetailResponse);
        executionLogCallback.saveExecutionLog(
            format("%nSuccessfully fetched %s file from s3 bucket: %s", fileKey, bucketName));
      } catch (Exception e) {
        String exceptionMessage = "";
        if (e instanceof WingsException) {
          WingsException wingsException = (WingsException) e;
          if (wingsException.getParams() != null && wingsException.getParams().get("message") != null) {
            exceptionMessage = wingsException.getParams().get("message").toString();
          }
        }

        String errorMsg = format("Failed to fetch %s from s3 bucket: %s : %s", fileKey, bucketName,
            exceptionMessage.isEmpty() ? e.getMessage() : exceptionMessage);
        log.error(errorMsg, e.getMessage());
        executionLogCallback.saveExecutionLog(errorMsg, ERROR, CommandExecutionStatus.FAILURE);
        throw e;
      }
    }
    keyVersionMap.put(awsS3FetchFileDelegateConfig.getIdentifier(), versionMap);
    return s3FileDetailResponses;
  }

  private AwsInternalConfig getAwsInternalConfig(AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig) {
    AwsConnectorDTO awsConnectorDTO = awsS3FetchFileDelegateConfig.getAwsConnector();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(awsS3FetchFileDelegateConfig.getRegion());
    return awsInternalConfig;
  }
}
