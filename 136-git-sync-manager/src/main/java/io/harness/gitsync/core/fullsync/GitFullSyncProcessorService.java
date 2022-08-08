/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.core.beans.FullSyncMsvcProcessingResponse;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;

import java.util.List;

@OwnedBy(DX)
public interface GitFullSyncProcessorService {
  FullSyncMsvcProcessingResponse processFilesInBatches(
      Microservice microservice, List<GitFullSyncEntityInfo> entityInfoList);

  void performFullSync(GitFullSyncJob fullSyncJob);
}
