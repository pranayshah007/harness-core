/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigGitFile;
import io.harness.git.model.GitFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OwnedBy(CDP)
public class ConfigGitFilesMapper {
  public List<ConfigGitFile> getConfigGitFiles(List<GitFile> gitFileList) {
    if (isEmpty(gitFileList)) {
      return Collections.emptyList();
    }

    List<ConfigGitFile> configGitFileList = new ArrayList<>();
    for (GitFile gitFile : gitFileList) {
      ConfigGitFile configGitFile =
          ConfigGitFile.builder().filePath(gitFile.getFilePath()).fileContent(gitFile.getFileContent()).build();
      configGitFileList.add(configGitFile);
    }
    return configGitFileList;
  }
}
