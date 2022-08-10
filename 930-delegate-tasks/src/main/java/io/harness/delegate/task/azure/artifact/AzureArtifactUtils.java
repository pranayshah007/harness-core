/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExplanationException;
import io.harness.exception.FileCopyException;
import io.harness.exception.FileCreationException;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.utils.ArtifactType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@OwnedBy(CDP)
@UtilityClass
@Slf4j
public class AzureArtifactUtils {
  public static final String ARTIFACT_NAME_PREFIX = "artifact-";
  private static final String JAR_EXTENSION = "jar";
  private static final String WAR_EXTENSION = "war";
  private static final String ZIP_EXTENSION = "zip";

  public ArtifactType detectArtifactType(String artifactPath, LogCallback logCallback) {
    if (isEmpty(artifactPath)) {
      logCallback.saveExecutionLog(
          color("Unable to detect artifact type based on empty artifact path. Fallback to default zip type",
              LogColor.Yellow, LogWeight.Bold));
      return ArtifactType.ZIP;
    }

    int fileExtensionIndex = artifactPath.lastIndexOf('.');
    if (fileExtensionIndex == -1) {
      log.warn("Unable to detect artifact path extension for artifact file {}", artifactPath);
      logCallback.saveExecutionLog(color(
          format(
              "Unable to detect artifact type based on artifact path '%s'. Unable to find file extension. Fallback to default zip type",
              artifactPath),
          LogColor.Yellow, LogWeight.Bold));
      return ArtifactType.ZIP;
    }

    String artifactExtension = artifactPath.substring(fileExtensionIndex + 1);
    switch (artifactExtension) {
      case JAR_EXTENSION:
        log.info("Detected jar artifact type for file {}", artifactPath);
        logCallback.saveExecutionLog("Detected artifact type: jar");
        return ArtifactType.JAR;
      case WAR_EXTENSION:
        log.info("Detected war artifact type for file {}", artifactPath);
        logCallback.saveExecutionLog("Detected artifact type: jar");
        return ArtifactType.WAR;
      case ZIP_EXTENSION:
        log.info("Detected zip artifact type for file {}", artifactPath);
        logCallback.saveExecutionLog("Detected artifact type: zip");
        return ArtifactType.ZIP;
      default:
        log.info("Detected default artifact type: {} for file {}", ArtifactType.ZIP, artifactPath);
        logCallback.saveExecutionLog(
            color(format("Unable to detect artifact type from file extension '%s'. Fallback to default zip type",
                      artifactExtension),
                LogColor.Yellow, LogWeight.Bold));
        return ArtifactType.ZIP;
    }
  }

  public File copyArtifactStreamToWorkingDirectory(
      ArtifactDownloadContext downloadContext, InputStream artifactStream, LogCallback logCallback) {
    final AzureArtifactRequestDetails artifactDetails = downloadContext.getArtifactConfig().getArtifactDetails();
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

  private File createArtifactFileInWorkingDirectory(ArtifactDownloadContext downloadContext) {
    final AzureArtifactRequestDetails artifactDetails = downloadContext.getArtifactConfig().getArtifactDetails();
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
}
