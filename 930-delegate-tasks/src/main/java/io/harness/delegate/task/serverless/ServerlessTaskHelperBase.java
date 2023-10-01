/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_ARTIFACTORY_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_ARTIFACTORY_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_ARTIFACTORY_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_GIT_FILES_DOWNLOAD_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_GIT_FILES_DOWNLOAD_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_GIT_FILES_DOWNLOAD_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_S3_FILES_DOWNLOAD_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.SERVERLESS_S3_FILES_DOWNLOAD_HINT;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
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
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FileCreationException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serverless.model.AwsLambdaFunctionDetails;
import io.harness.serverless.model.ServerlessDelegateTaskParams;
import io.harness.shell.SshSessionConfig;

import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

import com.amazonaws.services.s3.model.S3Object;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class ServerlessTaskHelperBase {
  @Inject private GitFetchTaskHelper gitFetchTaskHelper;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private NGGitService ngGitService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private ArtifactoryNgService artifactoryNgService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Inject protected AwsApiHelperService awsApiHelperService;
  @Inject private DecryptionHelper decryptionHelper;
  @Inject private ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  private static final String ARTIFACTORY_ARTIFACT_PATH = "artifactPath";
  private static final String ARTIFACTORY_ARTIFACT_NAME = "artifactName";
  private static final String ARTIFACT_FILE_NAME = "artifactFile";
  private static final String ARTIFACT_DIR_NAME = "harnessArtifact";
  private static final String SIDECAR_ARTIFACT_FILE_NAME_PREFIX = "sidecar-artifact-";

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
    if (serverlessManifestConfig.getGitStoreDelegateConfig() != null) {
      GitStoreDelegateConfig gitStoreDelegateConfig = serverlessManifestConfig.getGitStoreDelegateConfig();
      printFilesInExecutionLogs(gitStoreDelegateConfig, executionLogCallback);
      downloadFilesFromGit(
          gitStoreDelegateConfig, executionLogCallback, accountId, serverlessDelegateTaskParams.getWorkingDirectory());
      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(
          getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory()));
    } else {
      S3StoreDelegateConfig s3StoreDelegateConfig = serverlessManifestConfig.getS3StoreDelegateConfig();
      downloadFilesFromS3(
          s3StoreDelegateConfig, executionLogCallback, serverlessDelegateTaskParams.getWorkingDirectory());
      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(
          getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory()));
    }
  }

  private void downloadFilesFromGit(GitStoreDelegateConfig gitStoreDelegateConfig, LogCallback executionLogCallback,
      String accountId, String workingDirectory) {
    try {
      if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
        executionLogCallback.saveExecutionLog("Using optimized file fetch");
        gitFetchTaskHelper.decryptGitStoreConfig(gitStoreDelegateConfig);
        scmFetchFilesHelper.downloadFilesUsingScm(workingDirectory, gitStoreDelegateConfig, executionLogCallback);
      } else {
        GitConfigDTO gitConfigDTO = scmConnectorMapperDelegate.toGitConfigDTO(
            gitStoreDelegateConfig.getGitConfigDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
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
          "Failed to download manifest files from git. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(
          format(SERVERLESS_GIT_FILES_DOWNLOAD_HINT, gitStoreDelegateConfig.getManifestId()),
          format(SERVERLESS_GIT_FILES_DOWNLOAD_EXPLANATION, gitStoreDelegateConfig.getConnectorName(),
              gitStoreDelegateConfig.getManifestId()),
          new ServerlessCommandExecutionException(SERVERLESS_GIT_FILES_DOWNLOAD_FAILED, sanitizedException));
    }
  }

  public void downloadFilesFromS3(S3StoreDelegateConfig s3StoreDelegateConfig, LogCallback executionLogCallback,
      String workingDirectory) throws IOException {
    decrypt(s3StoreDelegateConfig);
    AwsInternalConfig awsInternalConfig =
        awsNgConfigMapper.createAwsInternalConfig(s3StoreDelegateConfig.getAwsConnector());
    String filePath = s3StoreDelegateConfig.getPaths().get(0);
    String bucketName = s3StoreDelegateConfig.getBucketName();
    String region = s3StoreDelegateConfig.getRegion();
    S3Object s3Object = null;
    try {
      s3Object = awsApiHelperService.getObjectFromS3(awsInternalConfig, region, bucketName, filePath);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in fetching files from S3", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download manifest files from S3. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(format(SERVERLESS_S3_FILES_DOWNLOAD_HINT),
          format(SERVERLESS_S3_FILES_DOWNLOAD_FAILED, filePath, bucketName, region),
          new ServerlessCommandExecutionException(
              "Failed while trying to download files from zip file", sanitizedException));
    }
    ZipInputStream zipInputStream = new ZipInputStream(s3Object.getObjectContent());
    unzipManifestFiles(workingDirectory, zipInputStream);
  }

  private File getNewFileForZipEntry(File destinationDir, ZipEntry zipEntry) throws IOException {
    String filePath = getFilePathWithoutZipDirectory(zipEntry.getName());
    File destFile = new File(destinationDir, filePath);

    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + filePath);
    }
    return destFile;
  }

  private String getFilePathWithoutZipDirectory(String filePath) {
    String[] arr = filePath.split("/", 2);
    filePath = arr[1];
    return filePath;
  }

  public void unzipManifestFiles(String workingDirectory, ZipInputStream zipInputStream) throws IOException {
    File destDir = new File(workingDirectory);
    byte[] buffer = new byte[1024];
    ZipEntry zipEntry = skipRootDirectoryZipEntry(zipInputStream);
    while (zipEntry != null) {
      File newFile = getNewFileForZipEntry(destDir, zipEntry);
      if (zipEntry.isDirectory()) {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
          throw new IOException("Failed to create directory " + newFile);
        }
      } else {
        // fix for Windows-created archives
        File parent = newFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
          throw new IOException("Failed to create directory " + parent);
        }
        FileOutputStream fileOutputStream = new FileOutputStream(newFile);
        int len;
        while ((len = zipInputStream.read(buffer)) > 0) {
          fileOutputStream.write(buffer, 0, len);
        }
        fileOutputStream.close();
      }
      zipEntry = zipInputStream.getNextEntry();
    }
    zipInputStream.closeEntry();
    zipInputStream.close();
  }

  public ZipEntry skipRootDirectoryZipEntry(ZipInputStream zipInputStream) throws IOException {
    zipInputStream.getNextEntry();
    return zipInputStream.getNextEntry();
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

  public String getExceptionMessage(Exception e) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
    return ExceptionUtils.getMessage(sanitizedException);
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
      String workingDirectory, String savedArtifactFileName) throws IOException {
    if (serverlessArtifactConfig instanceof ServerlessArtifactoryArtifactConfig) {
      ServerlessArtifactoryArtifactConfig serverlessArtifactoryArtifactConfig =
          (ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig;
      String artifactoryDirectory = Paths.get(workingDirectory, ARTIFACT_DIR_NAME).toString();
      createDirectoryIfDoesNotExist(artifactoryDirectory);
      waitForDirectoryToBeAccessibleOutOfProcess(artifactoryDirectory, 10);
      fetchArtifactoryArtifact(
          serverlessArtifactoryArtifactConfig, logCallback, artifactoryDirectory, savedArtifactFileName);
    } else if (serverlessArtifactConfig instanceof ServerlessEcrArtifactConfig) {
      logCallback.saveExecutionLog(color("Skipping downloading artifact step as it is not needed..", White, Bold));
    } else if (serverlessArtifactConfig instanceof ServerlessS3ArtifactConfig) {
      ServerlessS3ArtifactConfig serverlessS3ArtifactConfig = (ServerlessS3ArtifactConfig) serverlessArtifactConfig;
      String s3Directory = Paths.get(workingDirectory, ARTIFACT_DIR_NAME).toString();
      createDirectoryIfDoesNotExist(s3Directory);
      waitForDirectoryToBeAccessibleOutOfProcess(s3Directory, 10);
      fetchS3Artifact(serverlessS3ArtifactConfig, logCallback, s3Directory, savedArtifactFileName);
    }
  }

  public void fetchArtifacts(ServerlessArtifactConfig serverlessArtifactConfig,
      Map<String, ServerlessArtifactConfig> sidecarServerlessArtifactConfigs, LogCallback logCallback,
      String workingDirectory) throws IOException {
    logCallback.saveExecutionLog(color("Download step for primary artifact...", White, Bold));
    fetchArtifact(serverlessArtifactConfig, logCallback, workingDirectory, ARTIFACT_FILE_NAME);

    for (Map.Entry<String, ServerlessArtifactConfig> entry : sidecarServerlessArtifactConfigs.entrySet()) {
      String savedArtifactFileName = SIDECAR_ARTIFACT_FILE_NAME_PREFIX + entry.getKey();
      logCallback.saveExecutionLog(
          color(String.format("Download step for Sidecar artifact [%s]...", entry.getKey()), White, Bold));
      fetchArtifact(entry.getValue(), logCallback, workingDirectory, savedArtifactFileName);
    }
  }

  public void fetchArtifactoryArtifact(ServerlessArtifactoryArtifactConfig artifactoryArtifactConfig,
      LogCallback executionLogCallback, String artifactoryDirectory, String savedArtifactFileName) throws IOException {
    if (EmptyPredicate.isEmpty(artifactoryArtifactConfig.getArtifactPath())) {
      executionLogCallback.saveExecutionLog("artifactPath or artifactPathFilter is blank", ERROR);
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
    String artifactFilePath = Paths.get(artifactoryDirectory, savedArtifactFileName).toAbsolutePath().toString();
    File artifactFile = new File(artifactFilePath);
    if (!artifactFile.createNewFile()) {
      log.error("Failed to create new file");
      executionLogCallback.saveExecutionLog("Failed to create a file for artifactory", ERROR);
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
        executionLogCallback.saveExecutionLog("Failed to download artifact from artifactory.ø", ERROR);
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
          "Failed to download artifact from artifactory. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_ARTIFACTORY_HINT,
          String.format(
              DOWNLOAD_FROM_ARTIFACTORY_EXPLANATION, artifactPath, artifactoryConfigRequest.getArtifactoryUrl()),
          new ServerlessCommandExecutionException(
              format(DOWNLOAD_FROM_ARTIFACTORY_FAILED, artifactoryArtifactConfig.getIdentifier()), sanitizedException));
    }
  }

  public void fetchS3Artifact(ServerlessS3ArtifactConfig s3ArtifactConfig, LogCallback executionLogCallback,
      String s3Directory, String savedArtifactFileName) throws IOException {
    if (EmptyPredicate.isEmpty(s3ArtifactConfig.getFilePath())) {
      executionLogCallback.saveExecutionLog("artifactPath or artifactPathFilter is blank", ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, s3ArtifactConfig.getIdentifier()),
          new ServerlessCommandExecutionException(BLANK_ARTIFACT_PATH));
    }

    String artifactPath = Paths.get(s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath()).toString();
    String artifactFilePath = Paths.get(s3Directory, savedArtifactFileName).toAbsolutePath().toString();
    File artifactFile = new File(artifactFilePath);
    if (!artifactFile.createNewFile()) {
      log.error("Failed to create new file");
      executionLogCallback.saveExecutionLog("Failed to create a file for s3 object", ERROR);
      throw new FileCreationException("Failed to create file " + artifactFile.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }
    executionLogCallback.saveExecutionLog(
        color(format("Downloading %s artifact with identifier: %s", s3ArtifactConfig.getServerlessArtifactType(),
                  s3ArtifactConfig.getIdentifier()),
            White, Bold));
    executionLogCallback.saveExecutionLog("S3 Object Path: " + artifactPath);
    List<DecryptableEntity> decryptableEntities =
        s3ArtifactConfig.getConnectorDTO().getConnectorConfig().getDecryptableEntities();
    if (isNotEmpty(decryptableEntities)) {
      for (DecryptableEntity entity : decryptableEntities) {
        secretDecryptionService.decrypt(entity, s3ArtifactConfig.getEncryptedDataDetails());
      }
    }
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        s3ArtifactConfig.getConnectorDTO().getConnectorConfig(), s3ArtifactConfig.getEncryptedDataDetails());
    AwsInternalConfig awsConfig = awsNgConfigMapper.createAwsInternalConfig(
        (AwsConnectorDTO) s3ArtifactConfig.getConnectorDTO().getConnectorConfig());
    String region =
        EmptyPredicate.isNotEmpty(s3ArtifactConfig.getRegion()) ? s3ArtifactConfig.getRegion() : AWS_DEFAULT_REGION;
    try (InputStream artifactInputStream =
             awsApiHelperService
                 .getObjectFromS3(awsConfig, region, s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath())
                 .getObjectContent();
         FileOutputStream outputStream = new FileOutputStream(artifactFile)) {
      if (artifactInputStream == null) {
        log.error("Failure in downloading artifact from S3");
        executionLogCallback.saveExecutionLog("Failed to download artifact from S3.ø", ERROR);
        throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
            String.format(
                DOWNLOAD_FROM_S3_EXPLANATION, s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath()),
            new ServerlessCommandExecutionException(format(DOWNLOAD_FROM_S3_FAILED, s3ArtifactConfig.getIdentifier())));
      }
      IOUtils.copy(artifactInputStream, outputStream);
      executionLogCallback.saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from s3", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download artifact from s3. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
          String.format(DOWNLOAD_FROM_S3_EXPLANATION, s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath()),
          new ServerlessCommandExecutionException(
              format(DOWNLOAD_FROM_S3_FAILED, s3ArtifactConfig.getIdentifier()), sanitizedException));
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

  public void cleanup(String workingDirectory) {
    try {
      log.warn("Cleaning up directory " + workingDirectory);
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    } catch (Exception ex) {
      log.warn("Exception in directory cleanup.", ExceptionMessageSanitizer.sanitizeException(ex));
    }
  }
}
