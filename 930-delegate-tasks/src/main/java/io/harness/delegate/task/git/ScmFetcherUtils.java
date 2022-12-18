/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.product.ci.scm.proto.FileContent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@UtilityClass
@Slf4j
public class ScmFetcherUtils {
  public void writeFile(String directoryPath, FileContent fileContent, String basePath, boolean relativize,
      boolean useBase64) throws IOException {
    String filePath;
    if (relativize) {
      filePath = Paths.get(basePath).relativize(Paths.get(fileContent.getPath())).toString();
      if (isEmpty(filePath)) {
        filePath = Paths.get(fileContent.getPath()).getFileName().toString();
      }
    } else {
      filePath = fileContent.getPath();
    }

    Path finalPath = Paths.get(directoryPath, filePath);
    Path parent = finalPath.getParent();
    if (parent == null) {
      throw new WingsException("Failed to create file at path " + finalPath.toString());
    }

    createDirectoryIfDoesNotExist(parent.toString());
    FileIo.writeFile(finalPath.toString(), getFileContent(fileContent, useBase64));
  }

  private byte[] getFileContent(FileContent fileContent, boolean useBase64) {
    if (!useBase64) {
      return fileContent.getContent().getBytes(StandardCharsets.UTF_8);
    }

    try {
      return Base64.getDecoder().decode(fileContent.getContent());
    } catch (IllegalArgumentException e) {
      log.warn("File content is not a valid base64 value, fallback to plain text. Error: {}", e.getMessage());
      return fileContent.getContent().getBytes(StandardCharsets.UTF_8);
    }
  }
}
