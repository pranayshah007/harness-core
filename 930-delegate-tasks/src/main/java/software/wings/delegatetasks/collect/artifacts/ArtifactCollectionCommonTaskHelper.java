/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.collect.artifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.ListNotifyResponseData;

import software.wings.beans.artifact.ArtifactFile;
import software.wings.delegatetasks.DelegateFileManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ArtifactCollectionCommonTaskHelper {
  @Inject private DelegateFileManager delegateFileManager;

  public void addDataToResponse(Pair<String, InputStream> fileInfo, String artifactPath, ListNotifyResponseData res,
      String delegateId, String taskId, String accountId) throws FileNotFoundException {
    if (fileInfo == null) {
      throw new FileNotFoundException("Unable to get artifact for path " + artifactPath);
    }
    log.info("Uploading the file {} for artifact path {}", fileInfo.getKey(), artifactPath);

    DelegateFile delegateFile = aDelegateFile()
                                    .withBucket(FileBucket.ARTIFACTS)
                                    .withFileName(fileInfo.getKey())
                                    .withDelegateId(delegateId)
                                    .withTaskId(taskId)
                                    .withAccountId(accountId)
                                    .build(); // TODO: more about delegate and task info
    DelegateFile fileRes = null;
    try (InputStream in = fileInfo.getValue()) {
      fileRes = delegateFileManager.upload(delegateFile, in);
    } catch (IOException ignored) {
    }

    if (fileRes == null || fileRes.getFileId() == null) {
      log.error(
          "Failed to upload file name {} for artifactPath {} to manager. Artifact files will be uploaded during the deployment of Artifact Check Step",
          fileInfo.getKey(), artifactPath);
    } else {
      log.info("Uploaded the file name {} and fileUuid {} for artifactPath {}", fileInfo.getKey(), fileRes.getFileId(),
          artifactPath);
      ArtifactFile artifactFile = new ArtifactFile();
      artifactFile.setFileUuid(fileRes.getFileId());
      artifactFile.setName(fileInfo.getKey());
      res.addData(artifactFile);
    }
  }
}
