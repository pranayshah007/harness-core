/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.artifacts.azureartifacts.beans.AzureArtifactsExceptionConstants.DOWNLOAD_FROM_AZURE_ARTIFACTS_EXPLANATION;
import static io.harness.artifacts.azureartifacts.beans.AzureArtifactsExceptionConstants.DOWNLOAD_FROM_AZURE_ARTIFACTS_FAILED;
import static io.harness.artifacts.azureartifacts.beans.AzureArtifactsExceptionConstants.DOWNLOAD_FROM_AZURE_ARTIFACTS_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_FAILED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_HINT;
import static io.harness.delegate.utils.NexusUtils.getNexusArtifactFileName;
import static io.harness.delegate.utils.NexusUtils.getNexusVersion;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsProtocolType;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.mappers.AzureArtifactsRequestResponseMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadResponse.AzureArtifactDownloadResponseBuilder;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.utils.NexusVersion;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.AzureAppServiceTaskException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.nexus.NexusRequest;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class AzureArtifactDownloadServiceImpl implements AzureArtifactDownloadService {
  private static final String NEXUS_FAILED_DOWNLOAD_EXPLANATION = "Unable to download nexus artifact due to: ";
  private static final String NEXUS_FAILED_DOWNLOAD_HINT =
      "Review artifact configuration and nexus ConnectorDisconnectHandler details. For any intermittent network I/O issues please check delegate connectivity with Nexus server";

  @Inject private ArtifactoryNgService artifactoryNgService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsApiHelperService awsApiHelperService;
  @Inject private NexusService nexusService;
  @Inject private NexusMapper nexusMapper;
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private AzureArtifactsRegistryService azureArtifactsRegistryService;

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
        case NEXUS3_REGISTRY:
          artifactStream = downloadFromNexus(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case JENKINS:
          artifactStream = downloadFromJenkins(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case AZURE_ARTIFACTS:
          artifactStream = downloadFromAzureArtifacts(artifactConfig, artifactResponseBuilder, logCallback);
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

  private InputStream downloadFromJenkins(AzurePackageArtifactConfig artifactConfig,
      AzureArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    JenkinsAzureArtifactRequestDetails jenkinsAzureArtifactRequestDetails =
        (JenkinsAzureArtifactRequestDetails) artifactConfig.getArtifactDetails();
    validateJenkinsArtifact(artifactConfig, jenkinsAzureArtifactRequestDetails, logCallback);
    Pair<String, InputStream> pair;
    Jenkins jenkins = configureJenkins(artifactConfig);

    try {
      JenkinsConnectorDTO jenkinsConnectorDto = (JenkinsConnectorDTO) artifactConfig.getConnectorConfig();

      logCallback.saveExecutionLog(
          color(format("Downloading jenkins artifact: %s/job/%s/%s/artifact/%s", jenkinsConnectorDto.getJenkinsUrl(),
                    jenkinsAzureArtifactRequestDetails.getJobName(), jenkinsAzureArtifactRequestDetails.getBuild(),
                    jenkinsAzureArtifactRequestDetails.getArtifactPath()),
              White, Bold));
      pair = jenkins.downloadArtifact(jenkinsAzureArtifactRequestDetails.getJobName(),
          jenkinsAzureArtifactRequestDetails.getBuild(), jenkinsAzureArtifactRequestDetails.getArtifactPath());
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading jenkins artifact ", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download jenkins artifact. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(JENKINS_ARTIFACT_DOWNLOAD_HINT,
          format(JENKINS_ARTIFACT_DOWNLOAD_EXPLANATION, jenkinsAzureArtifactRequestDetails.getIdentifier()),
          new InvalidArgumentsException(
              format(JENKINS_ARTIFACT_DOWNLOAD_FAILED, jenkinsAzureArtifactRequestDetails.getIdentifier())));
    }
    if (null != pair) {
      artifactResponseBuilder.artifactType(
          AzureArtifactUtils.detectArtifactType(jenkinsAzureArtifactRequestDetails.getArtifactPath(), logCallback));
      return pair.getRight();
    } else {
      return null;
    }
  }

  private Jenkins configureJenkins(AzurePackageArtifactConfig artifactConfig) {
    JenkinsConnectorDTO jenkinsConnectorDto = (JenkinsConnectorDTO) artifactConfig.getConnectorConfig();
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

  private InputStream downloadFromAzureArtifacts(AzurePackageArtifactConfig artifactConfig,
      AzureArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    AzureDevOpsArtifactRequestDetails azureArtifactsRequestDetails =
        (AzureDevOpsArtifactRequestDetails) artifactConfig.getArtifactDetails();

    validateAzureDevOpsArtifact(artifactConfig, azureArtifactsRequestDetails, logCallback);

    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(
            (AzureArtifactsConnectorDTO) artifactConfig.getConnectorConfig());

    String project = azureArtifactsRequestDetails.getProject();
    String packageName = azureArtifactsRequestDetails.getPackageName();
    String feed = azureArtifactsRequestDetails.getFeed();
    String protocolType = azureArtifactsRequestDetails.getPackageType();
    String version = azureArtifactsRequestDetails.getVersion();

    if (isBlank(version)) {
      throw new InvalidRequestException("Artifact version is invalid");
    }

    String errorMessageExplanation =
        String.format(DOWNLOAD_FROM_AZURE_ARTIFACTS_EXPLANATION, packageName, protocolType, feed, version);
    Pair<String, InputStream> pair;
    try {
      pair = azureArtifactsRegistryService.downloadArtifact(
          azureArtifactsInternalConfig, project, feed, protocolType, packageName, version);
      artifactResponseBuilder.artifactType(detectArtifactType(pair.getKey(), protocolType, logCallback));
    } catch (Exception exception) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(exception);
      log.error("Failure in downloading artifact from Azure Artifacts", sanitizedException);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_AZURE_ARTIFACTS_HINT,
          errorMessageExplanation,
          new AzureAppServiceTaskException(
              format(DOWNLOAD_FROM_AZURE_ARTIFACTS_FAILED, azureArtifactsRequestDetails.getIdentifier()),
              sanitizedException));
    }

    if (pair.getRight() != null) {
      return pair.getRight();
    } else {
      log.error("Failure in downloading artifact from Azure Artifacts");
      logCallback.saveExecutionLog(
          "Failed to download artifact from Azure Artifacts.", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_AZURE_ARTIFACTS_HINT,
          errorMessageExplanation,
          new AzureAppServiceTaskException(
              format(DOWNLOAD_FROM_AZURE_ARTIFACTS_FAILED, azureArtifactsRequestDetails.getIdentifier())));
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

  private void validateJenkinsArtifact(AzurePackageArtifactConfig jenkinsArtifactConfig,
      JenkinsAzureArtifactRequestDetails artifactRequestDetails, LogCallback logCallback) {
    if (!(jenkinsArtifactConfig.getArtifactDetails() instanceof JenkinsAzureArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              jenkinsArtifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format("Invalid artifact details, expected '%s'",
                  JenkinsAzureArtifactRequestDetails.class.getSimpleName()))));
    }

    if (EmptyPredicate.isEmpty(artifactRequestDetails.getArtifactName())) {
      logCallback.saveExecutionLog("artifact Path is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, artifactRequestDetails.getIdentifier()),
          new InvalidArgumentsException("not able to find artifact Path"));
    }
  }

  private void validateAzureDevOpsArtifact(AzurePackageArtifactConfig azureDevopsArtifactConfig,
      AzureDevOpsArtifactRequestDetails artifactRequestDetails, LogCallback logCallback) {
    if (!(azureDevopsArtifactConfig.getArtifactDetails() instanceof AzureDevOpsArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              azureDevopsArtifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format("Invalid artifact details, expected '%s'",
                  AzureDevOpsArtifactRequestDetails.class.getSimpleName()))));
    }

    if (EmptyPredicate.isEmpty(artifactRequestDetails.getArtifactName())) {
      logCallback.saveExecutionLog("artifact Path is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, artifactRequestDetails.getIdentifier()),
          new InvalidArgumentsException("not able to find artifact Path"));
    }
  }

  private ArtifactType detectArtifactType(String artifactName, String packageType, LogCallback logCallback) {
    if (AzureArtifactsProtocolType.nuget.name().equalsIgnoreCase(packageType)) {
      log.info("Detected nuget artifact type for file {}", artifactName);
      logCallback.saveExecutionLog("Detected artifact type: nuget");
      return ArtifactType.NUGET;
    }
    return AzureArtifactUtils.detectArtifactType(artifactName, logCallback);
  }

  private InputStream downloadFromArtifactory(AzurePackageArtifactConfig artifactConfig,
      AzureArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    if (!(artifactConfig.getConnectorConfig() instanceof ArtifactoryConnectorDTO)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Configure artifactory ConnectorDisconnectHandler for artifactory configuration",
          format("Unexpected ConnectorDisconnectHandler type '%s' for artifactory configuration",
              artifactConfig.getConnectorConfig().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("connectorConfig",
              format("Invalid ConnectorDisconnectHandler type '%s', expected '%s'",
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

  private InputStream downloadFromNexus(AzurePackageArtifactConfig artifactConfig,
      AzureArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    if (!(artifactConfig.getConnectorConfig() instanceof NexusConnectorDTO)) {
      throw NestedExceptionUtils.hintWithExplanationException("Configure nexus ConnectorDisconnectHandler for nexus configuration",
          format("Unexpected ConnectorDisconnectHandler type '%s' for nexus configuration",
              artifactConfig.getConnectorConfig().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("connectorConfig",
              format("Invalid ConnectorDisconnectHandler type '%s', expected '%s'",
                  artifactConfig.getConnectorConfig().getClass().getSimpleName(),
                  NexusConnectorDTO.class.getSimpleName()))));
    }

    if (!(artifactConfig.getArtifactDetails() instanceof NexusAzureArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              artifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format(
                  "Invalid artifact details, expected '%s'", NexusAzureArtifactRequestDetails.class.getSimpleName()))));
    }

    NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) artifactConfig.getConnectorConfig();
    NexusAzureArtifactRequestDetails requestDetails =
        (NexusAzureArtifactRequestDetails) artifactConfig.getArtifactDetails();

    NexusVersion nexusVersion = getNexusVersion(nexusConnectorDTO);
    NexusRequest nexusRequest = nexusMapper.toNexusRequest(nexusConnectorDTO, requestDetails);
    String artifactUrl = requestDetails.getArtifactUrl();

    try {
      String artifactName =
          getNexusArtifactFileName(nexusVersion, requestDetails.getRepositoryFormat(), requestDetails.getMetadata());
      logCallback.saveExecutionLog(
          color(format("Downloading artifact '%s' from nexus url '%s'", artifactName, requestDetails.getArtifactUrl()),
              LogColor.White, LogWeight.Bold));

      artifactResponseBuilder.artifactType(getArtifactType(requestDetails, logCallback));

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
          NEXUS_FAILED_DOWNLOAD_EXPLANATION + message, new AzureAppServiceTaskException(message));
    }
  }

  private ArtifactType getArtifactType(NexusAzureArtifactRequestDetails nexusRequestDetails, LogCallback logCallback) {
    String repositoryFormat = nexusRequestDetails.getRepositoryFormat();
    if (RepositoryFormat.nuget.name().equals(repositoryFormat)) {
      logCallback.saveExecutionLog(format(
          "Detected artifact type '%s' based on repository format '%s'", ArtifactType.NUGET.name(), repositoryFormat));
      return ArtifactType.NUGET;
    }

    return AzureArtifactUtils.detectArtifactType(nexusRequestDetails.getArtifactUrl(), logCallback);
  }
}
