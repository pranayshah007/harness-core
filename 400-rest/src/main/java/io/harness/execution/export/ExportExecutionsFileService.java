/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.exception.ExportExecutionsException;

import software.wings.service.intfc.FileService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ExportExecutionsFileService {
  private static final FileBucket FILE_BUCKET = FileBucket.EXPORT_EXECUTIONS;

  @Inject private FileService fileService;

  public String uploadFile(@NotNull String accountId, @NotNull File file) {
    try (FileInputStream in = new FileInputStream(file)) {
      return fileService.saveFile(prepareFileMetadata(accountId, file), in, FILE_BUCKET);
    } catch (Exception ex) {
      throw new ExportExecutionsException("Unable to upload file for export executions request", ex);
    }
  }

  @VisibleForTesting
  FileMetadata prepareFileMetadata(@NotNull String accountId, @NotNull File file) {
    return FileMetadata.builder()
        .accountId(accountId)
        .fileName(file.getName())
        .fileUuid(UUIDGenerator.generateUuid())
        .build();
  }

  public void downloadFileToStream(@NotNull String fileId, @NotNull OutputStream outputStream) {
    try {
      fileService.downloadToStream(fileId, outputStream, FILE_BUCKET);
    } catch (Exception ex) {
      throw new ExportExecutionsException("Unable to download file for export executions request", ex);
    }
  }

  public void deleteFile(String fileId) {
    if (fileId == null) {
      return;
    }

    try {
      fileService.deleteFile(fileId, FILE_BUCKET);
    } catch (Exception ex) {
      throw new ExportExecutionsException(
          format("Unable to delete file [%s] for export executions request", fileId), ex);
    }
  }
}
