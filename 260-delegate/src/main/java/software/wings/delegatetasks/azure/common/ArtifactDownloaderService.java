/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.UNIX_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.FileBucket;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.FileCopyException;
import io.harness.exception.FileCreationException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.services.DelegateFileManager;
import software.wings.delegatetasks.azure.common.context.ArtifactDownloaderContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class ArtifactDownloaderService {
  @Inject private DelegateFileManager delegateFileManager;

  public File downloadArtifactFile(ArtifactDownloaderContext downloaderContext) {
    File workingDirectory = downloaderContext.getWorkingDirectory();
    ArtifactStreamAttributes artifactStreamAttributes = downloaderContext.getArtifactStreamAttributes();

    try (InputStream artifactFileStream = delegateFileManager.downloadArtifactAtRuntime(artifactStreamAttributes,
             downloaderContext.getAccountId(), downloaderContext.getAppId(), downloaderContext.getActivityId(),
             downloaderContext.getCommandName(), artifactStreamAttributes.getRegistryHostName())) {
      return copyArtifactFile(artifactFileStream, workingDirectory, artifactStreamAttributes.getArtifactName());
    } catch (IOException | ExecutionException ex) {
      throw new InvalidRequestException(
          format("Unable to download artifact, artifactFileName: %s, artifactId: %s",
              artifactStreamAttributes.getArtifactName(), artifactStreamAttributes.getArtifactId()),
          ex);
    }
  }

  public File downloadArtifactFileFromManager(ArtifactDownloaderContext downloaderContext) {
    String accountId = downloaderContext.getAccountId();
    List<ArtifactFile> artifactFiles = downloaderContext.getArtifactFiles();
    if (isEmpty(artifactFiles)) {
      throw new InvalidArgumentsException(format("Artifact file list is empty, accountId: %s", accountId));
    }

    return downloadArtifactByFileId(downloaderContext.getWorkingDirectory(), artifactFiles.get(0), accountId);
  }

  private File downloadArtifactByFileId(
      final File workingDirectory, ArtifactFile artifactFile, final String accountId) {
    try (InputStream inputStream = delegateFileManager.downloadArtifactByFileId(
             FileBucket.ARTIFACTS, artifactFile.getFileUuid(), accountId)) {
      return copyArtifactFile(inputStream, workingDirectory, artifactFile.getName());
    } catch (IOException | ExecutionException ex) {
      throw new InvalidRequestException(
          format("Unable to download artifact, artifactFileName: %s, artifactFileUUID: %s", artifactFile.getFileName(),
              artifactFile.getFileUuid()),
          ex);
    }
  }

  private File copyArtifactFile(InputStream inputStream, final File destDirectory, final String artifactFileName) {
    File artifact = createArtifactFileInWorkingDirectory(destDirectory, artifactFileName);
    try (FileOutputStream output = new FileOutputStream(artifact)) {
      IOUtils.copy(inputStream, output);
      return artifact;
    } catch (IOException e) {
      throw new FileCopyException(
          format("Failed to copy artifact file from input stream to path, artifactAbsolutePath: %s",
              artifact.getAbsolutePath()),
          e);
    }
  }

  private File createArtifactFileInWorkingDirectory(final File workingDirectory, final String artifactName) {
    String fileName = FilenameUtils.getName(System.currentTimeMillis() + artifactName);
    String artifactFilePath = FilenameUtils.concat(workingDirectory.getAbsolutePath(), fileName + UNIX_SEPARATOR);
    File artifactFile = new File(artifactFilePath);

    try {
      if (!artifactFile.createNewFile()) {
        throw new FileCreationException(
            format("Failed to create artifact file, artifactFile: %s ", artifactFile.getCanonicalPath()), null,
            ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
      }

      return artifactFile;
    } catch (IOException e) {
      throw new FileCreationException(format("Failed to create artifact file, artifactFile: %s", artifactFilePath),
          null, ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }
  }
}
