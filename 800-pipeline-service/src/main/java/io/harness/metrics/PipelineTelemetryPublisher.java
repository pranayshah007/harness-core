package io.harness.metrics;

import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
public class PipelineTelemetryPublisher {
  @Inject PMSPipelineService pmsPipelineService;
  @Inject PMSExecutionService pmsExecutionService;
  @Inject TelemetryReporter telemetryReporter;

  String PIPELINES_CREATED_IN_A_DAY = "pipelines_create_in_a_day";
  String TOTAL_NUMBER_OF_PIPELINES = "total_number_of_pipelines";
  String EXECUTIONS_IN_A_DAY = "pipelines_executed_in_a_day";
  String TOTAL_EXECUTIONS = "total_pipeline_executions";

  public void recordTelemetry() {
    Long MILLISECONDS_IN_A_DAY = 86400000L;
    Long pipelinesCreatedInADay = 0L;
    Long totalNumberOfPipelines = 0L;
    Long pipelinesExecutedInADay = 0L;
    Long totalPipelinesExecuted = 0L;

    String accountId = getAccountId();

    Criteria criteria =
        Criteria.where(PipelineEntityKeys.createdAt).gt(System.currentTimeMillis() - MILLISECONDS_IN_A_DAY);
    pipelinesCreatedInADay = pmsPipelineService.countAllPipelines(criteria);

    Criteria noCriteria = new Criteria();
    totalNumberOfPipelines = pmsPipelineService.countAllPipelines(noCriteria);

    Criteria criteriaExecutions =
        Criteria.where(PlanExecutionSummaryKeys.createdAt).gt(System.currentTimeMillis() - MILLISECONDS_IN_A_DAY);
    pipelinesExecutedInADay = pmsExecutionService.getCountOfExecutions(criteriaExecutions);

    Criteria noCriteriaExecutions = new Criteria();
    totalPipelinesExecuted = pmsExecutionService.getCountOfExecutions(noCriteriaExecutions);

    HashMap<String, Object> map = new HashMap<>();
    map.put(PIPELINES_CREATED_IN_A_DAY, pipelinesCreatedInADay);
    map.put(TOTAL_NUMBER_OF_PIPELINES, totalNumberOfPipelines);
    map.put(EXECUTIONS_IN_A_DAY, pipelinesExecutedInADay);
    map.put(TOTAL_EXECUTIONS, totalPipelinesExecuted);
    telemetryReporter.sendGroupEvent(
        accountId, null, map, null, TelemetryOption.builder().sendForCommunity(true).build());
  }

  private String getAccountId() {
    Criteria filterCriteria = new Criteria();
    PipelineEntity pipelineEntity = pmsPipelineService.findFirstPipeline(filterCriteria);

    return pipelineEntity.getAccountId();
  }
}
