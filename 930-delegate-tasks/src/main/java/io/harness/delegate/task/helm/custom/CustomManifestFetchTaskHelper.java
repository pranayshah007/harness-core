/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceFile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CustomManifestFetchTaskHelper {
  private static final String HELM_IGNORE_FILENAME = ".helmignore";
  @Inject CustomManifestService customManifestService;
  @Inject DelegateFileManagerBase delegateFileManagerBase;

  private static File getNewFileForZipEntry(File destinationDir, ZipEntry zipEntry) throws IOException {
    File destFile = new File(destinationDir, zipEntry.getName());

    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }

    return destFile;
  }

  public static void unzipManifestFiles(File destDir, ZipInputStream zipInputStream) throws IOException {
    byte[] buffer = new byte[1024];
    ZipEntry zipEntry = zipInputStream.getNextEntry();
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

  private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
    if (fileToZip.isHidden() && !fileName.contains(HELM_IGNORE_FILENAME)) {
      return;
    }
    if (fileToZip.isDirectory()) {
      if (fileName.endsWith("/")) {
        zipOut.putNextEntry(new ZipEntry(fileName));
        zipOut.closeEntry();
      } else {
        zipOut.putNextEntry(new ZipEntry(fileName + "/"));
        zipOut.closeEntry();
      }
      File[] children = fileToZip.listFiles();
      for (File childFile : children) {
        zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
      }
      return;
    }
    FileInputStream fis = new FileInputStream(fileToZip);
    ZipEntry zipEntry = new ZipEntry(fileName);
    zipOut.putNextEntry(zipEntry);
    byte[] bytes = new byte[1024];
    int length;
    while ((length = fis.read(bytes)) >= 0) {
      zipOut.write(bytes, 0, length);
    }
    fis.close();
  }

  public static void zipManifestDirectory(String sourceFile, String destManifestFilesDirectoryPath) throws IOException {
    FileOutputStream fos = new FileOutputStream(destManifestFilesDirectoryPath);
    ZipOutputStream zipOut = new ZipOutputStream(fos);
    File fileToZip = new File(sourceFile);

    zipFile(fileToZip, fileToZip.getName(), zipOut);
    zipOut.close();
    fos.close();
  }

  @NonNull
  public CustomManifestValuesFetchResponse fetchValuesTask(CustomManifestValuesFetchParams fetchParams,
      LogCallback logCallback, String defaultSourceWorkingDirectory, boolean closeLogStream) {
    Map<String, Collection<CustomSourceFile>> fetchedFilesContent = new HashMap<>();

    for (CustomManifestFetchConfig fetchFileConfig : fetchParams.getFetchFilesList()) {
      String workingDirectory = null;
      try {
        workingDirectory = customManifestService.getWorkingDirectory();
        CustomManifestSource customManifestSource = fetchFileConfig.getCustomManifestSource();
        String activityId = fetchParams.getActivityId();

        if (!isScriptExecutionRequired(fetchFileConfig, customManifestSource)) {
          logCallback.saveExecutionLog("Reusing execution output from service manifest.");
        }

        logCallback.saveExecutionLog("Fetching following files:");
        logFilePathList(customManifestSource.getFilePaths(), logCallback);

        Collection<CustomSourceFile> valuesContent = isScriptExecutionRequired(fetchFileConfig, customManifestSource)
            ? customManifestService.fetchValues(
                customManifestSource, workingDirectory, activityId, logCallback, closeLogStream)
            : customManifestService.readFilesContent(
                defaultSourceWorkingDirectory, customManifestSource.getFilePaths());

        if (fetchedFilesContent.containsKey(fetchFileConfig.getKey())) {
          fetchedFilesContent.get(fetchFileConfig.getKey()).addAll(valuesContent);
        } else {
          fetchedFilesContent.put(fetchFileConfig.getKey(), valuesContent);
        }

      } catch (IOException e) {
        Throwable cause = e.getCause();
        boolean isNotFound = e instanceof FileNotFoundException || cause instanceof FileNotFoundException;
        if (isNotFound && !fetchFileConfig.isRequired()) {
          log.info("Failed to fetch values file for {} and activity {}", fetchFileConfig.getKey(),
              fetchParams.getActivityId());
          logCallback.saveExecutionLog("Failed to fetch values file for " + fetchFileConfig.getKey(), LogLevel.WARN);
          continue;
        }

        String message = format("Failed to fetch values file for %s. %s.", fetchFileConfig.getKey(), e.getMessage());
        log.error(message, e);
        logCallback.saveExecutionLog(message, LogLevel.ERROR, FAILURE);
        return CustomManifestValuesFetchResponse.builder()
            .commandExecutionStatus(FAILURE)
            .errorMessage(getMessage(e))
            .build();
      } catch (Exception e) {
        String message = format("Failed to execute script. %s", e.getMessage());
        log.error(message, e);
        logCallback.saveExecutionLog(message, LogLevel.ERROR, FAILURE);
        return CustomManifestValuesFetchResponse.builder()
            .commandExecutionStatus(FAILURE)
            .errorMessage(getMessage(e))
            .build();
      } finally {
        cleanup(workingDirectory);
      }
    }

    return CustomManifestValuesFetchResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .valuesFilesContentMap(fetchedFilesContent)
        .build();
  }

  private boolean isScriptExecutionRequired(
      CustomManifestFetchConfig fetchFileConfig, CustomManifestSource customManifestSource) {
    return !fetchFileConfig.isDefaultSource() && isNotEmpty(customManifestSource.getScript());
  }

  private void logFilePathList(List<String> filePathList, LogCallback logCallback) {
    if (isEmpty(filePathList)) {
      logCallback.saveExecutionLog("Empty file list. Skip.");
      return;
    }

    StringBuilder sb = new StringBuilder();
    filePathList.forEach(filePath -> sb.append(color(format("- %s", filePath), Gray)).append(System.lineSeparator()));
    logCallback.saveExecutionLog(sb.toString());
  }

  public static void cleanup(String workingDirectory) {
    if (workingDirectory == null) {
      log.warn("provided directory path is null");
      return;
    }
    try {
      log.info("Cleaning up directory " + workingDirectory);
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    } catch (Exception ex) {
      log.warn("Exception in directory cleanup.", ex);
    }
  }

  public void downloadAndUnzipCustomSourceManifestFiles(
      String workingDirectory, String zippedManifestFileId, String accountId) throws IOException {
    InputStream inputStream =
        delegateFileManagerBase.downloadByFileId(FileBucket.CUSTOM_MANIFEST, zippedManifestFileId, accountId);
    ZipInputStream zipInputStream = new ZipInputStream(inputStream);

    File destDir = new File(workingDirectory);
    unzipManifestFiles(destDir, zipInputStream);
  }
}
