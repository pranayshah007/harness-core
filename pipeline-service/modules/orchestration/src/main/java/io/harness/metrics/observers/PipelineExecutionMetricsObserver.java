/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.observers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.metrics.PipelineMetricUtils;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
public class PipelineExecutionMetricsObserver
    implements OrchestrationStartObserver, OrchestrationEndObserver, AsyncInformObserver {
  private static final String PIPELINE_EXECUTION_STARTING_COUNT = "pipeline_execution_start_count";
  private static final String PIPELINE_EXECUTION_END_COUNT = "pipeline_execution_end_count";

  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject PipelineMetricUtils pipelineMetricUtils;

  @Override
  public void onStart(OrchestrationStartInfo orchestrationStartInfo) {
    // Add metrics for execution starting for running and queued.
    pipelineMetricUtils.publishPipelineExecutionMetrics(PIPELINE_EXECUTION_STARTING_COUNT,
        orchestrationStartInfo.getStartStatus(),
        SetupAbstractionUtils.getAccountId(orchestrationStartInfo.getAmbiance().getSetupAbstractionsMap()));
  }

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    // Update pipeline execution metrics for end
    pipelineMetricUtils.publishPipelineExecutionMetrics(
        PIPELINE_EXECUTION_END_COUNT, endStatus, AmbianceUtils.getAccountId(ambiance));
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
