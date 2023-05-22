/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.beans.CompositeSLODebugResponse;
import io.harness.cvng.core.beans.SLODebugResponse;
import io.harness.cvng.core.beans.VerifyStepDebugResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVNGLog;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.jobs.FakeFeatureFlagSRMProducer;
import io.harness.cvng.core.services.DebugConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.DebugService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DebugServiceImpl implements DebugService {
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject DataCollectionTaskService dataCollectionTaskService;
  @Inject SLIRecordService sliRecordService;
  @Inject CompositeSLORecordService sloRecordService;
  @Inject AnalysisStateMachineService analysisStateMachineService;
  @Inject CVNGStepTaskService cvngStepTaskService;
  @Inject VerificationJobInstanceService verificationJobInstanceService;
  @Inject ActivityService activityService;
  @Inject CVNGLogService cvngLogService;
  @Inject OrchestrationService orchestrationService;

  @Inject private FakeFeatureFlagSRMProducer fakeFeatureFlagSRMProducer;
  @Inject ChangeEventService changeEventService;

  @Inject DebugConfigService debugConfigService;
  public static final Integer RECORDS_BATCH_SIZE = 100;

  @Override
  public SLODebugResponse getSLODebugResponse(ProjectParams projectParams, String identifier) {
    AbstractServiceLevelObjective abstractServiceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, identifier);
    Preconditions.checkNotNull(abstractServiceLevelObjective, "Value of Identifier is not present in database");

    Preconditions.checkArgument(abstractServiceLevelObjective.getType().equals(ServiceLevelObjectiveType.SIMPLE));

    SimpleServiceLevelObjective simpleServiceLevelObjective =
        (SimpleServiceLevelObjective) abstractServiceLevelObjective;
    List<ServiceLevelIndicator> serviceLevelIndicatorList = serviceLevelIndicatorService.getEntities(
        projectParams, simpleServiceLevelObjective.getServiceLevelIndicators());

    SLOHealthIndicator sloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, abstractServiceLevelObjective.getIdentifier());

    Map<String, VerificationTask> sliIdentifierToVerificationTaskMap = new HashMap<>();

    Map<String, List<DataCollectionTask>> sliIdentifierToDataCollectionTaskMap = new HashMap<>();

    Map<String, AnalysisStateMachine> sliIdentifierToAnalysisStateMachineMap = new HashMap<>();

    Map<String, List<SLIRecord>> sliIdentifierToSLIRecordMap = new HashMap<>();

    for (ServiceLevelIndicator serviceLevelIndicator : serviceLevelIndicatorList) {
      sliIdentifierToVerificationTaskMap.put(serviceLevelIndicator.getIdentifier(),
          verificationTaskService.getSLITask(projectParams.getAccountIdentifier(), serviceLevelIndicator.getUuid()));

      sliIdentifierToDataCollectionTaskMap.put(serviceLevelIndicator.getIdentifier(),
          dataCollectionTaskService.getLatestDataCollectionTasks(
              projectParams.getAccountIdentifier(), serviceLevelIndicator.getUuid(), 3));

      sliIdentifierToSLIRecordMap.put(serviceLevelIndicator.getIdentifier(),
          sliRecordService.getLatestCountSLIRecords(serviceLevelIndicator.getUuid(), 100));

      sliIdentifierToAnalysisStateMachineMap.put(serviceLevelIndicator.getIdentifier(),
          analysisStateMachineService.getExecutingStateMachine(verificationTaskService.getSLIVerificationTaskId(
              projectParams.getAccountIdentifier(), serviceLevelIndicator.getUuid())));
    }

    return SLODebugResponse.builder()
        .projectParams(projectParams)
        .simpleServiceLevelObjective(simpleServiceLevelObjective)
        .serviceLevelIndicatorList(serviceLevelIndicatorList)
        .sloHealthIndicator(sloHealthIndicator)
        .sliIdentifierToVerificationTaskMap(sliIdentifierToVerificationTaskMap)
        .sliIdentifierToDataCollectionTaskMap(sliIdentifierToDataCollectionTaskMap)
        .sliIdentifierToSLIRecordMap(sliIdentifierToSLIRecordMap)
        .sliIdentifierToAnalysisStateMachineMap(sliIdentifierToAnalysisStateMachineMap)
        .build();
  }

  @Override
  public boolean forceDeleteSLO(ProjectParams projectParams, String sloIdentifier) {
    if (!debugConfigService.isDebugEnabled()) {
      throw new RuntimeException("Debug Mode is turned off");
    }
    return serviceLevelObjectiveV2Service.forceDelete(projectParams, sloIdentifier);
  }

  @Override
  public VerifyStepDebugResponse getVerifyStepDebugResponse(ProjectParams projectParams, String callBackId) {
    CVNGStepTask cvngStepTask = cvngStepTaskService.getByCallBackId(callBackId);
    Activity activity = activityService.get(cvngStepTask.getActivityId());
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(cvngStepTask.getVerificationJobInstanceId());
    Set<String> verificationTaskIds = verificationTaskService.getVerificationTaskIds(
        projectParams.getAccountIdentifier(), cvngStepTask.getVerificationJobInstanceId());

    List<VerificationTask> verificationTaskList = new ArrayList<>();
    Map<String, List<DataCollectionTask>> verificationTaskIdToDataCollectionTaskMap = new HashMap<>();
    Map<String, AnalysisStateMachine> verificationTaskIdToAnalysisStateMachineMap = new HashMap<>();
    Map<String, List<CVNGLog>> verificationTaskIdToCVNGApiLogMap = new HashMap<>();
    Map<String, List<CVNGLog>> verificationTaskIdToCVNGExecutionLogMap = new HashMap<>();

    for (String verificationTaskId : verificationTaskIds) {
      verificationTaskList.add(verificationTaskService.get(verificationTaskId));
      verificationTaskIdToDataCollectionTaskMap.put(verificationTaskId,
          dataCollectionTaskService.getAllDataCollectionTasks(
              projectParams.getAccountIdentifier(), verificationTaskId));
      verificationTaskIdToAnalysisStateMachineMap.put(
          verificationTaskId, analysisStateMachineService.getExecutingStateMachine(verificationTaskId));
      verificationTaskIdToCVNGApiLogMap.put(verificationTaskId,
          cvngLogService.getCompleteCVNGLog(
              projectParams.getAccountIdentifier(), verificationTaskId, CVNGLogType.API_CALL_LOG));
      verificationTaskIdToCVNGExecutionLogMap.put(verificationTaskId,
          cvngLogService.getCompleteCVNGLog(
              projectParams.getAccountIdentifier(), verificationTaskId, CVNGLogType.EXECUTION_LOG));
    }

    return VerifyStepDebugResponse.builder()
        .projectParams(projectParams)
        .cvngStepTask(cvngStepTask)
        .activity(activity)
        .verificationJobInstance(verificationJobInstance)
        .verificationTaskList(verificationTaskList)
        .verificationTaskIdToDataCollectionTaskMap(verificationTaskIdToDataCollectionTaskMap)
        .verificationTaskIdToAnalysisStateMachineMap(verificationTaskIdToAnalysisStateMachineMap)
        .verificationTaskIdToCVNGApiLogMap(verificationTaskIdToCVNGApiLogMap)
        .verificationTaskIdToCVNGExecutionLogMap(verificationTaskIdToCVNGExecutionLogMap)
        .build();
  }

  @Override
  public CompositeSLODebugResponse getCompositeSLODebugResponse(ProjectParams projectParams, String identifier) {
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, identifier);

    Preconditions.checkNotNull(serviceLevelObjective, "Value of Identifier is not present in database");
    Preconditions.checkArgument(serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.COMPOSITE));

    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjective;

    List<SimpleServiceLevelObjective> simpleServiceLevelObjectives = new ArrayList<>();
    List<ServiceLevelIndicator> serviceLevelIndicators = new ArrayList<>();
    Map<String, List<SLIRecord>> sliIdentifierToSLIRecordsMap = new HashMap<>();

    for (CompositeServiceLevelObjective.ServiceLevelObjectivesDetail objectivesDetail :
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails()) {
      ProjectParams projectParamsForSimpleSLO = ProjectParams.builder()
                                                    .projectIdentifier(objectivesDetail.getProjectIdentifier())
                                                    .orgIdentifier(objectivesDetail.getOrgIdentifier())
                                                    .accountIdentifier(objectivesDetail.getAccountId())
                                                    .build();
      SimpleServiceLevelObjective simpleServiceLevelObjective =
          (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
              projectParamsForSimpleSLO, objectivesDetail.getServiceLevelObjectiveRef());
      Preconditions.checkState(simpleServiceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
          ProjectParams.builder()
              .accountIdentifier(simpleServiceLevelObjective.getAccountId())
              .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
              .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
              .build(),
          simpleServiceLevelObjective.getServiceLevelIndicators().get(0));
      List<SLIRecord> sliRecords =
          sliRecordService.getLatestCountSLIRecords(serviceLevelIndicator.getUuid(), RECORDS_BATCH_SIZE);
      simpleServiceLevelObjectives.add(simpleServiceLevelObjective);
      serviceLevelIndicators.add(serviceLevelIndicator);
      sliIdentifierToSLIRecordsMap.put(serviceLevelIndicator.getUuid(), sliRecords);
    }

    VerificationTask verificationTask = verificationTaskService.getCompositeSLOTask(
        projectParams.getAccountIdentifier(), compositeServiceLevelObjective.getUuid());

    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(projectParams, identifier);

    AnalysisStateMachine analysisStateMachine =
        analysisStateMachineService.getExecutingStateMachine(verificationTask.getUuid());

    AnalysisOrchestrator analysisOrchestrator =
        orchestrationService.getAnalysisOrchestrator(verificationTask.getUuid());

    List<CompositeSLORecord> sloRecords =
        sloRecordService.getLatestCountSLORecords(compositeServiceLevelObjective.getUuid(), RECORDS_BATCH_SIZE);

    return CompositeSLODebugResponse.builder()
        .projectParams(projectParams)
        .serviceLevelObjective(compositeServiceLevelObjective)
        .simpleServiceLevelObjectives(simpleServiceLevelObjectives)
        .serviceLevelIndicators(serviceLevelIndicators)
        .sliIdentifierToSLIRecordsMap(sliIdentifierToSLIRecordsMap)
        .sloHealthIndicator(sloHealthIndicator)
        .verificationTask(verificationTask)
        .analysisOrchestrator(analysisOrchestrator)
        .analysisStateMachine(analysisStateMachine)
        .sloRecords(sloRecords)
        .build();
  }

  public DataCollectionTask retryDataCollectionTask(ProjectParams projectParams, String identifier) {
    if (!debugConfigService.isDebugEnabled()) {
      throw new RuntimeException("Debug Mode is turned off");
    }
    return dataCollectionTaskService.updateRetry(projectParams, identifier);
  }

  @Override
  public boolean registerInternalChangeEvent(ProjectParams projectParams, ChangeEventDTO changeEventDTO) {
    if (!debugConfigService.isDebugEnabled()) {
      throw new RuntimeException("Debug Mode is turned off");
    }
    return changeEventService.register(changeEventDTO);
  }

  @Override
  public void registerFFChangeEvent(FakeFeatureFlagSRMProducer.FFEventBody ffEventBody) {
    if (!debugConfigService.isDebugEnabled()) {
      throw new RuntimeException("Debug Mode is turned off");
    }
    fakeFeatureFlagSRMProducer.publishEvent(ffEventBody);
  }
}
