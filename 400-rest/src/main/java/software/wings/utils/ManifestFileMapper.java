/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import software.wings.beans.appmanifest.ManifestFile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ManifestFileMapper {
  public List<software.wings.beans.dto.ManifestFile> manifestFileDTOList(List<ManifestFile> manifestFiles) {
    if (isEmpty(manifestFiles)) {
      return Collections.emptyList();
    }
    return manifestFiles.stream()
        .map(manifestFile
            -> software.wings.beans.dto.ManifestFile.builder()
                   .accountId(manifestFile.getAccountId())
                   .fileName(manifestFile.getFileName())
                   .fileContent(manifestFile.getFileContent())
                   .applicationManifestId(manifestFile.getApplicationManifestId())
                   .build())
        .collect(Collectors.toList());
  }
}
