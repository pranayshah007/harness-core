/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadResponse.AzureArtifactDownloadResponseBuilder;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.io.File;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class AzureArtifactDownloadServiceImpl implements AzureArtifactDownloadService {
  @Inject private ArtifactoryNgService artifactoryNgService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;

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
