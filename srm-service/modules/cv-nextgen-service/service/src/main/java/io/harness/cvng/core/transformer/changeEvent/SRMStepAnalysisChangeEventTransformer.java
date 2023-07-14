/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.HarnessSRMAnalysisEventMetadata;
import io.harness.cvng.beans.change.HarnessSRMAnalysisEventMetadata.HarnessSRMAnalysisEventMetadataBuilder;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.entities.MonitoredService;

import com.google.inject.Inject;
import java.time.Instant;

public class SRMStepAnalysisChangeEventTransformer
    extends ChangeEventMetaDataTransformer<SRMStepAnalysisActivity, HarnessSRMAnalysisEventMetadata> {
  @Inject SRMAnalysisStepService srmAnalysisStepService;
  @Override
  public SRMStepAnalysisActivity getEntity(ChangeEventDTO changeEventDTO) {
    HarnessSRMAnalysisEventMetadata metaData = (HarnessSRMAnalysisEventMetadata) changeEventDTO.getMetadata();
    return SRMStepAnalysisActivity.builder()
        .accountId(changeEventDTO.getAccountId())
        .activityName("SRM Step Analysis of "
            + MonitoredService.getIdentifier(changeEventDTO.getServiceIdentifier(), changeEventDTO.getEnvIdentifier()))
        .orgIdentifier(changeEventDTO.getOrgIdentifier())
        .projectIdentifier(changeEventDTO.getProjectIdentifier())
        .eventTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .changeSourceIdentifier(changeEventDTO.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
        .type(changeEventDTO.getType().getActivityType())
        .planExecutionId(metaData.getPlanExecutionId())
        .stageStepId(metaData.getStageStepId())
        .pipelineId(metaData.getPipelineId())
        .stageId(metaData.getStageId())
        .planExecutionId(metaData.getPlanExecutionId())
        .artifactTag(metaData.getArtifactTag())
        .artifactType(metaData.getArtifactType())
        .executionNotificationDetailsId(metaData.getExecutionNotificationDetailsId())
        .build();
  }

  @Override
  protected HarnessSRMAnalysisEventMetadata getMetadata(SRMStepAnalysisActivity activity) {
    SRMAnalysisStepExecutionDetail executionDetails =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(activity.getExecutionNotificationDetailsId());
    HarnessSRMAnalysisEventMetadataBuilder harnessCDEventMetadataBuilder =
        HarnessSRMAnalysisEventMetadata.builder()
            .stageId(activity.getStageId())
            .planExecutionId(activity.getPlanExecutionId())
            .stageStepId(activity.getStageStepId())
            .pipelineId(activity.getPipelineId())
            .stageId(activity.getStageId())
            .artifactTag(activity.getArtifactTag())
            .pipelinePath("/account/" + activity.getAccountId() + "/cd/orgs/" + activity.getOrgIdentifier()
                + "/projects/" + activity.getProjectIdentifier() + "/pipelines/" + activity.getPipelineId()
                + "/executions/" + activity.getPlanExecutionId() + "/pipeline?stage=" + activity.getStageStepId())
            .artifactType(activity.getArtifactType())
            .analysisStartTime(executionDetails.getAnalysisStartTime())
            .analysisEndTime(executionDetails.getAnalysisEndTime())
            .analysisStatus(executionDetails.getAnalysisStatus());
    return harnessCDEventMetadataBuilder.build();
  }
}
