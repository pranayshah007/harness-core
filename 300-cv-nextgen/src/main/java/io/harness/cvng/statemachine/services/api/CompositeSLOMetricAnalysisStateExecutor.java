/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.CompositeSLOMetricAnalysisState;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeSLOMetricAnalysisStateExecutor extends AnalysisStateExecutor<CompositeSLOMetricAnalysisState> {
  @Inject private SLIRecordService sliRecordService;

  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;

  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private CompositeSLORecordService compositeSLORecordService;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject private VerificationTaskService verificationTaskService;
  @Override
  public AnalysisState execute(CompositeSLOMetricAnalysisState analysisState) {
    Instant startTime = analysisState.getInputs().getStartTime();
    Instant endTime = analysisState.getInputs().getEndTime();
    String verificationTaskId = analysisState.getInputs().getVerificationTaskId();
    // here startTime will be the prv Data endTime and endTime will be the current time.
    String sloId = verificationTaskService.getCompositeSLOId(verificationTaskId);
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.get(sloId);
    Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailSLIRecordMap = new HashMap<>();
    Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap = new HashMap<>();
    for (ServiceLevelObjectivesDetail objectivesDetail :
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails()) {
      ProjectParams projectParams = ProjectParams.builder()
                                        .projectIdentifier(objectivesDetail.getProjectIdentifier())
                                        .orgIdentifier(objectivesDetail.getOrgIdentifier())
                                        .accountIdentifier(objectivesDetail.getAccountId())
                                        .build();
      SimpleServiceLevelObjective simpleServiceLevelObjective =
          (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
              projectParams, objectivesDetail.getServiceLevelObjectiveRef());
      Preconditions.checkState(simpleServiceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
          ProjectParams.builder()
              .accountIdentifier(simpleServiceLevelObjective.getAccountId())
              .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
              .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
              .build(),
          simpleServiceLevelObjective.getServiceLevelIndicators().get(0));
      String sliId = serviceLevelIndicator.getUuid();
      List<SLIRecord> sliRecords = sliRecordService.getSLIRecords(sliId, startTime, endTime);
      serviceLevelObjectivesDetailSLIRecordMap.put(objectivesDetail, sliRecords);
      objectivesDetailSLIMissingDataTypeMap.put(objectivesDetail, serviceLevelIndicator.getSliMissingDataType());
    }
    if (serviceLevelObjectivesDetailSLIRecordMap.size()
        == compositeServiceLevelObjective.getServiceLevelObjectivesDetails().size()) {
      compositeSLORecordService.create(serviceLevelObjectivesDetailSLIRecordMap, objectivesDetailSLIMissingDataTypeMap,
          compositeServiceLevelObjective.getVersion(), verificationTaskId, startTime, endTime);
      sloHealthIndicatorService.upsert(compositeServiceLevelObjective);
    }
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState.getStatus();
  }

  @Override
  public AnalysisState handleRerun(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleRunning(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(CompositeSLOMetricAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleRetry(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState;
  }
}
