/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_HINT;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadResponse.AzureArtifactDownloadResponseBuilder;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ngexception.AzureAppServiceTaskException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class AzureArtifactDownloadServiceImpl implements AzureArtifactDownloadService {
  @Inject private ArtifactoryNgService artifactoryNgService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsApiHelperService awsApiHelperService;

  @Override
  public AzureArtifactDownloadResponse download(ArtifactDownloadContext artifactDownloadContext) {
    AzureArtifactDownloadResponseBuilder artifactResponseBuilder =
        AzureArtifactDownloadResponse.builder().artifactType(ArtifactType.ZIP);
    InputStream artifactStream;
    AzurePackageArtifactConfig artifactConfig = artifactDownloadContext.getArtifactConfig();
    final LogCallback logCallback = artifactDownloadContext.getLogCallbackProvider().obtainLogCallback(
        artifactDownloadContext.getCommandUnitName());

    try {
      switch (artifactConfig.getSourceType()) {
        case ARTIFACTORY_REGISTRY:
          artifactStream = downloadFromArtifactory(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case AMAZONS3:
          artifactStream = downloadFromAwsS3(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        default:
          throw NestedExceptionUtils.hintWithExplanationException("Use supported artifact registry",
              format("Registry of type '%s' is not supported yet", artifactConfig.getSourceType().getDisplayName()),
              new InvalidArgumentsException(Pair.of("sourceType", "Unsupported artifact source type")));
      }

      File artifactFile =
          AzureArtifactUtils.copyArtifactStreamToWorkingDirectory(artifactDownloadContext, artifactStream, logCallback);
      logCallback.saveExecutionLog("" /* Empty line */);
      logCallback.saveExecutionLog(
          color(format("Successfully downloaded artifact '%s'", artifactConfig.getArtifactDetails().getArtifactName()),
              LogColor.White, LogWeight.Bold),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return artifactResponseBuilder.artifactFile(artifactFile).build();
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          format("Failed to download artifact '%s' due to: %s", artifactConfig.getArtifactDetails().getArtifactName(),
              ExceptionUtils.getMessage(e)),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  private InputStream downloadFromAwsS3(AzurePackageArtifactConfig s3ArtifactConfig,
      AzureArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    AwsS3AzureArtifactRequestDetails artifactRequestDetails =
        (AwsS3AzureArtifactRequestDetails) s3ArtifactConfig.getArtifactDetails();
    validateAwsS3Artifact(s3ArtifactConfig, artifactRequestDetails, logCallback);
    logCallback.saveExecutionLog(color(format("Downloading %s artifact with identifier: %s",
                                           s3ArtifactConfig.getSourceType(), artifactRequestDetails.getIdentifier()),
        White, Bold));

    String artifactPath =
        Paths.get(artifactRequestDetails.getBucketName(), artifactRequestDetails.getFilePath()).toString();
    logCallback.saveExecutionLog("S3 Object Path: " + artifactPath);

    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        s3ArtifactConfig.getConnectorConfig(), s3ArtifactConfig.getEncryptedDataDetails());

    AwsInternalConfig awsConfig =
        awsNgConfigMapper.createAwsInternalConfig((AwsConnectorDTO) s3ArtifactConfig.getConnectorConfig());
    String region = EmptyPredicate.isNotEmpty(artifactRequestDetails.getRegion()) ? artifactRequestDetails.getRegion()
                                                                                  : AWS_DEFAULT_REGION;
    InputStream artifactInputStream;
    try {
      artifactInputStream = awsApiHelperService
                                .getObjectFromS3(awsConfig, region, artifactRequestDetails.getBucketName(),
                                    artifactRequestDetails.getFilePath())
                                .getObjectContent();

      if (artifactInputStream == null) {
        log.error("Failure in downloading artifact from S3");
        logCallback.saveExecutionLog("Failed to download artifact from S3.", ERROR, CommandExecutionStatus.FAILURE);
        throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
            String.format(DOWNLOAD_FROM_S3_EXPLANATION, artifactRequestDetails.getBucketName(),
                artifactRequestDetails.getFilePath()),
            new AzureAppServiceTaskException(format(DOWNLOAD_FROM_S3_FAILED, artifactRequestDetails.getIdentifier())));
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from s3", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download artifact from s3. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
          String.format(DOWNLOAD_FROM_S3_EXPLANATION, artifactRequestDetails.getBucketName(),
              artifactRequestDetails.getFilePath()),
          new AzureAppServiceTaskException(
              format(DOWNLOAD_FROM_S3_FAILED, artifactRequestDetails.getIdentifier()), sanitizedException));
    }
    artifactResponseBuilder.artifactType(
        AzureArtifactUtils.detectArtifactType(artifactRequestDetails.getFilePath(), logCallback));

    return artifactInputStream;
  }

  private void validateAwsS3Artifact(AzurePackageArtifactConfig s3ArtifactConfig,
      AwsS3AzureArtifactRequestDetails artifactRequestDetails, LogCallback logCallback) {
    if (!(s3ArtifactConfig.getArtifactDetails() instanceof AwsS3AzureArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              s3ArtifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format(
                  "Invalid artifact details, expected '%s'", AwsS3AzureArtifactRequestDetails.class.getSimpleName()))));
    }

    if (EmptyPredicate.isEmpty(artifactRequestDetails.getFilePath())) {
      logCallback.saveExecutionLog("artifact Path is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, artifactRequestDetails.getIdentifier()),
          new InvalidArgumentsException("not able to find artifact Path"));
    }
  }

  private InputStream downloadFromArtifactory(AzurePackageArtifactConfig artifactConfig,
      AzureArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
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

    if (!(artifactConfig.getArtifactDetails() instanceof ArtifactoryAzureArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              artifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format("Invalid artifact details, expected '%s'",
                  ArtifactoryAzureArtifactRequestDetails.class.getSimpleName()))));
    }

    ArtifactoryConnectorDTO artifactoryConnector = (ArtifactoryConnectorDTO) artifactConfig.getConnectorConfig();
    ArtifactoryAzureArtifactRequestDetails artifactDetails =
        (ArtifactoryAzureArtifactRequestDetails) artifactConfig.getArtifactDetails();
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnector);
    logCallback.saveExecutionLog(
        color(format("Downloading artifact '%s' from artifactory server %s", artifactDetails.getArtifactName(),
                  artifactoryConfigRequest.getArtifactoryUrl()),
            LogColor.White, LogWeight.Bold));

    artifactResponseBuilder.artifactType(
        AzureArtifactUtils.detectArtifactType(artifactDetails.getArtifactPath(), logCallback));

    return artifactoryNgService.downloadArtifacts(artifactoryConfigRequest, artifactDetails.getRepository(),
        artifactDetails.toMetadata(), ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactName);
  }
}
