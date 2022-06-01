/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.git.model.GitFile;

import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.yaml.GitFetchFilesResult;

import java.util.ArrayList;
import java.util.List;

public class K8sManagerHelper {
  public static List<ManifestFile> manifestFilesFromGitFetchFilesResult(
      GitFetchFilesResult gitFetchFilesResult, String prefixPath) {
    List<ManifestFile> manifestFiles = new ArrayList<>();

    if (isNotEmpty(gitFetchFilesResult.getFiles())) {
      List<GitFile> files = gitFetchFilesResult.getFiles();

      for (GitFile gitFile : files) {
        String filePath = K8sTaskHelperBase.getRelativePath(gitFile.getFilePath(), prefixPath);
        manifestFiles.add(ManifestFile.builder().fileName(filePath).fileContent(gitFile.getFileContent()).build());
      }
    }

    return manifestFiles;
  }
}
