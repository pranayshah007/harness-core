/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.entities.NGFile;
import io.harness.ng.core.filestore.dto.FileDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class FileStoreUtils {
  public static boolean nameChanged(NGFile oldNGFile, FileDTO fileDto) {
    return !oldNGFile.getName().equals(fileDto.getName());
  }

  public static boolean parentChanged(NGFile oldNGFile, FileDTO fileDto) {
    return !oldNGFile.getParentIdentifier().equals(fileDto.getParentIdentifier());
  }

  public static boolean isPathValid(final String path) {
    return isNotEmpty(path) && path.charAt(0) == '/' && path.split("/").length > 1;
  }

  public static Optional<List<String>> getSubPaths(final String path) {
    if (isEmpty(path) || path.charAt(0) != '/' || "/".equals(path)) {
      return Optional.empty();
    }

    String[] paths = path.split("/");
    List<String> subPathList = new ArrayList<>(paths.length - 1);
    String fullSubPath = "";
    for (String subPath : paths) {
      if (isEmpty(subPath)) {
        continue;
      }

      fullSubPath = fullSubPath + "/" + subPath;
      subPathList.add(fullSubPath);
    }

    return Optional.of(subPathList);
  }
}
