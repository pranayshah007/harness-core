/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.model.PcfConstants.PCF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static com.google.common.base.Charsets.UTF_8;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.Misc;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CfRunPluginScriptRequestData;
import io.harness.pcf.model.PcfConstants;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfRunPluginCommandTaskHandler extends PcfCommandTaskHandler {
  /**
   * Execute the pcf plugin command
   */
  @Override
  protected CfCommandExecutionResponse executeTaskInternal(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ILogStreamingTaskClient logStreamingTaskClient,
      boolean isInstanceSync) {
    if (!(cfCommandRequest instanceof CfRunPluginCommandRequest)) {
      throw new InvalidArgumentsException(Pair.of("cfCommandRequest", "Must be instance of CfPluginCommandRequest"));
    }
    LogCallback executionLogCallback = logStreamingTaskClient.obtainLogCallback(cfCommandRequest.getCommandName());
    CfRunPluginCommandRequest pluginCommandRequest = (CfRunPluginCommandRequest) cfCommandRequest;

    executionLogCallback.saveExecutionLog(color("---------- Starting PCF Run Plugin Command Execution", White, Bold));
    CfInternalConfig pcfConfig = cfCommandRequest.getPcfConfig();
    secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);
    File workingDirectory = null;
    try {
      workingDirectory = createWorkingDirectory();
      final String workingDirCanonicalPath = dirCanonicalPath(workingDirectory);

      // save the files in the directory
      if (EmptyPredicate.isNotEmpty(pluginCommandRequest.getFileDataList())) {
        saveFilesInWorkingDirectoryStringContent(pluginCommandRequest.getFileDataList(), workingDirCanonicalPath);
      }

      CfCliVersion cfCliVersion = cfCommandRequest.getCfCliVersion();
      String cfCliPath = pcfCommandTaskBaseHelper.getCfCliPathOnDelegate(true, cfCliVersion);
      //  insert working directory in script path
      final String finalScriptString = prepareFinalScript(pluginCommandRequest.getRenderedScriptString(),
          workingDirCanonicalPath, StringUtils.defaultIfEmpty(pluginCommandRequest.getRepoRoot(), "/"), cfCliPath);

      // log all the files being saved and files being resolved in the script

      executionLogCallback.saveExecutionLog("\n #  File paths identified in the Script :");
      pcfCommandTaskBaseHelper.printFileNamesInExecutionLogs(
          pluginCommandRequest.getFilePathsInScript(), executionLogCallback);

      executionLogCallback.saveExecutionLog(
          "\n #  Files saved in working directory [" + workingDirCanonicalPath + "]:");
      pcfCommandTaskBaseHelper.printFileNamesInExecutionLogs(
          CollectionUtils.emptyIfNull(pluginCommandRequest.getFileDataList())
              .stream()
              .map(FileData::getFilePath)
              .collect(Collectors.toList()),
          executionLogCallback);

      CfRequestConfig cfRequestConfig =
          CfRequestConfig.builder()
              .orgName(pluginCommandRequest.getOrganization())
              .spaceName(pluginCommandRequest.getSpace())
              .userName(String.valueOf(pcfConfig.getUsername()))
              .password(String.valueOf(pcfConfig.getPassword()))
              .endpointUrl(pcfConfig.getEndpointUrl())
              .timeOutIntervalInMins(pluginCommandRequest.getTimeoutIntervalInMin())
              .limitPcfThreads(pluginCommandRequest.isLimitPcfThreads())
              .cfCliPath(cfCliPath)
              .cfCliVersion(cfCliVersion)
              .ignorePcfConnectionContextCache(pluginCommandRequest.isIgnorePcfConnectionContextCache())
              .build();

      final CfRunPluginScriptRequestData cfRunPluginScriptRequestData = CfRunPluginScriptRequestData.builder()
                                                                            .workingDirectory(workingDirCanonicalPath)
                                                                            .finalScriptString(finalScriptString)
                                                                            .cfRequestConfig(cfRequestConfig)
                                                                            .build();
      pcfDeploymentManager.runPcfPluginScript(cfRunPluginScriptRequestData, executionLogCallback);

      executionLogCallback.saveExecutionLog(
          "\n ----------  PCF Run Plugin Command completed successfully", INFO, CommandExecutionStatus.SUCCESS);
      return CfCommandExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    } catch (Exception e) {
      return handleError(executionLogCallback, pluginCommandRequest, e);
    } finally {
      removeTempFilesCreated(executionLogCallback, workingDirectory);
    }
  }

  private String dirCanonicalPath(File workingDirectory) throws IOException {
    String canonicalPath = workingDirectory.getCanonicalPath().trim();
    if (canonicalPath.endsWith("/")) {
      canonicalPath = canonicalPath.substring(0, canonicalPath.length() - 1);
    }
    return canonicalPath;
  }

  @VisibleForTesting
  String prepareFinalScript(
      String renderedScriptString, String workingDirCanonicalPathStr, String repoRoot, String cfCliPath) {
    // replace the path identifier with actual working directory path
    String finalScript =
        renderedScriptString.replaceAll(PcfConstants.FILE_START_REPO_ROOT_REGEX, workingDirCanonicalPathStr);
    final String dirPathWithRepoRoot = workingDirCanonicalPathStr + ("/".equals(repoRoot) ? "" : repoRoot);
    finalScript = finalScript.replaceAll(PcfConstants.FILE_START_SERVICE_MANIFEST_REGEX, dirPathWithRepoRoot);

    return finalScript;
  }

  @VisibleForTesting
  void saveFilesInWorkingDirectory(final List<FileData> fileDataList, final String workingDirectoryCanonicalPath) {
    if (EmptyPredicate.isNotEmpty(fileDataList)) {
      fileDataList.forEach(file -> {
        try {
          final Path filePath = Paths.get(workingDirectoryCanonicalPath, canonicalise(file.getFilePath()));
          // ensure directory exists
          FileIo.createDirectoryIfDoesNotExist(filePath.getParent());
          // ensure file is newly created
          Files.deleteIfExists(filePath);
          final Path createdFile = Files.createFile(filePath);
          final byte[] fileBytes = file.getFileBytes();
          if (fileBytes != null) {
            FileIo.writeFile(createdFile, fileBytes);
          }
        } catch (IOException e) {
          throw new UnexpectedException(
              "Error while writing file :" + file.getFilePath() + "in directory :" + workingDirectoryCanonicalPath);
        }
      });
    }
  }

  @VisibleForTesting
  void saveFilesInWorkingDirectoryStringContent(
      final List<FileData> fileDataList, final String workingDirectoryCanonicalPath) throws IOException {
    if (EmptyPredicate.isEmpty(fileDataList)) {
      return;
    }
    for (FileData file : fileDataList) {
      final Path filePath = Paths.get(workingDirectoryCanonicalPath, canonicalise(file.getFilePath()));
      FileIo.createDirectoryIfDoesNotExist(filePath.getParent());
      Files.deleteIfExists(filePath);
      final Path createdFile = Files.createFile(filePath);
      String fileContent = file.getFileContent();
      FileUtils.writeStringToFile(createdFile.toFile(), fileContent, UTF_8);
    }
  }

  private String canonicalise(String filePath) {
    String result = filePath.trim();
    if (result.isEmpty() || result.charAt(0) != '/') {
      result = "/" + result;
    }
    return result;
  }

  @VisibleForTesting
  CfCommandExecutionResponse handleError(
      LogCallback executionLogCallback, CfRunPluginCommandRequest pluginCommandRequest, Exception e) {
    log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Run Plugin Command task [{}]",
        pluginCommandRequest);
    log.error("Exception is ", e);
    executionLogCallback.saveExecutionLog("\n\n ----------  PCF Run Plugin Command failed to complete successfully",
        ERROR, CommandExecutionStatus.FAILURE);
    Misc.logAllMessages(e, executionLogCallback);
    return CfCommandExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .errorMessage(ExceptionUtils.getMessage(e))
        .build();
  }

  private void removeTempFilesCreated(LogCallback executionLogCallback, File workingDirectory) {
    try {
      executionLogCallback.saveExecutionLog("# Deleting any temporary files created");
      if (workingDirectory != null) {
        FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
      }
    } catch (Exception e) {
      log.warn("Failed to remove temp files created", e);
    }
  }

  private File createWorkingDirectory() throws IOException {
    // This value is set to CF_HOME env variable when process executor is created.
    String randomToken = UUIDGenerator.generateUuid();
    return generateWorkingDirectoryForScript(randomToken);
  }

  private File generateWorkingDirectoryForScript(String workingDirecotry) throws IOException {
    String workingDir = PCF_ARTIFACT_DOWNLOAD_DIR_PATH + "/" + workingDirecotry;
    FileIo.createDirectoryIfDoesNotExist(workingDir);
    return new File(workingDir);
  }
}
