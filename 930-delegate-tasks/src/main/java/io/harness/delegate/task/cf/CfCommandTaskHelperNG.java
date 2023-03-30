/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.artifacts.azureartifacts.beans.AzureArtifactsExceptionConstants.DOWNLOAD_FROM_AZURE_ARTIFACTS_EXPLANATION;
import static io.harness.artifacts.azureartifacts.beans.AzureArtifactsExceptionConstants.DOWNLOAD_FROM_AZURE_ARTIFACTS_FAILED;
import static io.harness.artifacts.azureartifacts.beans.AzureArtifactsExceptionConstants.DOWNLOAD_FROM_AZURE_ARTIFACTS_HINT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
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
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.model.CfConstants.CF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.pcf.model.CfConstants.REPOSITORY_DIR_PATH;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsProtocolType;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfRollbackCommandResult;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.cf.apprenaming.AppRenamingOperator;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.mappers.AzureArtifactsRequestResponseMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.azure.artifact.AzureArtifactUtils;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse.TasArtifactDownloadResponseBuilder;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.task.pcf.artifact.ArtifactoryTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.AwsS3TasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.AzureDevOpsTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.JenkinsTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.NexusTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.TasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfRollbackCommandRequestNG;
import io.harness.delegate.utils.CFLogCallbackFormatter;
import io.harness.delegate.utils.NexusVersion;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.FileCopyException;
import io.harness.exception.FileCreationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.nexus.NexusRequest;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.security.encryption.EncryptedDataDetail;

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
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class CfCommandTaskHelperNG {
  public static final String DELIMITER = "__";
  public static final String APPLICATION = "APPLICATION: ";
  private static final String NEXUS_FAILED_DOWNLOAD_EXPLANATION = "Unable to download nexus artifact due to: ";
  private static final String NEXUS_FAILED_DOWNLOAD_HINT =
      "Review artifact configuration and nexus connector details. For any intermittent network I/O issues please check delegate connectivity with Nexus server";
  public static final String ARTIFACT_NAME_PREFIX = "artifact-";
  private static final String APPLICATION_NAME = "APPLICATION-NAME:";
  private static final String DESIRED_INSTANCE_COUNT = "DESIRED-INSTANCE-COUNT: ";

  @Inject PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject private ArtifactoryNgService artifactoryNgService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject private DecryptionHelper decryptionHelper;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsApiHelperService awsApiHelperService;
  @Inject private NexusService nexusService;
  @Inject private NexusMapper nexusMapper;
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private AzureArtifactsRegistryService azureArtifactsRegistryService;

  public File generateWorkingDirectoryForDeployment() throws IOException {
    String workingDirecotry = UUIDGenerator.generateUuid();
    createDirectoryIfDoesNotExist(REPOSITORY_DIR_PATH);
    createDirectoryIfDoesNotExist(CF_ARTIFACT_DOWNLOAD_DIR_PATH);
    String workingDir = CF_ARTIFACT_DOWNLOAD_DIR_PATH + "/" + workingDirecotry;
    createDirectoryIfDoesNotExist(workingDir);
    return new File(workingDir);
  }

  public TasArtifactDownloadResponse downloadPackageArtifact(
      TasArtifactDownloadContext artifactDownloadContext, LogCallback logCallback) {
    TasArtifactDownloadResponseBuilder artifactResponseBuilder =
        TasArtifactDownloadResponse.builder().artifactType(ArtifactType.ZIP);
    InputStream artifactStream = null;
    TasPackageArtifactConfig artifactConfig = artifactDownloadContext.getArtifactConfig();

    try {
      switch (artifactConfig.getSourceType()) {
        case ARTIFACTORY_REGISTRY:
          artifactStream = downloadFromArtifactory(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case AMAZONS3:
          artifactStream = downloadFromAwsS3(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case NEXUS3_REGISTRY:
        case NEXUS2_REGISTRY:
          artifactStream = downloadFromNexus(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case JENKINS:
          artifactStream = downloadFromJenkins(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case AZURE_ARTIFACTS:
          artifactStream = downloadFromAzureArtifacts(artifactConfig, artifactResponseBuilder, logCallback);
          break;
        case CUSTOM_ARTIFACT:
          break;
        default:
          throw NestedExceptionUtils.hintWithExplanationException("Use supported artifact registry",
              format("Registry of type '%s' is not supported yet", artifactConfig.getSourceType().getDisplayName()),
              new InvalidArgumentsException(Pair.of("sourceType", "Unsupported artifact source type")));
      }
      if (!isNull(artifactStream)) {
        File artifactFile = copyArtifactStreamToWorkingDirectory(artifactDownloadContext, artifactStream, logCallback);
        artifactResponseBuilder.artifactFile(artifactFile);
      }
      logCallback.saveExecutionLog("" /* Empty line */);
      logCallback.saveExecutionLog(
          color(format("Successfully downloaded artifact '%s'", artifactConfig.getArtifactDetails().getArtifactName()),
              LogColor.White, LogWeight.Bold));

      return artifactResponseBuilder.build();
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          format("Failed to download artifact '%s' due to: %s", artifactConfig.getArtifactDetails().getArtifactName(),
              ExceptionUtils.getMessage(e)),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  public File copyArtifactStreamToWorkingDirectory(
      TasArtifactDownloadContext downloadContext, InputStream artifactStream, LogCallback logCallback) {
    final TasArtifactRequestDetails artifactDetails = downloadContext.getArtifactConfig().getArtifactDetails();
    final File artifactFile = createArtifactFileInWorkingDirectory(downloadContext);
    try (FileOutputStream output = new FileOutputStream(artifactFile)) {
      logCallback.saveExecutionLog(
          format("Copy artifact '%s' to '%s'", artifactDetails.getArtifactName(), artifactFile.getPath()));
      IOUtils.copy(artifactStream, output);
      logCallback.saveExecutionLog(format(
          "Artifact '%s' successfully copied to '%s'", artifactDetails.getArtifactName(), artifactFile.getPath()));
      return artifactFile;
    } catch (IOException exception) {
      throw NestedExceptionUtils.hintWithExplanationException(HintException.HINT_FILE_CREATION_ERROR,
          ExplanationException.EXPLANATION_FILE_CREATION_ERROR,
          new FileCopyException(format("Failed to copy artifact file '%s' from input stream to path '%s' due to: %s",
              artifactDetails.getArtifactName(), artifactFile.getPath(), exception.getMessage())));
    }
  }

  private File createArtifactFileInWorkingDirectory(TasArtifactDownloadContext downloadContext) {
    final TasArtifactRequestDetails artifactDetails = downloadContext.getArtifactConfig().getArtifactDetails();
    final int fileNameIndex = artifactDetails.getArtifactName().lastIndexOf('/');
    final String fileName = fileNameIndex == -1 ? artifactDetails.getArtifactName()
                                                : artifactDetails.getArtifactName().substring(fileNameIndex + 1);
    final Path artifactFolderPath =
        Paths.get(downloadContext.getWorkingDirectory().getPath(), ARTIFACT_NAME_PREFIX + System.currentTimeMillis())
            .toAbsolutePath();
    final File artifactFolder = new File(artifactFolderPath.toString());
    final File artifactFile = new File(Paths.get(artifactFolderPath.toString(), fileName).toAbsolutePath().toString());

    try {
      if (!artifactFolder.mkdirs()) {
        throw NestedExceptionUtils.hintWithExplanationException(HintException.HINT_FILE_CREATION_ERROR,
            ExplanationException.EXPLANATION_FILE_CREATION_ERROR,
            new FileCreationException(format("Failed to create artifact folder '%s' for artifact '%s'",
                                          artifactFolderPath, artifactDetails.getArtifactName()),
                null, ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null));
      }

      if (!artifactFile.createNewFile()) {
        throw NestedExceptionUtils.hintWithExplanationException(HintException.HINT_FILE_CREATION_ERROR,
            ExplanationException.EXPLANATION_FILE_CREATION_ERROR,
            new FileCreationException(format("Failed to create a new file for artifact '%s' using artifact file '%s'",
                                          artifactDetails.getArtifactName(), artifactFile.getPath()),
                null, ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null));
      }

      return artifactFile;
    } catch (IOException exception) {
      IOException sanitizedException = ExceptionMessageSanitizer.sanitizeException(exception);
      log.error("Failed to create file {}", artifactFile.getPath(), sanitizedException);
      throw NestedExceptionUtils.hintWithExplanationException(HintException.HINT_FILE_CREATION_ERROR,
          ExplanationException.EXPLANATION_FILE_CREATION_ERROR,
          new FileCreationException(format("Failed to create a new file for artifact '%s' using artifact file '%s'",
                                        artifactDetails.getArtifactName(), artifactFile.getPath()),
              sanitizedException, ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null));
    }
  }

  private InputStream downloadFromJenkins(TasPackageArtifactConfig artifactConfig,
      TasArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    if (!(artifactConfig.getArtifactDetails() instanceof JenkinsTasArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              artifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format(
                  "Invalid artifact details, expected '%s'", JenkinsTasArtifactRequestDetails.class.getSimpleName()))));
    }

    JenkinsTasArtifactRequestDetails jenkinsTasArtifactRequestDetails =
        (JenkinsTasArtifactRequestDetails) artifactConfig.getArtifactDetails();
    validateJenkinsArtifact(jenkinsTasArtifactRequestDetails, logCallback);
    Pair<String, InputStream> pair = null;

    try {
      JenkinsConnectorDTO jenkinsConnectorDto = (JenkinsConnectorDTO) artifactConfig.getConnectorConfig();
      decryptEntity(
          decryptionHelper, jenkinsConnectorDto.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

      logCallback.saveExecutionLog(
          color(format("Downloading jenkins artifact: %s/job/%s/%s/artifact/%s", jenkinsConnectorDto.getJenkinsUrl(),
                    jenkinsTasArtifactRequestDetails.getJobName(), jenkinsTasArtifactRequestDetails.getBuild(),
                    jenkinsTasArtifactRequestDetails.getArtifactPath()),
              White, Bold));
      Jenkins jenkins = configureJenkins(artifactConfig);
      if (!isNull(jenkins)) {
        pair = jenkins.downloadArtifact(jenkinsTasArtifactRequestDetails.getJobName(),
            jenkinsTasArtifactRequestDetails.getBuild(), jenkinsTasArtifactRequestDetails.getArtifactPath());
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading jenkins artifact ", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download jenkins artifact. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(JENKINS_ARTIFACT_DOWNLOAD_HINT,
          format(JENKINS_ARTIFACT_DOWNLOAD_EXPLANATION, jenkinsTasArtifactRequestDetails.getIdentifier()),
          new InvalidArgumentsException(
              format(JENKINS_ARTIFACT_DOWNLOAD_FAILED, jenkinsTasArtifactRequestDetails.getIdentifier())));
    }
    if (pair != null) {
      artifactResponseBuilder.artifactType(
          AzureArtifactUtils.detectArtifactType(jenkinsTasArtifactRequestDetails.getArtifactPath(), logCallback));
      return pair.getRight();
    } else {
      return null;
    }
  }

  private Jenkins configureJenkins(TasPackageArtifactConfig artifactConfig) {
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

  private InputStream downloadFromAzureArtifacts(TasPackageArtifactConfig artifactConfig,
      TasArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    if (!(artifactConfig.getArtifactDetails() instanceof AzureDevOpsTasArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              artifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format("Invalid artifact details, expected '%s'",
                  AzureDevOpsTasArtifactRequestDetails.class.getSimpleName()))));
    }
    AzureDevOpsTasArtifactRequestDetails azureArtifactsRequestDetails =
        (AzureDevOpsTasArtifactRequestDetails) artifactConfig.getArtifactDetails();

    validateAzureDevOpsArtifact(azureArtifactsRequestDetails, logCallback);
    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        (AzureArtifactsConnectorDTO) artifactConfig.getConnectorConfig();
    decryptEntity(decryptionHelper, azureArtifactsConnectorDTO.getDecryptableEntities(),
        artifactConfig.getEncryptedDataDetails());
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(azureArtifactsConnectorDTO);

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
          new PivotalClientApiException(
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
          new PivotalClientApiException(
              format(DOWNLOAD_FROM_AZURE_ARTIFACTS_FAILED, azureArtifactsRequestDetails.getIdentifier())));
    }
  }

  private InputStream downloadFromAwsS3(TasPackageArtifactConfig s3ArtifactConfig,
      TasArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    if (!(s3ArtifactConfig.getArtifactDetails() instanceof AwsS3TasArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              s3ArtifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format(
                  "Invalid artifact details, expected '%s'", AwsS3TasArtifactRequestDetails.class.getSimpleName()))));
    }
    AwsS3TasArtifactRequestDetails artifactRequestDetails =
        (AwsS3TasArtifactRequestDetails) s3ArtifactConfig.getArtifactDetails();
    validateAwsS3Artifact(artifactRequestDetails, logCallback);
    logCallback.saveExecutionLog(color(format("Downloading %s artifact with identifier: %s",
                                           s3ArtifactConfig.getSourceType(), artifactRequestDetails.getIdentifier()),
        White, Bold));

    String artifactPath =
        Paths.get(artifactRequestDetails.getBucketName(), artifactRequestDetails.getFilePath()).toString();
    logCallback.saveExecutionLog("S3 Object Path: " + artifactPath);

    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        s3ArtifactConfig.getConnectorConfig(), s3ArtifactConfig.getEncryptedDataDetails());
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) s3ArtifactConfig.getConnectorConfig();
    decryptEntity(
        decryptionHelper, awsConnectorDTO.getDecryptableEntities(), s3ArtifactConfig.getEncryptedDataDetails());
    AwsInternalConfig awsConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
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
            new PivotalClientApiException(format(DOWNLOAD_FROM_S3_FAILED, artifactRequestDetails.getIdentifier())));
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
          new PivotalClientApiException(
              format(DOWNLOAD_FROM_S3_FAILED, artifactRequestDetails.getIdentifier()), sanitizedException));
    }
    artifactResponseBuilder.artifactType(
        AzureArtifactUtils.detectArtifactType(artifactRequestDetails.getFilePath(), logCallback));

    return artifactInputStream;
  }

  private void validateAwsS3Artifact(AwsS3TasArtifactRequestDetails artifactRequestDetails, LogCallback logCallback) {
    if (EmptyPredicate.isEmpty(artifactRequestDetails.getFilePath())) {
      logCallback.saveExecutionLog("artifact Path is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, artifactRequestDetails.getIdentifier()),
          new InvalidArgumentsException("not able to find artifact Path"));
    }
  }

  private void validateJenkinsArtifact(
      JenkinsTasArtifactRequestDetails artifactRequestDetails, LogCallback logCallback) {
    if (EmptyPredicate.isEmpty(artifactRequestDetails.getArtifactName())) {
      logCallback.saveExecutionLog("artifact Path is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, artifactRequestDetails.getIdentifier()),
          new InvalidArgumentsException("not able to find artifact Path"));
    }
  }

  private void validateAzureDevOpsArtifact(
      AzureDevOpsTasArtifactRequestDetails azureDevOpsTasArtifactRequestDetails, LogCallback logCallback) {
    if (EmptyPredicate.isEmpty(azureDevOpsTasArtifactRequestDetails.getArtifactName())) {
      logCallback.saveExecutionLog("artifact Path is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, azureDevOpsTasArtifactRequestDetails.getIdentifier()),
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

  private InputStream downloadFromArtifactory(TasPackageArtifactConfig artifactConfig,
      TasArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
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

    if (!(artifactConfig.getArtifactDetails() instanceof ArtifactoryTasArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              artifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format("Invalid artifact details, expected '%s'",
                  ArtifactoryTasArtifactRequestDetails.class.getSimpleName()))));
    }

    ArtifactoryConnectorDTO artifactoryConnector = (ArtifactoryConnectorDTO) artifactConfig.getConnectorConfig();
    decryptEntity(
        decryptionHelper, artifactoryConnector.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());
    ArtifactoryTasArtifactRequestDetails artifactDetails =
        (ArtifactoryTasArtifactRequestDetails) artifactConfig.getArtifactDetails();
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

  private InputStream downloadFromNexus(TasPackageArtifactConfig artifactConfig,
      TasArtifactDownloadResponseBuilder artifactResponseBuilder, LogCallback logCallback) {
    if (!(artifactConfig.getConnectorConfig() instanceof NexusConnectorDTO)) {
      throw NestedExceptionUtils.hintWithExplanationException("Configure nexus connector for nexus configuration",
          format("Unexpected connector type '%s' for nexus configuration",
              artifactConfig.getConnectorConfig().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("connectorConfig",
              format("Invalid connector type '%s', expected '%s'",
                  artifactConfig.getConnectorConfig().getClass().getSimpleName(),
                  NexusConnectorDTO.class.getSimpleName()))));
    }

    if (!(artifactConfig.getArtifactDetails() instanceof NexusTasArtifactRequestDetails)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact harness support team",
          format("Unexpected artifact configuration of type '%s'",
              artifactConfig.getArtifactDetails().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifactDetails",
              format(
                  "Invalid artifact details, expected '%s'", NexusTasArtifactRequestDetails.class.getSimpleName()))));
    }

    NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) artifactConfig.getConnectorConfig();
    decryptEntity(
        decryptionHelper, nexusConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());
    NexusTasArtifactRequestDetails requestDetails =
        (NexusTasArtifactRequestDetails) artifactConfig.getArtifactDetails();

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
          NEXUS_FAILED_DOWNLOAD_EXPLANATION + message, new PivotalClientApiException(message));
    }
  }

  protected void decryptEntity(DecryptionHelper decryptionHelper, List<DecryptableEntity> decryptableEntities,
      List<EncryptedDataDetail> encryptedDataDetails) {
    if (isNotEmpty(decryptableEntities)) {
      for (DecryptableEntity decryptableEntity : decryptableEntities) {
        decryptionHelper.decrypt(decryptableEntity, encryptedDataDetails);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(decryptableEntity, encryptedDataDetails);
      }
    }
  }

  private ArtifactType getArtifactType(NexusTasArtifactRequestDetails nexusRequestDetails, LogCallback logCallback) {
    String repositoryFormat = nexusRequestDetails.getRepositoryFormat();
    if (RepositoryFormat.nuget.name().equals(repositoryFormat)) {
      logCallback.saveExecutionLog(format(
          "Detected artifact type '%s' based on repository format '%s'", ArtifactType.NUGET.name(), repositoryFormat));
      return ArtifactType.NUGET;
    }

    return AzureArtifactUtils.detectArtifactType(nexusRequestDetails.getArtifactUrl(), logCallback);
  }

  public ApplicationDetail getNewlyCreatedApplication(CfRequestConfig cfRequestConfig,
      CfDeployCommandRequestNG cfDeployCommandRequestNG, CfDeploymentManager cfDeploymentManager)
      throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(cfDeployCommandRequestNG.getNewReleaseName());
    cfRequestConfig.setDesiredCount(cfDeployCommandRequestNG.getUpsizeCount());
    return cfDeploymentManager.getApplicationByName(cfRequestConfig);
  }

  public ApplicationDetail getApplicationDetails(
      CfRequestConfig cfRequestConfig, CfDeploymentManager cfDeploymentManager) throws PivotalClientApiException {
    return cfDeploymentManager.getApplicationByName(cfRequestConfig);
  }

  public void downsizePreviousReleases(CfDeployCommandRequestNG cfDeployCommandRequestNG,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, List<CfServiceData> cfServiceDataUpdated,
      Integer updateCount, List<CfInternalInstanceElement> oldAppInstances,
      CfAppAutoscalarRequestData appAutoscalarRequestData) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog("# Downsizing previous application version/s");

    TasApplicationInfo downsizeAppDetail = cfDeployCommandRequestNG.getDownsizeAppDetail();
    if (downsizeAppDetail == null) {
      executionLogCallback.saveExecutionLog("# No Application is available for downsize");
      return;
    }

    if (cfDeployCommandRequestNG.isStandardBlueGreen() && cfDeployCommandRequestNG.getDownSizeCount() == 0) {
      executionLogCallback.saveExecutionLog(
          color("# Skipping Downsizing of Old Instances as there will be a downtime if downsized to 0", White, Bold));
      return;
    }

    cfRequestConfig.setApplicationName(downsizeAppDetail.getApplicationName());
    ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
    executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
        applicationDetail.getName(), applicationDetail.getInstances(), updateCount));

    CfServiceData cfServiceData = CfServiceData.builder()
                                      .name(applicationDetail.getName())
                                      .id(applicationDetail.getId())
                                      .previousCount(applicationDetail.getInstances())
                                      .desiredCount(updateCount)
                                      .build();

    cfServiceDataUpdated.add(cfServiceData);

    appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
    appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
    boolean isAutoScalarEnabled =
        cfDeploymentManager.checkIfAppHasAutoscalarEnabled(appAutoscalarRequestData, executionLogCallback);

    // First disable App Auto scalar if attached with application
    if (isAutoScalarEnabled) {
      appAutoscalarRequestData.setExpectedEnabled(true);
      boolean autoscalarStateChanged =
          pcfCommandTaskBaseHelper.disableAutoscalarSafe(appAutoscalarRequestData, executionLogCallback);
      cfServiceData.setDisableAutoscalarPerformed(autoscalarStateChanged);
    }

    ApplicationDetail applicationDetailAfterResize =
        downSize(cfServiceData, executionLogCallback, cfRequestConfig, cfDeploymentManager);

    // Application that is downsized
    if (EmptyPredicate.isNotEmpty(applicationDetailAfterResize.getInstanceDetails())) {
      applicationDetailAfterResize.getInstanceDetails().forEach(instance
          -> oldAppInstances.add(CfInternalInstanceElement.builder()
                                     .applicationId(applicationDetailAfterResize.getId())
                                     .displayName(applicationDetailAfterResize.getName())
                                     .instanceIndex(instance.getIndex())
                                     .isUpsize(false)
                                     .build()));
    }
    unmapRoutesIfAppDownsizedToZero(cfDeployCommandRequestNG, cfRequestConfig, executionLogCallback);
  }

  ApplicationDetail downSize(CfServiceData cfServiceData, LogCallback executionLogCallback,
      CfRequestConfig cfRequestConfig, CfDeploymentManager pcfDeploymentManager) throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(cfServiceData.getName());
    cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());

    ApplicationDetail applicationDetail = pcfDeploymentManager.resizeApplication(cfRequestConfig);
    executionLogCallback.saveExecutionLog("# Downsizing successful");
    executionLogCallback.saveExecutionLog("\n# App details after downsize:");
    pcfCommandTaskBaseHelper.printApplicationDetail(applicationDetail, executionLogCallback);
    return applicationDetail;
  }

  void unmapRoutesIfAppDownsizedToZero(CfDeployCommandRequestNG cfCommandDeployRequest, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    if (cfCommandDeployRequest.isStandardBlueGreen() || cfCommandDeployRequest.getDownsizeAppDetail() == null
        || isBlank(cfCommandDeployRequest.getDownsizeAppDetail().getApplicationName())) {
      return;
    }

    cfRequestConfig.setApplicationName(cfCommandDeployRequest.getDownsizeAppDetail().getApplicationName());
    ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);

    if (applicationDetail.getInstances() == 0) {
      pcfCommandTaskBaseHelper.unmapExistingRouteMaps(applicationDetail, cfRequestConfig, executionLogCallback);
    }
  }

  public void upsizeNewApplication(LogCallback executionLogCallback, CfDeployCommandRequestNG cfCommandDeployRequest,
      List<CfServiceData> cfServiceDataUpdated, CfRequestConfig cfRequestConfig, ApplicationDetail details,
      List<CfInternalInstanceElement> newAppInstances, CfAppAutoscalarRequestData cfAppAutoscalarRequestData)
      throws PivotalClientApiException, IOException {
    executionLogCallback.saveExecutionLog(color("# Upsizing new application:", White, Bold));

    executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
        details.getName(), details.getInstances(), cfCommandDeployRequest.getUpsizeCount()));

    // Upscale new app
    cfRequestConfig.setApplicationName(cfCommandDeployRequest.getNewReleaseName());
    cfRequestConfig.setDesiredCount(cfCommandDeployRequest.getUpsizeCount());

    // perform upsize
    pcfCommandTaskBaseHelper.upsizeInstance(
        cfRequestConfig, cfDeploymentManager, executionLogCallback, cfServiceDataUpdated, newAppInstances);
    configureAutoscalarIfNeeded(cfCommandDeployRequest, details, cfAppAutoscalarRequestData, executionLogCallback);
  }

  public void createYamlFileLocally(String filePath, String autoscalarManifestYml) throws IOException {
    pcfCommandTaskBaseHelper.createYamlFileLocally(filePath, autoscalarManifestYml);
  }

  public void configureAutoscalarIfNeeded(CfDeployCommandRequestNG cfCommandDeployRequest,
      ApplicationDetail applicationDetail, CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback executionLogCallback) throws PivotalClientApiException, IOException {
    if (cfCommandDeployRequest.isUseAppAutoScalar() && cfCommandDeployRequest.getTasManifestsPackage() != null
        && isNotEmpty(cfCommandDeployRequest.getTasManifestsPackage().getAutoscalarManifestYml())
        && cfCommandDeployRequest.getMaxCount() <= cfCommandDeployRequest.getUpsizeCount()) {
      // This is autoscalar file inside workingDirectory
      String filePath =
          appAutoscalarRequestData.getConfigPathVar() + "/autoscalar_" + System.currentTimeMillis() + ".yml";
      createYamlFileLocally(filePath, cfCommandDeployRequest.getTasManifestsPackage().getAutoscalarManifestYml());

      // upload autoscalar config
      appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
      appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
      appAutoscalarRequestData.setTimeoutInMins(cfCommandDeployRequest.getTimeoutIntervalInMin());
      appAutoscalarRequestData.setAutoscalarFilePath(filePath);
      cfDeploymentManager.performConfigureAutoscalar(appAutoscalarRequestData, executionLogCallback);
    }
  }

  public String getCfCliPathOnDelegate(boolean useCfCLI, CfCliVersion cfCliVersion) {
    return pcfCommandTaskBaseHelper.getCfCliPathOnDelegate(useCfCLI, cfCliVersion);
  }

  public void upsizeListOfInstancesAndRestoreRoutes(LogCallback executionLogCallback,
      CfDeploymentManager cfDeploymentManager, TasApplicationInfo oldApplicationInfo, CfRequestConfig cfRequestConfig,
      CfRollbackCommandRequestNG cfRollbackCommandRequestNG, List<CfInternalInstanceElement> oldAppInstances,
      CfRollbackCommandResult cfRollbackCommandResult) throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(oldApplicationInfo.getApplicationName());
    cfRequestConfig.setDesiredCount(oldApplicationInfo.getRunningCount());
    executionLogCallback.saveExecutionLog(color("# Upsizing application:", White, Bold));
    executionLogCallback.saveExecutionLog(format("%s%s",
        CFLogCallbackFormatter.formatSameLineKeyValue(
            APPLICATION_NAME, encodeColor(oldApplicationInfo.getApplicationName())),
        CFLogCallbackFormatter.formatNewLineKeyValue(DESIRED_INSTANCE_COUNT, oldApplicationInfo.getRunningCount())));
    ApplicationDetail detailsAfterUpsize =
        cfDeploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);
    detailsAfterUpsize.getInstanceDetails().forEach(instanceDetail
        -> oldAppInstances.add(CfInternalInstanceElement.builder()
                                   .applicationId(detailsAfterUpsize.getId())
                                   .displayName(detailsAfterUpsize.getName())
                                   .instanceIndex(instanceDetail.getIndex())
                                   .build()));
    executionLogCallback.saveExecutionLog("\n# Application state details after upsize:  ");
    pcfCommandTaskBaseHelper.printApplicationDetail(detailsAfterUpsize, executionLogCallback);
    restoreRoutesForOldApplication(cfRollbackCommandRequestNG.getActiveApplicationDetails(), cfRequestConfig,
        executionLogCallback, cfRollbackCommandResult);
  }

  public void upsizeListOfInstances(LogCallback executionLogCallback, CfDeploymentManager cfDeploymentManager,
      List<CfServiceData> cfServiceDataUpdated, CfRequestConfig cfRequestConfig, List<CfServiceData> upsizeList,
      List<CfInternalInstanceElement> cfInstanceElements) throws PivotalClientApiException {
    pcfCommandTaskBaseHelper.upsizeListOfInstances(executionLogCallback, cfDeploymentManager, cfServiceDataUpdated,
        cfRequestConfig, upsizeList, cfInstanceElements);
  }

  public List<String> getAppNameBasedOnGuidForBlueGreenDeployment(
      CfRequestConfig cfRequestConfig, String cfAppNamePrefix, String appGuid) throws PivotalClientApiException {
    List<ApplicationSummary> previousReleases =
        cfDeploymentManager.getPreviousReleases(cfRequestConfig, cfAppNamePrefix);
    return previousReleases.stream()
        .filter(app -> app.getId().equalsIgnoreCase(appGuid))
        .map(ApplicationSummary::getName)
        .collect(toList());
  }

  public void downSizeListOfInstances(LogCallback executionLogCallback, List<CfServiceData> cfServiceDataUpdated,
      CfRequestConfig cfRequestConfig, List<CfServiceData> downSizeList, boolean isUseAppAutoScalar,
      CfAppAutoscalarRequestData autoscalarRequestData) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog("\n");
    for (CfServiceData cfServiceData : downSizeList) {
      executionLogCallback.saveExecutionLog(color("# Downsizing application:", White, Bold));
      executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
          cfServiceData.getName(), cfServiceData.getPreviousCount(), cfServiceData.getDesiredCount()));

      cfRequestConfig.setApplicationName(cfServiceData.getName());
      cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());

      if (isUseAppAutoScalar) {
        ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
        autoscalarRequestData.setApplicationName(applicationDetail.getName());
        autoscalarRequestData.setApplicationGuid(applicationDetail.getId());
        autoscalarRequestData.setExpectedEnabled(true);
        pcfCommandTaskBaseHelper.disableAutoscalarSafe(autoscalarRequestData, executionLogCallback);
      }

      downSize(cfServiceData, executionLogCallback, cfRequestConfig, cfDeploymentManager);

      cfServiceDataUpdated.add(cfServiceData);
    }
  }

  public void downSizeListOfInstancesAndUnmapRoutes(LogCallback executionLogCallback, CfRequestConfig cfRequestConfig,
      TasApplicationInfo newAppInfo, CfRollbackCommandRequestNG cfRollbackCommandRequestNG,
      CfAppAutoscalarRequestData autoscalarRequestData) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog("\n");
    executionLogCallback.saveExecutionLog(color("# Downsizing application:", White, Bold));
    executionLogCallback.saveExecutionLog(format("Downsizing the %s app to zero", newAppInfo.getApplicationName()));

    cfRequestConfig.setApplicationName(newAppInfo.getApplicationName());
    cfRequestConfig.setDesiredCount(0);

    if (cfRollbackCommandRequestNG.isUseAppAutoScalar()) {
      ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
      autoscalarRequestData.setApplicationName(applicationDetail.getName());
      autoscalarRequestData.setApplicationGuid(applicationDetail.getId());
      autoscalarRequestData.setExpectedEnabled(true);
      pcfCommandTaskBaseHelper.disableAutoscalarSafe(autoscalarRequestData, executionLogCallback);
    }
    ApplicationDetail applicationDetail = cfDeploymentManager.resizeApplication(cfRequestConfig);

    executionLogCallback.saveExecutionLog("# Downsizing successful");
    executionLogCallback.saveExecutionLog("\n# App details after downsize:");
    pcfCommandTaskBaseHelper.printApplicationDetail(applicationDetail, executionLogCallback);
    unmapRoutesFromNewAppAfterDownsize(
        executionLogCallback, cfRollbackCommandRequestNG.getNewApplicationDetails(), cfRequestConfig);
  }

  public void mapRouteMaps(String applicationName, List<String> urls, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    pcfCommandTaskBaseHelper.mapRouteMaps(applicationName, urls, cfRequestConfig, executionLogCallback);
  }

  public void unmapRouteMaps(String applicationName, List<String> urls, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    pcfCommandTaskBaseHelper.unmapRouteMaps(applicationName, urls, cfRequestConfig, executionLogCallback);
  }

  public boolean disableAutoscalar(CfAppAutoscalarRequestData pcfAppAutoscalarRequestData,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    return cfDeploymentManager.changeAutoscalarState(pcfAppAutoscalarRequestData, executionLogCallback, false);
  }

  public CfInBuiltVariablesUpdateValues performAppRenaming(AppRenamingOperator.NamingTransition transition,
      CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    AppRenamingOperator renamingOperator = AppRenamingOperator.of(transition);
    return renamingOperator.renameApp(
        cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback, cfDeploymentManager, pcfCommandTaskBaseHelper);
  }

  public List<CfServiceData> getUpsizeListForRollback(
      List<CfServiceData> instanceData, TasApplicationInfo newApplicationDetails) {
    return instanceData.stream()
        .filter(cfServiceData -> {
          if (cfServiceData.getDesiredCount() > cfServiceData.getPreviousCount()) {
            return true;
          } else if (cfServiceData.getDesiredCount() == cfServiceData.getPreviousCount()) {
            String newApplicationName = null;
            if (!isNull(newApplicationDetails)) {
              newApplicationName = newApplicationDetails.getApplicationName();
            }
            return cfServiceData.getDesiredCount() == 0 && (!cfServiceData.getName().equals(newApplicationName));
          }
          return false;
        })
        .collect(toList());
  }

  public void restoreRoutesForOldApplication(TasApplicationInfo tasApplicationInfo, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback, CfRollbackCommandResult cfRollbackCommandResult)
      throws PivotalClientApiException {
    if (isNull(tasApplicationInfo)) {
      return;
    }

    cfRequestConfig.setApplicationName(tasApplicationInfo.getApplicationName());
    ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
    cfRollbackCommandResult.setActiveAppAttachedRoutes(tasApplicationInfo.getAttachedRoutes());
    if (EmptyPredicate.isEmpty(tasApplicationInfo.getAttachedRoutes())) {
      return;
    }

    if (EmptyPredicate.isEmpty(applicationDetail.getUrls())
        || !tasApplicationInfo.getAttachedRoutes().containsAll(applicationDetail.getUrls())) {
      mapRouteMaps(tasApplicationInfo.getApplicationName(), tasApplicationInfo.getAttachedRoutes(), cfRequestConfig,
          executionLogCallback);
    }
  }

  public void unmapRoutesFromNewAppAfterDownsize(LogCallback executionLogCallback,
      TasApplicationInfo newApplicationDetails, CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    if (newApplicationDetails == null || isBlank(newApplicationDetails.getApplicationName())) {
      return;
    }

    cfRequestConfig.setApplicationName(newApplicationDetails.getApplicationName());
    ApplicationDetail appDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);

    if (appDetail.getInstances() == 0) {
      pcfCommandTaskBaseHelper.unmapExistingRouteMaps(appDetail, cfRequestConfig, executionLogCallback);
    }
  }

  public void enableAutoscalerIfNeeded(TasApplicationInfo oldAppInfo, CfAppAutoscalarRequestData autoscalarRequestData,
      LogCallback logCallback) throws PivotalClientApiException {
    autoscalarRequestData.setApplicationName(oldAppInfo.getApplicationName());
    autoscalarRequestData.setApplicationGuid(oldAppInfo.getApplicationGuid());
    autoscalarRequestData.setExpectedEnabled(false);
    cfDeploymentManager.changeAutoscalarState(autoscalarRequestData, logCallback, true);
  }

  public void enableAutoscalerIfNeeded(ApplicationDetail applicationDetail,
      CfAppAutoscalarRequestData autoscalarRequestData, LogCallback logCallback) throws PivotalClientApiException {
    autoscalarRequestData.setApplicationName(applicationDetail.getName());
    autoscalarRequestData.setApplicationGuid(applicationDetail.getId());
    autoscalarRequestData.setExpectedEnabled(false);
    cfDeploymentManager.changeAutoscalarState(autoscalarRequestData, logCallback, true);
  }

  public void deleteNewApp(CfRequestConfig cfRequestConfig, String cfAppNamePrefix, TasApplicationInfo newApp,
      LogCallback logCallback) throws PivotalClientApiException {
    String newAppGuid = newApp.getApplicationGuid();
    String newAppName = newApp.getApplicationName();
    List<String> newApps = pcfCommandTaskBaseHelper.getAppNameBasedOnGuid(cfRequestConfig, cfAppNamePrefix, newAppGuid);

    if (newApps.isEmpty()) {
      logCallback.saveExecutionLog(
          String.format("No new app found to delete with id - [%s] and name - [%s]", newAppGuid, newAppName));
    } else if (newApps.size() == 1) {
      String newAppToDelete = newApps.get(0);
      cfRequestConfig.setApplicationName(newAppToDelete);
      logCallback.saveExecutionLog("Deleting application " + encodeColor(newAppToDelete));
      cfDeploymentManager.deleteApplication(cfRequestConfig);
    } else {
      String newAppToDelete = newApps.get(0);
      String message = String.format(
          "Found [%d] applications with with id - [%s] and name - [%s]. Skipping new app deletion. Kindly delete the invalid app manually",
          newApps.size(), newAppGuid, newAppToDelete);
      logCallback.saveExecutionLog(message, WARN);
    }
  }
}
