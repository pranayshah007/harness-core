/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.dao;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.FileProcessingResponse;
import io.harness.gitsync.FileProcessingStatus;
import io.harness.gitsync.GitToHarnessProcessRequest;
import io.harness.gitsync.beans.GitProcessRequest;

import com.mongodb.client.result.UpdateResult;
import java.util.Map;

@OwnedBy(DX)
public interface GitProcessingRequestService {
  Map<String, FileProcessingResponse> upsert(GitToHarnessProcessRequest gitToHarnessProcessRequest);

  UpdateResult updateFileStatus(
      String commitId, String fileId, FileProcessingStatus status, String errorMsg, String accountId);

  GitProcessRequest get(String commitId, String accountId, String repo, String branch);
  boolean deleteByAccount(String accountId);
}
