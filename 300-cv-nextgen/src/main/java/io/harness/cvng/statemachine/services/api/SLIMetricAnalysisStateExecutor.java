package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLIMetricAnalysisTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.SLIMetricAnalysisState;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIMetricAnalysisStateExecutor extends AnalysisStateExecutor<SLIMetricAnalysisState> {
  @Inject private SLIDataProcessorService sliDataProcessorService;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject private VerificationTaskService verificationTaskService;

  @Inject private SLIRecordService sliRecordService;

  @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;

  @Inject private TimeSeriesRecordService timeSeriesRecordService;

  @Inject private SLIMetricAnalysisTransformer sliMetricAnalysisTransformer;

  @Override
  public AnalysisState execute(SLIMetricAnalysisState analysisState) {
    Instant startTime = analysisState.getInputs().getStartTime();
    Instant endTime = analysisState.getInputs().getEndTime();
    String verificationTaskId = analysisState.getInputs().getVerificationTaskId();
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.get(verificationTaskService.getSliId(verificationTaskId));
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOS =
        timeSeriesRecordService.getTimeSeriesRecordDTOs(verificationTaskId, startTime, endTime);
    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequest =
        sliMetricAnalysisTransformer.getSLIAnalyseRequest(timeSeriesRecordDTOS);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        serviceLevelIndicatorEntityAndDTOTransformer.getDto(serviceLevelIndicator);
    List<SLIAnalyseResponse> sliAnalyseResponseList = sliDataProcessorService.process(
        sliAnalyseRequest, serviceLevelIndicatorDTO.getSpec().getSpec(), startTime, endTime);
    List<SLIRecordParam> sliRecordList = sliMetricAnalysisTransformer.getSLIAnalyseResponse(sliAnalyseResponseList);

    sliRecordService.create(sliRecordList, serviceLevelIndicator.getUuid(), verificationTaskId);
    analysisState.setStatus(AnalysisStatus.SUCCESS);
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
