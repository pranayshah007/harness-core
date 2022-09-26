/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.logging.LogLevel.ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.AwsS3DelegateTaskHelper;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.S3Object;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AwsS3ArtifactCommandUnitHandler extends ArtifactCommandUnitHandler {
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
  @Inject private AwsApiHelperService awsApiHelperService;

  @Override
  protected InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException {
    if (AwsS3ArtifactDelegateConfig.class.isAssignableFrom(context.getArtifactDelegateConfig().getClass())) {
      AwsS3ArtifactDelegateConfig s3ArtifactDelegateConfig =
          (AwsS3ArtifactDelegateConfig) context.getArtifactDelegateConfig();
      try {
        return getS3FileInputStream(s3ArtifactDelegateConfig, logCallback);
      } catch (Exception e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.error("Error while fetching S3 artifact", sanitizedException);
        logCallback.saveExecutionLog("Failed to download S3 artifact. " + ExceptionUtils.getMessage(sanitizedException),
            ERROR, CommandExecutionStatus.FAILURE);
        throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.S3_ARTIFACT_DOWNLOAD_HINT,
            format(SshExceptionConstants.S3_ARTIFACT_DOWNLOAD_EXPLANATION, s3ArtifactDelegateConfig.getArtifactPath(),
                s3ArtifactDelegateConfig.getBucketName()),
            new SshCommandExecutionException(format(SshExceptionConstants.S3_ARTIFACT_DOWNLOAD_FAILED,
                s3ArtifactDelegateConfig.getArtifactPath(), s3ArtifactDelegateConfig.getBucketName())));
      }
    }
    return null;
  }

  @Override
  public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
    if (AwsS3ArtifactDelegateConfig.class.isAssignableFrom(context.getArtifactDelegateConfig().getClass())) {
      AwsS3ArtifactDelegateConfig s3ArtifactDelegateConfig =
          (AwsS3ArtifactDelegateConfig) context.getArtifactDelegateConfig();
      try {
        updateArtifactMetadata(context);
        return getS3FileSize(s3ArtifactDelegateConfig, logCallback);
      } catch (Exception e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.error("Error while fetching S3 artifact", sanitizedException);
        logCallback.saveExecutionLog("Failed to download S3 artifact. " + ExceptionUtils.getMessage(sanitizedException),
            ERROR, CommandExecutionStatus.FAILURE);
        throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.S3_ARTIFACT_DOWNLOAD_HINT,
            format(SshExceptionConstants.S3_ARTIFACT_DOWNLOAD_EXPLANATION, s3ArtifactDelegateConfig.getArtifactPath(),
                s3ArtifactDelegateConfig.getBucketName()),
            new SshCommandExecutionException(format(SshExceptionConstants.S3_ARTIFACT_DOWNLOAD_FAILED,
                s3ArtifactDelegateConfig.getArtifactPath(), s3ArtifactDelegateConfig.getBucketName())));
      }
    }
    return 0L;
  }

  private Long getS3FileSize(
      AwsS3ArtifactDelegateConfig awsS3ArtifactDelegateConfig, LogCallback executionLogCallback) {
    awsS3DelegateTaskHelper.decryptRequestDTOs(
        awsS3ArtifactDelegateConfig.getAwsConnector(), awsS3ArtifactDelegateConfig.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsS3ArtifactDelegateConfig);
    String fileKey = awsS3ArtifactDelegateConfig.getArtifactPath();
    String bucketName = awsS3ArtifactDelegateConfig.getBucketName();
    executionLogCallback.saveExecutionLog(format("Fetching %s file from s3 bucket: %s", fileKey, bucketName));
    S3Object s3Object = awsApiHelperService.getObjectFromS3(
        awsInternalConfig, awsS3ArtifactDelegateConfig.getRegion(), bucketName, fileKey);
    return s3Object.getObjectMetadata().getContentLength();
  }

  private InputStream getS3FileInputStream(
      AwsS3ArtifactDelegateConfig awsS3FetchFileDelegateConfig, LogCallback executionLogCallback) {
    awsS3DelegateTaskHelper.decryptRequestDTOs(
        awsS3FetchFileDelegateConfig.getAwsConnector(), awsS3FetchFileDelegateConfig.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsS3FetchFileDelegateConfig);

    String fileKey = awsS3FetchFileDelegateConfig.getArtifactPath();
    String bucketName = awsS3FetchFileDelegateConfig.getBucketName();

    executionLogCallback.saveExecutionLog(format("Fetching file: %s from s3 bucket: %s", fileKey, bucketName));
    S3Object s3Object = awsApiHelperService.getObjectFromS3(
        awsInternalConfig, awsS3FetchFileDelegateConfig.getRegion(), bucketName, fileKey);
    return s3Object.getObjectContent();
  }

  private AwsInternalConfig getAwsInternalConfig(AwsS3ArtifactDelegateConfig awsS3ArtifactDelegateConfig) {
    AwsConnectorDTO awsConnectorDTO = awsS3ArtifactDelegateConfig.getAwsConnector();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(awsS3ArtifactDelegateConfig.getRegion());
    return awsInternalConfig;
  }

  private void updateArtifactMetadata(SshExecutorFactoryContext context) {
    AwsS3ArtifactDelegateConfig awsS3ArtifactDelegateConfig =
        (AwsS3ArtifactDelegateConfig) context.getArtifactDelegateConfig();
    Map<String, String> artifactMetadata = context.getArtifactMetadata();
    artifactMetadata.put(
        io.harness.artifact.ArtifactMetadataKeys.artifactPath, awsS3ArtifactDelegateConfig.getArtifactPath());
    artifactMetadata.put(ArtifactMetadataKeys.artifactName,
        Paths.get(awsS3ArtifactDelegateConfig.getArtifactPath()).getFileName().toString());
  }
}
