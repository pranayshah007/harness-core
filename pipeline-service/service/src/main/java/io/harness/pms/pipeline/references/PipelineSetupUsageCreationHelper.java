/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.references;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.pms.pipeline.PipelineEntity;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineSetupUsageCreationHelper {
  private ExecutorService executor;
  private Injector injector;
  private final String USE_CASE = "Pipeline Setup Usage Creation Background Task";

  @Inject
  public PipelineSetupUsageCreationHelper(
      @Named("pipelineSetupUsageCreationExecutorService") ExecutorService executor, Injector injector) {
    this.executor = executor;
    this.injector = injector;
  }

  public void submitTask(FilterCreationParams filterCreationParams) {
    PipelineEntity pipelineEntity = filterCreationParams.getPipelineEntity();
    Scope pipelineScope = Scope.of(pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier());
    try (PipelineSetupUsageLogContext ignore1 = new PipelineSetupUsageLogContext(
             pipelineScope, pipelineEntity.getAccountIdentifier(), OVERRIDE_ERROR, USE_CASE)) {
      try {
        PipelineSetupUsageCreationRunnable pipelineSetupUsageCreationRunnable =
            new PipelineSetupUsageCreationRunnable(filterCreationParams);
        injector.injectMembers(pipelineSetupUsageCreationRunnable);
        executor.execute(pipelineSetupUsageCreationRunnable);
      } catch (RejectedExecutionException rejectedExecutionException) {
        log.warn("Skipping background pipeline setup usage creation task for pipeline {} as task queue is full : {}",
            pipelineEntity.getIdentifier(), rejectedExecutionException.getMessage());
      } catch (Exception exception) {
        log.error("Faced exception while submitting background pipeline setup usage creation task for pipeline: {}",
            pipelineEntity.getIdentifier(), exception);
      }
    }
  }
}
