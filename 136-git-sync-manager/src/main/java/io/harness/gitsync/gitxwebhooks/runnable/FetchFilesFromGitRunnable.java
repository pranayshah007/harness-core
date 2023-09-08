/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.dtos.ScmUpdateGitFileCacheRequestDTO;
import io.harness.gitsync.common.dtos.ScmUpdateGitFileCacheResponseDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.logging.ResponseTimeRecorder;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class FetchFilesFromGitRunnable implements Runnable {
  @Inject ScmFacilitatorService scmFacilitatorService;

  private ScmUpdateGitFileCacheRequestDTO scmUpdateGitFileCacheRequestDTO;

  public FetchFilesFromGitRunnable(ScmUpdateGitFileCacheRequestDTO scmUpdateGitFileCacheRequestDTO) {
    this.scmUpdateGitFileCacheRequestDTO = scmUpdateGitFileCacheRequestDTO;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("FetchFilesFromGitRunnable BG Task")) {
      ScmUpdateGitFileCacheResponseDTO scmUpdateGitFileCacheResponseDTO =
          scmFacilitatorService.updateGitFileCache(scmUpdateGitFileCacheRequestDTO);
    } catch (Exception exception) {
    }
  }
}
