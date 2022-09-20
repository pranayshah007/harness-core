/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.task.shell.ArtifactoryUtils.getArtifactConfigRequest;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.winrm.WinRmHelperUtils.buildErrorDetailsFromWinRmClientException;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.utils.ExecutionLogWriter;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDP)
@Slf4j
public class FileBasedWinRmExecutorNG extends FileBasedAbstractWinRmExecutor {
  private static final String ARTIFACT = "artifact";

  private final SecretDecryptionService secretDecryptionService;
  private final ArtifactoryRequestMapper artifactoryRequestMapper;

  public FileBasedWinRmExecutorNG(LogCallback logCallback, boolean shouldSaveExecutionLogs, WinRmSessionConfig config,
      boolean disableCommandEncoding, SecretDecryptionService secretDecryptionService,
      ArtifactoryRequestMapper artifactoryRequestMapper) {
    super(logCallback, shouldSaveExecutionLogs, config, disableCommandEncoding);
    this.secretDecryptionService = secretDecryptionService;
    this.artifactoryRequestMapper = artifactoryRequestMapper;
  }

  @Override
  public byte[] getConfigFileBytes(ConfigFileMetaData configFileMetaData) {
    // no need for the implementation for NG
    throw new NotImplementedException("Not implemented");
  }

  public CommandExecutionStatus copyArtifacts(WinrmTaskParameters taskParameters, CopyCommandUnit copyCommandUnit) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = taskParameters.getArtifactDelegateConfig();
    if (artifactDelegateConfig == null) {
      throw new InvalidRequestException("Artifact delegate config not found.");
    }
    if (artifactDelegateConfig instanceof ArtifactoryArtifactDelegateConfig) {
      return handleArtifactoryArtifact((ArtifactoryArtifactDelegateConfig) artifactDelegateConfig, copyCommandUnit);
    } else if (artifactDelegateConfig instanceof JenkinsArtifactDelegateConfig) {
      return handleJenkinsArtifact((JenkinsArtifactDelegateConfig) artifactDelegateConfig, copyCommandUnit);
    } else {
      log.warn("Wrong artifact delegate config submitted");
      throw new InvalidRequestException("Expecting artifactory or jenkins delegate config");
    }
  }

  private CommandExecutionStatus handleArtifactoryArtifact(
      ArtifactoryArtifactDelegateConfig artifactoryArtifactDelegateConfig, CopyCommandUnit copyCommandUnit) {
    ArtifactoryConfigRequest artifactoryConfigRequest = getArtifactConfigRequest(
        artifactoryArtifactDelegateConfig, logCallback, secretDecryptionService, artifactoryRequestMapper);

    CommandExecutionStatus commandExecutionStatus = FAILURE;
    String artifactPath = Paths
                              .get(artifactoryArtifactDelegateConfig.getRepositoryName(),
                                  artifactoryArtifactDelegateConfig.getArtifactPath())
                              .toString();
    saveExecutionLog(format("Begin execution of command: %s", copyCommandUnit.getName()), INFO);
    saveExecutionLog("Copying artifact from ARTIFACTORY to " + copyCommandUnit.getDestinationPath() + "\\"
            + getArtifactFileName(artifactPath),
        INFO);

    saveExecutionLog(format("Initializing WinRM connection to %s ...", config.getHostname()), INFO);
    try (WinRmSession session = new WinRmSession(config, this.logCallback);
         ExecutionLogWriter outputWriter = getExecutionLogWriter(INFO);
         ExecutionLogWriter errorWriter = getExecutionLogWriter(ERROR)) {
      saveExecutionLog(format("Connected to %s", config.getHostname()), INFO);
      saveExecutionLog(
          format("Executing copy artifact command...\nArtifact filename: %s", getArtifactFileName(artifactPath)), INFO);

      String command =
          getDownloadArtifactCommand(artifactoryConfigRequest, copyCommandUnit.getDestinationPath(), artifactPath);
      commandExecutionStatus = executeRemoteCommand(session, outputWriter, errorWriter, command, true);
      if (FAILURE == commandExecutionStatus) {
        saveExecutionLog("Failed to copy artifact.", ERROR, commandExecutionStatus);
        return commandExecutionStatus;
      }
      saveExecutionLog(
          "Command execution finished with status " + commandExecutionStatus, INFO, commandExecutionStatus);

    } catch (Exception e) {
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }

    log.info("Copy Config command execution returned status: {}", commandExecutionStatus);
    return commandExecutionStatus;
  }

  private CommandExecutionStatus handleJenkinsArtifact(
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, CopyCommandUnit copyCommandUnit) {
    CommandExecutionStatus commandExecutionStatus = FAILURE;

    String artifactPathOnTarget = Paths.get(jenkinsArtifactDelegateConfig.getArtifactPath()).getFileName().toString();
    saveExecutionLog(format("Begin execution of command: %s", copyCommandUnit.getName()), INFO);
    saveExecutionLog("Downloading artifact from JENKINS to " + copyCommandUnit.getDestinationPath() + "\\"
            + getArtifactFileName(artifactPathOnTarget),
        INFO);
    try (WinRmSession session = new WinRmSession(config, this.logCallback);
         ExecutionLogWriter outputWriter = getExecutionLogWriter(INFO);
         ExecutionLogWriter errorWriter = getExecutionLogWriter(ERROR)) {
      saveExecutionLog(format("Connected to %s", config.getHostname()), INFO);
      saveExecutionLog(format("Executing copy artifact command...\nArtifact filename: %s",
                           getArtifactFileName(artifactPathOnTarget)),
          INFO);
      String artifactPath =
          Paths
              .get(jenkinsArtifactDelegateConfig.getJobName(), jenkinsArtifactDelegateConfig.getBuild(), ARTIFACT,
                  jenkinsArtifactDelegateConfig.getArtifactPath())
              .toString();
      saveExecutionLog(
          format("Begin file transfer from %s", getJenkinsUrl(jenkinsArtifactDelegateConfig, artifactPath)), INFO);
      String command = getDownloadJenkinsArtifactCommand(
          jenkinsArtifactDelegateConfig, copyCommandUnit.getDestinationPath(), artifactPath, artifactPathOnTarget);
      commandExecutionStatus = executeRemoteCommand(session, outputWriter, errorWriter, command, true);
      if (FAILURE == commandExecutionStatus) {
        saveExecutionLog(format("Failed to copy Jenkins artifact from %s",
                             getJenkinsUrl(jenkinsArtifactDelegateConfig, artifactPath)),
            ERROR, commandExecutionStatus);
        return commandExecutionStatus;
      }
      saveExecutionLog(
          format("File successfully transferred to %s\\%s", copyCommandUnit.getDestinationPath(), artifactPathOnTarget),
          INFO);
    } catch (Exception e) {
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }

    saveExecutionLog("Command execution finished with status " + commandExecutionStatus, INFO, commandExecutionStatus);

    log.info("Copy Config command execution returned status: {}", commandExecutionStatus);
    return commandExecutionStatus;
  }

  public CommandExecutionStatus copyConfigFiles(ConfigFileParameters configFileParameters) {
    if (isBlank(configFileParameters.getFileName())) {
      saveExecutionLog("There is no config file to copy. " + configFileParameters, INFO);
      return CommandExecutionStatus.SUCCESS;
    }

    CommandExecutionStatus commandExecutionStatus = FAILURE;

    try (WinRmSession session = new WinRmSession(config, this.logCallback);
         ExecutionLogWriter outputWriter = getExecutionLogWriter(INFO);
         ExecutionLogWriter errorWriter = getExecutionLogWriter(ERROR)) {
      saveExecutionLog(format("Connected to %s", config.getHostname()), INFO);
      saveExecutionLog(
          format("Executing copy config files command...\nConfig filename: %s", configFileParameters.getFileName()),
          INFO);

      commandExecutionStatus = splitFileAndTransfer(configFileParameters, session, outputWriter, errorWriter);
      saveExecutionLog("Command completed successfully", INFO);

    } catch (Exception e) {
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }

    log.info("Copy Config command execution returned status: {}", commandExecutionStatus);
    return commandExecutionStatus;
  }

  private CommandExecutionStatus splitFileAndTransfer(ConfigFileParameters configFileParameters, WinRmSession session,
      ExecutionLogWriter outputWriter, ExecutionLogWriter errorWriter) throws IOException {
    final List<List<Byte>> partitions =
        Lists.partition(Bytes.asList(configFileParameters.getFileContent().getBytes()), BATCH_SIZE_BYTES);
    clearTargetFile(session, outputWriter, errorWriter, configFileParameters.getDestinationPath(),
        configFileParameters.getFileName());
    logFileSizeAndOtherMetadata(
        configFileParameters.getFileSize(), partitions.size(), configFileParameters.getFileName());

    int chunkNumber = 1;
    for (List<Byte> partition : partitions) {
      final byte[] bytesToCopy = Bytes.toArray(partition);
      String command = getCopyConfigCommand(
          bytesToCopy, configFileParameters.getDestinationPath(), configFileParameters.getFileName());
      CommandExecutionStatus commandExecutionStatus =
          executeRemoteCommand(session, outputWriter, errorWriter, command, true);
      if (FAILURE == commandExecutionStatus) {
        saveExecutionLog(format("Failed to copy chunk #%d. Discontinuing", chunkNumber), ERROR, RUNNING);
        return commandExecutionStatus;
      }
      saveExecutionLog(format("Transferred %s data for config file...\n",
                           calcPercentage(chunkNumber * BATCH_SIZE_BYTES, configFileParameters.getFileSize())),
          INFO, RUNNING);
      chunkNumber++;
    }
    return SUCCESS;
  }

  private String getDownloadArtifactCommand(
      ArtifactoryConfigRequest artifactoryConfigRequest, String destinationPath, String artifactPath) {
    String artifactFileName = getArtifactFileName(artifactPath);
    if (artifactoryConfigRequest.isHasCredentials()) {
      return "$Headers = @{\n"
          + "    Authorization = \"" + getAuthHeader(artifactoryConfigRequest) + "\"\n"
          + "}\n [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12"
          + "\n $ProgressPreference = 'SilentlyContinue'"
          + "\n Invoke-WebRequest -Uri \"" + getArtifactoryUrl(artifactoryConfigRequest, artifactPath)
          + "\" -Headers $Headers -OutFile \"" + destinationPath + "\\" + artifactFileName + "\"";
    } else {
      return "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n "
          + "$ProgressPreference = 'SilentlyContinue'\n"
          + "Invoke-WebRequest -Uri \"" + getArtifactoryUrl(artifactoryConfigRequest, artifactPath) + "\" -OutFile \""
          + destinationPath + "\\" + artifactFileName + "\"";
    }
  }

  private String getArtifactFileName(String artifactPath) {
    String artifactFileName = artifactPath;
    int lastIndexOfSlash = artifactFileName.lastIndexOf('/');
    if (lastIndexOfSlash > 0) {
      artifactFileName = artifactFileName.substring(lastIndexOfSlash + 1);
      log.info("Got filename: " + artifactFileName);
    }
    return artifactFileName;
  }

  private String getDownloadJenkinsArtifactCommand(JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig,
      String destinationPath, String artifactPath, String artifactPathOnTarget) {
    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    if (jenkinsConnectorDto.getAuth() != null) {
      String createFolderIfDoesntExist = "$targetPathForArtifact = \"" + destinationPath + "\\"
          + jenkinsArtifactDelegateConfig.getJobName() + "\"\n"
          + "If(!(test-path -PathType container $targetPathForArtifact))\n"
          + "{\n"
          + "      New-Item -ItemType Directory -Path $targetPathForArtifact\n"
          + "}\n";
      return createFolderIfDoesntExist + "$Headers = @{\n"
          + "    Authorization = \"" + getJenkinsAuthHeader(jenkinsArtifactDelegateConfig) + "\"\n"
          + "}\n"
          + "$AllProtocols = [Net.SecurityProtocolType]'Ssl3,Tls,Tls11,Tls12'\n"
          + "[Net.ServicePointManager]::SecurityProtocol = $AllProtocols"
          + "\n $ProgressPreference = 'SilentlyContinue'"
          + "\n Invoke-WebRequest -Uri \"" + getJenkinsUrl(jenkinsArtifactDelegateConfig, artifactPath)
          + "\" -Headers $Headers -OutFile \"" + destinationPath + "\\" + getArtifactFileName(artifactPathOnTarget)
          + "\"";
    } else {
      return "$AllProtocols = [Net.SecurityProtocolType]'Ssl3,Tls,Tls11,Tls12'\n"
          + "[Net.ServicePointManager]::SecurityProtocol = $AllProtocols\n"
          + "$ProgressPreference = 'SilentlyContinue'\n"
          + "Invoke-WebRequest -Uri \"" + getJenkinsUrl(jenkinsArtifactDelegateConfig, artifactPath) + "\" -OutFile \""
          + destinationPath + "\\" + getArtifactFileName(artifactPathOnTarget) + "\"";
    }
  }

  private String getAuthHeader(ArtifactoryConfigRequest artifactoryConfigRequest) {
    String authHeader = null;
    if (artifactoryConfigRequest.isHasCredentials()) {
      String pair = artifactoryConfigRequest.getUsername() + ":" + new String(artifactoryConfigRequest.getPassword());
      authHeader = "Basic " + encodeBase64(pair);
    }
    return authHeader;
  }

  private String getJenkinsAuthHeader(JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig) {
    String authHeader = null;
    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    JenkinsAuthType authType = jenkinsConnectorDto.getAuth().getAuthType();
    if (JenkinsAuthType.USER_PASSWORD.equals(authType)) {
      JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO =
          (JenkinsUserNamePasswordDTO) jenkinsConnectorDto.getAuth().getCredentials();
      String pair = jenkinsUserNamePasswordDTO.getUsername() + ":"
          + String.copyValueOf((jenkinsUserNamePasswordDTO.isDecrypted()
                  ? jenkinsUserNamePasswordDTO
                  : decrypt(jenkinsUserNamePasswordDTO, jenkinsArtifactDelegateConfig))
                                   .getPasswordRef()
                                   .getDecryptedValue());
      authHeader = "Basic " + encodeBase64(pair);
    } else if (JenkinsAuthType.BEARER_TOKEN.equals(authType)) {
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO =
          (JenkinsBearerTokenDTO) jenkinsConnectorDto.getAuth().getCredentials();
      if (!jenkinsBearerTokenDTO.isDecrypted()) {
        jenkinsBearerTokenDTO = decrypt(jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig);
      }
      authHeader = "Bearer "
          + String.copyValueOf(
              (jenkinsBearerTokenDTO.isDecrypted() ? jenkinsBearerTokenDTO
                                                   : decrypt(jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig))
                  .getTokenRef()
                  .getDecryptedValue());
    }
    return authHeader;
  }

  private String getArtifactoryUrl(ArtifactoryConfigRequest artifactoryConfigRequest, String artifactPath) {
    String url = artifactoryConfigRequest.getArtifactoryUrl().trim();
    if (!url.endsWith("/")) {
      url += "/";
    }
    return url + artifactPath;
  }

  private String getJenkinsUrl(JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, String artifactPath) {
    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    String url = jenkinsConnectorDto.getJenkinsUrl().trim();
    if (!url.endsWith("/")) {
      url += "/";
    }
    return url + "job"
        + "/" + artifactPath;
  }

  private JenkinsUserNamePasswordDTO decrypt(JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO,
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig) {
    return (JenkinsUserNamePasswordDTO) secretDecryptionService.decrypt(
        jenkinsUserNamePasswordDTO, jenkinsArtifactDelegateConfig.getEncryptedDataDetails());
  }

  private JenkinsBearerTokenDTO decrypt(
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO, JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig) {
    return (JenkinsBearerTokenDTO) secretDecryptionService.decrypt(
        jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig.getEncryptedDataDetails());
  }
}