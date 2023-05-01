/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.beans.SLOMetricContext;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOService;
import io.harness.cvng.servicelevelobjective.services.api.SLIConsecutiveMinutesProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataUnavailabilityInstancesHandlerService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLIMetricAnalysisTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.SLIMetricAnalysisState;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIMetricAnalysisStateExecutor extends AnalysisStateExecutor<SLIMetricAnalysisState> {
  @Inject private SLIDataUnavailabilityInstancesHandlerService sliDataUnavailabilityInstancesHandlerService;

  @Inject private SLIDataProcessorService sliDataProcessorService;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject private VerificationTaskService verificationTaskService;

  @Inject private SLIRecordService sliRecordService;

  @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;

  @Inject private TimeSeriesRecordService timeSeriesRecordService;

  @Inject private SLIMetricAnalysisTransformer sliMetricAnalysisTransformer;

  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;

  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private SLIConsecutiveMinutesProcessorService sliConsecutiveMinutesProcessorService;

  @Inject private MetricService metricService;

  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;

  @Inject private CompositeSLOService compositeSLOService;

  @Inject private OrchestrationService orchestrationService;

  @Inject private Clock clock;
  @Override
  public AnalysisState execute(SLIMetricAnalysisState analysisState) {
    Instant startTime = analysisState.getInputs().getStartTime();
    Instant endTime = analysisState.getInputs().getEndTime();
    String verificationTaskId = analysisState.getInputs().getVerificationTaskId();
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.get(verificationTaskService.getSliId(verificationTaskId));
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(serviceLevelIndicator.getAccountId())
                                      .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
                                      .build();
    String monitoredServiceIdentifier = serviceLevelIndicator.getMonitoredServiceIdentifier();
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOS =
        timeSeriesRecordService.getTimeSeriesRecordDTOs(verificationTaskId, startTime, endTime);
    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequest =
        sliMetricAnalysisTransformer.getSLIAnalyseRequest(timeSeriesRecordDTOS);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        serviceLevelIndicatorEntityAndDTOTransformer.getDto(serviceLevelIndicator);
    List<SLIAnalyseResponse> sliAnalyseResponseList =
        sliDataProcessorService.process(sliAnalyseRequest, serviceLevelIndicatorDTO, startTime, endTime);
    List<SLIRecordParam> sliRecordList = sliMetricAnalysisTransformer.getSLIAnalyseResponse(sliAnalyseResponseList);
    sliRecordList = sliDataUnavailabilityInstancesHandlerService.filterSLIRecordsToSkip(
        sliRecordList, projectParams, startTime, endTime, monitoredServiceIdentifier, serviceLevelIndicator.getUuid());
    sliRecordList = sliConsecutiveMinutesProcessorService.process(sliRecordList, serviceLevelIndicator);
    sliRecordService.create(
        sliRecordList, serviceLevelIndicator.getUuid(), verificationTaskId, serviceLevelIndicator.getVersion());
    SimpleServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getFromSLIIdentifier(projectParams, serviceLevelIndicator.getIdentifier());
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    // TODO (this end time won't always be the creation time, i.e in case of recalculation, we need to re-think this.
    try (SLOMetricContext sloMetricContext = new SLOMetricContext(serviceLevelIndicator)) {
      metricService.recordDuration(
          CVNGMetricsUtils.SLO_DATA_ANALYSIS_METRIC, Duration.between(clock.instant(), endTime));
    }
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(SLIMetricAnalysisState analysisState) {
    return analysisState.getStatus();
  }

  @Override
  public AnalysisState handleRerun(SLIMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleRunning(SLIMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(SLIMetricAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    if (analysisState.getInputs().isSLORestoreTask()) {
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.get(
          verificationTaskService.getSliId(analysisState.getInputs().getVerificationTaskId()));
      entityUnavailabilityStatusesService.updateDCPassedToDCRestoredForAllEntities(EntityType.SLO,
          serviceLevelIndicator.getUuid(), analysisState.getInputs().getStartTime().getEpochSecond(),
          analysisState.getInputs().getEndTime().getEpochSecond());
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(serviceLevelIndicator.getAccountId())
                                        .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
                                        .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
                                        .build();
      SimpleServiceLevelObjective simpleServiceLevelObjective =
          serviceLevelObjectiveV2Service.getFromSLIIdentifier(projectParams, serviceLevelIndicator.getIdentifier());
      List<CompositeServiceLevelObjective> compositeServiceLevelObjectives =
          compositeSLOService.getReferencedCompositeSLOs(projectParams, simpleServiceLevelObjective.getIdentifier());
      for (CompositeServiceLevelObjective compositeServiceLevelObjective : compositeServiceLevelObjectives) {
        String verificationTaskId = verificationTaskService.getCompositeSLOVerificationTaskId(
            serviceLevelIndicator.getAccountId(), compositeServiceLevelObjective.getUuid());
        orchestrationService.queueAnalysis(AnalysisInput.builder()
                                               .verificationTaskId(verificationTaskId)
                                               .startTime(analysisState.getInputs().getStartTime())
                                               .endTime(Instant.now())
                                               .isSLORestoreTask(true)
                                               .build());
      }
    }
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(SLIMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleRetry(SLIMetricAnalysisState analysisState) {
    return analysisState;
  }
}
