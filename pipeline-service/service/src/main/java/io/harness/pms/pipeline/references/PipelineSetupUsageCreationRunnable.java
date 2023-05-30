/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.references;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.pms.filter.creation.FilterCreatorMergeService;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineSetupUsageCreationRunnable implements Runnable {
  @Inject FilterCreatorMergeService filterCreatorMergeService;
  private FilterCreationParams filterCreationParams;

  public PipelineSetupUsageCreationRunnable(FilterCreationParams filterCreationParams) {
    this.filterCreationParams = filterCreationParams;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("PipelineReferencesRunnable BG Task")) {
      String pipelineIdentifier = filterCreationParams.getPipelineEntity().getIdentifier();
      try {
        log.info(String.format("Calculating pipeline setup usage creation in the background for pipelineIdentifier: %s",
            pipelineIdentifier));
        //        The filter service is being called here as the references are calculated as part of filter creation
        filterCreatorMergeService.getPipelineInfo(filterCreationParams);
      } catch (IOException e) {
        log.error(String.format(
            "Faced an IO exception while calculating setup usage creation for pipeline: %s.", pipelineIdentifier));
      } catch (Exception exception) {
        log.error("Faced exception while calculating setup usage creation for pipeline {} in BG THREAD : ",
            pipelineIdentifier, exception);
      }
    }
  }
}
