/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.observers;

import io.harness.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.metrics.PipelineMetricUtils;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
public class PipelineExecutionMetricsObserver implements OrchestrationEndObserver, AsyncInformObserver {
  private static final String PIPELINE_EXECUTION_END_COUNT = "pipeline_execution_end_count";

  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject PipelineMetricUtils pipelineMetricUtils;
  @Inject PipelineSettingsService pipelineSettingsService;

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    // Update pipeline execution metrics for end
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String accountEdition = pipelineSettingsService.getAccountEdition(accountId);
    pipelineMetricUtils.publishPipelineExecutionMetrics(
        PIPELINE_EXECUTION_END_COUNT, endStatus, accountId, accountEdition);
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
