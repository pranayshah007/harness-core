/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_ARTIFACTORY_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_ARTIFACTORY_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_ARTIFACTORY_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_GIT_FILES_DOWNLOAD_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_GIT_FILES_DOWNLOAD_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_GIT_FILES_DOWNLOAD_HINT;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FileCreationException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serverless.model.AwsLambdaFunctionDetails;
import io.harness.serverless.model.ServerlessDelegateTaskParams;
import io.harness.shell.SshSessionConfig;

import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class ServerlessTaskHelperBase {
  @Inject private ServerlessGitFetchTaskHelper serverlessGitFetchTaskHelper;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private NGGitService ngGitService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private ArtifactoryNgService artifactoryNgService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;

  private static final String ARTIFACTORY_ARTIFACT_PATH = "artifactPath";
  private static final String ARTIFACTORY_ARTIFACT_NAME = "artifactName";
  private static final String ARTIFACT_FILE_NAME = "artifactFile";
  private static final String ARTIFACT_DIR_NAME = "harnessArtifact";

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }
  public void createHomeDirectory(String directoryPath) throws IOException {
    createDirectoryIfDoesNotExist(directoryPath);
    waitForDirectoryToBeAccessibleOutOfProcess(directoryPath, 10);
  }

  public void fetchManifestFilesAndWriteToDirectory(ServerlessAwsLambdaManifestConfig serverlessManifestConfig,
      String accountId, LogCallback executionLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams)
      throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = serverlessManifestConfig.getGitStoreDelegateConfig();
    printFilesInExecutionLogs(gitStoreDelegateConfig, executionLogCallback);
    downloadFilesFromGit(
        gitStoreDelegateConfig, executionLogCallback, accountId, serverlessDelegateTaskParams.getWorkingDirectory());
    executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
    executionLogCallback.saveExecutionLog(
        getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory()));
  }

  private void downloadFilesFromGit(GitStoreDelegateConfig gitStoreDelegateConfig, LogCallback executionLogCallback,
      String accountId, String workingDirectory) {
    try {
      if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
        executionLogCallback.saveExecutionLog("Using optimized file fetch");
        serverlessGitFetchTaskHelper.decryptGitStoreConfig(gitStoreDelegateConfig);
        scmFetchFilesHelper.downloadFilesUsingScm(workingDirectory, gitStoreDelegateConfig, executionLogCallback);
      } else {
        GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
        gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
            gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
            gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
        ngGitService.downloadFiles(gitStoreDelegateConfig, workingDirectory, accountId, sshSessionConfig, gitConfigDTO);
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in fetching files from git", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download manifest files from git. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(
          format(SERVERLESS_GIT_FILES_DOWNLOAD_HINT, gitStoreDelegateConfig.getManifestId()),
          format(SERVERLESS_GIT_FILES_DOWNLOAD_EXPLANATION, gitStoreDelegateConfig.getConnectorName(),
              gitStoreDelegateConfig.getManifestId()),
          new ServerlessCommandExecutionException(SERVERLESS_GIT_FILES_DOWNLOAD_FAILED, sanitizedException));
    }
  }

  private void printFilesInExecutionLogs(
      GitStoreDelegateConfig gitStoreDelegateConfig, LogCallback executionLogCallback) {
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
    executionLogCallback.saveExecutionLog(
        color(format("Fetching %s files with identifier: %s", gitStoreDelegateConfig.getManifestType(),
                  gitStoreDelegateConfig.getManifestId()),
            White, Bold));
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitConfigDTO.getUrl());

    if (FetchType.BRANCH == gitStoreDelegateConfig.getFetchType()) {
      executionLogCallback.saveExecutionLog("Branch: " + gitStoreDelegateConfig.getBranch());
    } else {
      executionLogCallback.saveExecutionLog("CommitId: " + gitStoreDelegateConfig.getCommitId());
    }

    StringBuilder sb = new StringBuilder(1024);
    sb.append("Fetching files within this path: ");
    gitStoreDelegateConfig.getPaths().forEach(
        filePath -> sb.append(color(format("- %s", filePath), Gray)).append(System.lineSeparator()));
    executionLogCallback.saveExecutionLog(sb.toString());
  }

  public String getManifestFileNamesInLogFormat(String manifestFilesDirectory) throws IOException {
    Path basePath = Paths.get(manifestFilesDirectory);
    try (Stream<Path> paths = Files.walk(basePath)) {
      return generateTruncatedFileListForLogging(basePath, paths);
    }
  }

  public String generateTruncatedFileListForLogging(Path basePath, Stream<Path> paths) {
    StringBuilder sb = new StringBuilder(1024);
    AtomicInteger filesTraversed = new AtomicInteger(0);
    paths.filter(Files::isRegularFile).forEach(each -> {
      if (filesTraversed.getAndIncrement() <= 100) {
        sb.append(color(format("- %s", getRelativePath(each.toString(), basePath.toString())), Gray))
            .append(System.lineSeparator());
      }
    });
    if (filesTraversed.get() > 100) {
      sb.append(color(format("- ..%d more", filesTraversed.get() - 100), Gray)).append(System.lineSeparator());
    }

    return sb.toString();
  }

  public static String getRelativePath(String filePath, String prefixPath) {
    Path fileAbsolutePath = Paths.get(filePath).toAbsolutePath();
    Path prefixAbsolutePath = Paths.get(prefixPath).toAbsolutePath();
    return prefixAbsolutePath.relativize(fileAbsolutePath).toString();
  }

  public void replaceManifestWithRenderedContent(ServerlessDelegateTaskParams serverlessDelegateTaskParams,
      ServerlessAwsLambdaManifestConfig serverlessManifestConfig, String manifestOverrideContent,
      ServerlessAwsLambdaManifestSchema serverlessManifestSchema) throws IOException {
    String manifestFilePath =
        Paths.get(serverlessDelegateTaskParams.getWorkingDirectory(), serverlessManifestConfig.getManifestPath())
            .toString();
    manifestOverrideContent = removePluginVersion(manifestOverrideContent, serverlessManifestSchema);
    updateManifestFileContent(manifestFilePath, manifestOverrideContent);
  }

  private String removePluginVersion(
      String manifestContent, ServerlessAwsLambdaManifestSchema serverlessManifestSchema) {
    if (EmptyPredicate.isEmpty(serverlessManifestSchema.getPlugins())) {
      return manifestContent;
    }
    for (String plugin : serverlessManifestSchema.getPlugins()) {
      int index = plugin.indexOf('@');
      if (index != -1) {
        manifestContent = manifestContent.replace(plugin, plugin.substring(0, index));
      }
    }
    return manifestContent;
  }

  private void updateManifestFileContent(String manifestFilePath, String manifestContent) throws IOException {
    FileIo.deleteFileIfExists(manifestFilePath);
    FileIo.writeUtf8StringToFile(manifestFilePath, manifestContent);
  }

  public void fetchArtifact(ServerlessArtifactConfig serverlessArtifactConfig, LogCallback logCallback,
      String workingDirectory) throws IOException {
    if (serverlessArtifactConfig instanceof ServerlessArtifactoryArtifactConfig) {
      ServerlessArtifactoryArtifactConfig serverlessArtifactoryArtifactConfig =
          (ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig;
      String artifactoryDirectory = Paths.get(workingDirectory, ARTIFACT_DIR_NAME).toString();
      createDirectoryIfDoesNotExist(artifactoryDirectory);
      waitForDirectoryToBeAccessibleOutOfProcess(artifactoryDirectory, 10);
      fetchArtifactoryArtifact(serverlessArtifactoryArtifactConfig, logCallback, artifactoryDirectory);
    } else if (serverlessArtifactConfig instanceof ServerlessEcrArtifactConfig) {
      logCallback.saveExecutionLog(color("Skipping downloading artifact step as it is not needed..", White, Bold));
    }
  }

  public void fetchArtifactoryArtifact(ServerlessArtifactoryArtifactConfig artifactoryArtifactConfig,
      LogCallback executionLogCallback, String artifactoryDirectory) throws IOException {
    if (EmptyPredicate.isEmpty(artifactoryArtifactConfig.getArtifactPath())) {
      executionLogCallback.saveExecutionLog(
          "artifactPath or artifactPathFilter is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, artifactoryArtifactConfig.getIdentifier()),
          new ServerlessCommandExecutionException(BLANK_ARTIFACT_PATH));
    }
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        (ArtifactoryConnectorDTO) artifactoryArtifactConfig.getConnectorDTO().getConnectorConfig();
    secretDecryptionService.decrypt(
        artifactoryConnectorDTO.getAuth().getCredentials(), artifactoryArtifactConfig.getEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        artifactoryConnectorDTO, artifactoryArtifactConfig.getEncryptedDataDetails());
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);
    Map<String, String> artifactMetadata = new HashMap<>();
    String artifactPath =
        Paths.get(artifactoryArtifactConfig.getRepositoryName(), artifactoryArtifactConfig.getArtifactPath())
            .toString();
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_PATH, artifactPath);
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_NAME, artifactPath);
    String artifactFilePath = Paths.get(artifactoryDirectory, ARTIFACT_FILE_NAME).toAbsolutePath().toString();
    File artifactFile = new File(artifactFilePath);
    if (!artifactFile.createNewFile()) {
      log.error("Failed to create new file");
      executionLogCallback.saveExecutionLog(
          "Failed to create a file for artifactory", ERROR, CommandExecutionStatus.FAILURE);
      throw new FileCreationException("Failed to create file " + artifactFile.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }
    executionLogCallback.saveExecutionLog(
        color(format("Downloading %s artifact with identifier: %s",
                  artifactoryArtifactConfig.getServerlessArtifactType(), artifactoryArtifactConfig.getIdentifier()),
            White, Bold));
    executionLogCallback.saveExecutionLog("Artifactory Artifact Path: " + artifactPath);
    try (InputStream artifactInputStream = artifactoryNgService.downloadArtifacts(artifactoryConfigRequest,
             artifactoryArtifactConfig.getRepositoryName(), artifactMetadata, ARTIFACTORY_ARTIFACT_PATH,
             ARTIFACTORY_ARTIFACT_NAME);
         FileOutputStream outputStream = new FileOutputStream(artifactFile)) {
      if (artifactInputStream == null) {
        log.error("Failure in downloading artifact from artifactory");
        executionLogCallback.saveExecutionLog(
            "Failed to download artifact from artifactory.ø", ERROR, CommandExecutionStatus.FAILURE);
        throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_ARTIFACTORY_HINT,
            String.format(
                DOWNLOAD_FROM_ARTIFACTORY_EXPLANATION, artifactPath, artifactoryConfigRequest.getArtifactoryUrl()),
            new ServerlessCommandExecutionException(
                format(DOWNLOAD_FROM_ARTIFACTORY_FAILED, artifactoryArtifactConfig.getIdentifier())));
      }
      IOUtils.copy(artifactInputStream, outputStream);
      executionLogCallback.saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from artifactory", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download artifact from artifactory. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_ARTIFACTORY_HINT,
          String.format(
              DOWNLOAD_FROM_ARTIFACTORY_EXPLANATION, artifactPath, artifactoryConfigRequest.getArtifactoryUrl()),
          new ServerlessCommandExecutionException(
              format(DOWNLOAD_FROM_ARTIFACTORY_FAILED, artifactoryArtifactConfig.getIdentifier()), sanitizedException));
    }
  }

  public List<ServerInstanceInfo> getServerlessAwsLambdaServerInstanceInfos(
      ServerlessAwsLambdaDeploymentReleaseData deploymentReleaseData) {
    List<String> functions = deploymentReleaseData.getFunctions();
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) deploymentReleaseData.getServerlessInfraConfig();
    serverlessInfraConfigHelper.decryptServerlessInfraConfig(serverlessAwsLambdaInfraConfig);
    AwsInternalConfig awsInternalConfig =
        awsNgConfigMapper.createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO());
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    if (!CollectionUtils.isEmpty(functions)) {
      for (String function : functions) {
        AwsLambdaFunctionDetails awsLambdaFunctionDetails =
            awsLambdaHelperServiceDelegateNG.getAwsLambdaFunctionDetails(
                awsInternalConfig, function, deploymentReleaseData.getRegion());
        if (awsLambdaFunctionDetails != null) {
          ServerlessAwsLambdaServerInstanceInfo serverlessAwsLambdaServerInstanceInfo =
              ServerlessAwsLambdaServerInstanceInfo.getServerlessAwsLambdaServerInstanceInfo(
                  deploymentReleaseData.getServiceName(), serverlessAwsLambdaInfraConfig.getStage(),
                  deploymentReleaseData.getRegion(),
                  awsLambdaHelperServiceDelegateNG.getAwsLambdaFunctionDetails(
                      awsInternalConfig, function, deploymentReleaseData.getRegion()),
                  serverlessAwsLambdaInfraConfig.getInfraStructureKey());
          serverInstanceInfoList.add(serverlessAwsLambdaServerInstanceInfo);
        }
      }
    }
    return serverInstanceInfoList;
  }
}
