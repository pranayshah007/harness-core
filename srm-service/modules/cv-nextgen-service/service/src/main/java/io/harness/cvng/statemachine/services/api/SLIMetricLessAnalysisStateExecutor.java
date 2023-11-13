/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.entities.ErrorBudgetBurnDown;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ErrorBudgetBurnDownService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLIMetricAnalysisTransformer;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.SLIMetricLessAnalysisState;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIMetricLessAnalysisStateExecutor extends AnalysisStateExecutor<SLIMetricLessAnalysisState> {
  @Inject private Clock clock;
  @Inject private ErrorBudgetBurnDownService errorBudgetBurnDownService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private SLIDataProcessorService sliDataProcessorService;
  @Inject private SLIMetricAnalysisTransformer sliMetricAnalysisTransformer;
  @Inject private SLIRecordService sliRecordService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;

  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Override
  public AnalysisState execute(SLIMetricLessAnalysisState analysisState) {
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
    List<ErrorBudgetBurnDown> errorBudgetBurnDowns = errorBudgetBurnDownService.getByStartTimeAndEndTime(
        projectParams, serviceLevelIndicator.getIdentifier(), startTime.toEpochMilli(), endTime.toEpochMilli());
    List<SLIAnalyseResponse> sliAnalyseResponseList =
        sliDataProcessorService.process(errorBudgetBurnDowns, serviceLevelIndicator, startTime, endTime);
    List<SLIRecordParam> sliRecordList = sliMetricAnalysisTransformer.getSLIAnalyseResponse(sliAnalyseResponseList);
    sliRecordService.create(
        sliRecordList, serviceLevelIndicator.getUuid(), verificationTaskId, serviceLevelIndicator.getVersion());
    SimpleServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getFromSLIIdentifier(projectParams, serviceLevelIndicator.getIdentifier());
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(SLIMetricLessAnalysisState analysisState) {
    return analysisState.getStatus();
  }

  @Override
  public AnalysisState handleRerun(SLIMetricLessAnalysisState analysisState) {
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    analysisState.setStatus(AnalysisStatus.RETRY);
    return execute(analysisState);
  }

  @Override
  public AnalysisState handleRunning(SLIMetricLessAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(SLIMetricLessAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    handleFinalStatuses(analysisState);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(SLIMetricLessAnalysisState analysisState) {
    return analysisState;
  }
}
