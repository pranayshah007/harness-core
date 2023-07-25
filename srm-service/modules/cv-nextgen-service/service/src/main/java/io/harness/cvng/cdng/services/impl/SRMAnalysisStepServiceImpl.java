/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.entities.SRMAnalysisStepDetailDTO;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail.SRMAnalysisStepExecutionDetailsKeys;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.SRMAnalysisStatus;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;

public class SRMAnalysisStepServiceImpl implements SRMAnalysisStepService {
  @Inject HPersistence hPersistence;

  @Inject Clock clock;

  @Inject ActivityService activityService;
  @Override
  public String createSRMAnalysisStepExecution(Ambiance ambiance, String monitoredServiceIdentifier,
      ServiceEnvironmentParams serviceEnvironmentParams, Duration duration) {
    Instant instant = clock.instant();
    SRMAnalysisStepExecutionDetail executionDetails =
        SRMAnalysisStepExecutionDetail.builder()
            .stageId(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getIdentifier())
            .stageStepId(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getSetupId())
            .pipelineId(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
            .accountId(serviceEnvironmentParams.getAccountIdentifier())
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .analysisStartTime(instant.toEpochMilli())
            .analysisStatus(SRMAnalysisStatus.RUNNING)
            .analysisEndTime(instant.plus(duration).toEpochMilli())
            .analysisDuration(duration)
            .build();
    return hPersistence.save(executionDetails);
  }

  @Nullable
  @Override
  public SRMAnalysisStepExecutionDetail getSRMAnalysisStepExecutionDetail(String analysisStepExecutionDetailId) {
    return hPersistence.get(SRMAnalysisStepExecutionDetail.class, analysisStepExecutionDetailId);
  }

  @Override
  public void abortRunningStepsForMonitoredService(ProjectParams projectParams, String monitoredServiceIdentifier) {
    Query<SRMAnalysisStepExecutionDetail> updateQuery =
        hPersistence.createQuery(SRMAnalysisStepExecutionDetail.class)
            .filter(SRMAnalysisStepExecutionDetailsKeys.accountId, projectParams.getAccountIdentifier())
            .filter(SRMAnalysisStepExecutionDetailsKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(SRMAnalysisStepExecutionDetailsKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(SRMAnalysisStepExecutionDetailsKeys.monitoredServiceIdentifier, monitoredServiceIdentifier)
            .filter(SRMAnalysisStepExecutionDetailsKeys.analysisStatus, SRMAnalysisStatus.RUNNING);
    UpdateOperations<SRMAnalysisStepExecutionDetail> updateOperations =
        hPersistence.createUpdateOperations(SRMAnalysisStepExecutionDetail.class)
            .set(SRMAnalysisStepExecutionDetailsKeys.analysisStatus, SRMAnalysisStatus.ABORTED)
            .set(SRMAnalysisStepExecutionDetailsKeys.analysisEndTime, clock.millis());
    hPersistence.update(updateQuery, updateOperations);
  }

  @Override
  public SRMAnalysisStepDetailDTO abortRunningSrmAnalysisStep(String executionDetailId) {
    SRMAnalysisStepExecutionDetail stepExecutionDetail = getSRMAnalysisStepExecutionDetail(executionDetailId);

    Preconditions.checkArgument(
        !stepExecutionDetail.equals(null), String.format("Step Execution Id %s is not present.", executionDetailId));
    Preconditions.checkArgument(stepExecutionDetail.getAnalysisStatus().equals(SRMAnalysisStatus.RUNNING),
        String.format("Step Execution Id %s is not RUNNING, the current status is %s", executionDetailId,
            stepExecutionDetail.getAnalysisStatus()));

    UpdateOperations<SRMAnalysisStepExecutionDetail> updateOperations =
        hPersistence.createUpdateOperations(SRMAnalysisStepExecutionDetail.class)
            .set(SRMAnalysisStepExecutionDetailsKeys.analysisStatus, SRMAnalysisStatus.ABORTED)
            .set(SRMAnalysisStepExecutionDetailsKeys.analysisEndTime, clock.millis());
    hPersistence.update(stepExecutionDetail, updateOperations);
    return SRMAnalysisStepDetailDTO.getDTOFromEntity(getSRMAnalysisStepExecutionDetail(executionDetailId));
  }

  @Override
  public SRMAnalysisStepDetailDTO getSRMAnalysisSummary(String activityId) {
    Activity activity = activityService.get(activityId);
    Preconditions.checkArgument(!activity.equals(null), String.format("Activity Id %s is not present.", activityId));
    Preconditions.checkArgument(activity.getType().equals(ActivityType.SRM_STEP_ANALYSIS),
        String.format("Activity is not of the type SRM_STEP_ANALYSIS, the type is %s", activity.getType()));
    SRMStepAnalysisActivity stepAnalysisActivity = (SRMStepAnalysisActivity) activity;

    SRMAnalysisStepExecutionDetail stepExecutionDetail =
        getSRMAnalysisStepExecutionDetail(stepAnalysisActivity.getExecutionNotificationDetailsId());
    Preconditions.checkArgument(!stepExecutionDetail.equals(null),
        String.format(
            "Step Execution Details %s is not present.", stepAnalysisActivity.getExecutionNotificationDetailsId()));
    return SRMAnalysisStepDetailDTO.getDTOFromEntity(stepExecutionDetail);
  }
}
