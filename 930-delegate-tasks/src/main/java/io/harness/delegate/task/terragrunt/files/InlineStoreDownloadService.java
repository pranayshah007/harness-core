/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt.files;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.INLINE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.storeconfig.InlineFileConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;

import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@OwnedBy(CDP)
public class InlineStoreDownloadService implements FileStoreDownloadService {
  @Override
  public DownloadResult download(StoreDelegateConfig storeConfig, String accountId, String outputDirectory,
      LogCallback logCallback) throws IOException {
    downloadFiles(storeConfig, outputDirectory, logCallback);
    return DownloadResult.builder().rootDirectory(outputDirectory).build();
  }

  @Override
  public FetchFilesResult fetchFiles(StoreDelegateConfig storeConfig, String accountId, String outputDirectory,
      LogCallback logCallback) throws IOException {
    return downloadFiles(storeConfig, outputDirectory, logCallback);
  }

  private FetchFilesResult downloadFiles(
      StoreDelegateConfig storeConfig, String outputDirectory, LogCallback logCallback) throws IOException {
    validateInlineStore(storeConfig);
    InlineStoreDelegateConfig inlineStoreConfig = (InlineStoreDelegateConfig) storeConfig;

    FileIo.createDirectoryIfDoesNotExist(outputDirectory);
    List<String> filePaths = new ArrayList<>();
    String loggableOutputDirectory = getLoggablePath(outputDirectory);
    for (InlineFileConfig inlineFileConfig : inlineStoreConfig.getFiles()) {
      UUID uuid = UUID.randomUUID();
      String fileName = inlineFileConfig.getName().replace("${UUID}", uuid.toString());
      logCallback.saveExecutionLog(format("Save inline file '%s' to '%s'", fileName, loggableOutputDirectory));
      Path filePath = Files.createFile(Paths.get(outputDirectory, fileName));
      Files.write(filePath, inlineFileConfig.getContent().getBytes());
      filePaths.add(filePath.toAbsolutePath().toString());
    }

    return FetchFilesResult.builder().files(filePaths).identifier(inlineStoreConfig.getIdentifier()).build();
  }

  private void validateInlineStore(StoreDelegateConfig storeConfig) {
    if (INLINE != storeConfig.getType()) {
      throw new InvalidArgumentsException(
          Pair.of("storeConfig", format("Invalid store config '%s', expected '%s'", storeConfig.getType(), INLINE)));
    }
  }
}
