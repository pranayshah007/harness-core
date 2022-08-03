/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.execution.PlanExecution;
import io.harness.logging.AutoLogContext;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.service.GraphGenerationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class OrchestrationEndGraphHandler implements AsyncInformObserver, OrchestrationEndObserver {
  private final ExecutorService executorService;
  private final PlanExecutionService planExecutionService;
  private final GraphGenerationService graphGenerationService;
  private final OrchestrationEventLogRepository orchestrationEventLogRepository;

  @Inject
  public OrchestrationEndGraphHandler(@Named("OrchestrationVisualizationExecutorService")
                                      ExecutorService executorService, PlanExecutionService planExecutionService,
      GraphGenerationService graphGenerationService, OrchestrationEventLogRepository orchestrationEventLogRepository) {
    this.executorService = executorService;
    this.planExecutionService = planExecutionService;
    this.graphGenerationService = graphGenerationService;
    this.orchestrationEventLogRepository = orchestrationEventLogRepository;
  }

  @Override
  public void onEnd(Ambiance ambiance) {
    try (AutoLogContext autoLogContext = AmbianceUtils.autoLogContext(ambiance)) {
      PlanExecution planExecution = planExecutionService.get(ambiance.getPlanExecutionId());
      // One last time try to update the graph to process any unprocessed logs
      boolean updated = graphGenerationService.updateGraphWithWaitLock(planExecution.getUuid());
      if (!updated) {
        log.error(
            "[GRAPH_ERROR] Exception occurred while updating graph through logs. Regenerating the graph from nodeExecutions");
        graphGenerationService.buildOrchestrationGraph(ambiance.getPlanExecutionId());
      }
      // Todo: Check if this is required
      OrchestrationGraph orchestrationGraph =
          graphGenerationService.getCachedOrchestrationGraph(ambiance.getPlanExecutionId());
      orchestrationGraph = orchestrationGraph.withStatus(planExecution.getStatus()).withEndTs(planExecution.getEndTs());
      graphGenerationService.cacheOrchestrationGraph(orchestrationGraph);
    } catch (Exception e) {
      log.error("[GRAPH_ERROR] Cannot update Orchestration graph for ORCHESTRATION_END", e);
      throw e;
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
