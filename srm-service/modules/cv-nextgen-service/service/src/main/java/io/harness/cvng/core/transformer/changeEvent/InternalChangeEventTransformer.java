/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.InternalChangeActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.InternalChangeEventMetaData;

import java.time.Instant;

public class InternalChangeEventTransformer
    extends ChangeEventMetaDataTransformer<InternalChangeActivity, InternalChangeEventMetaData> {
  @Override
  public InternalChangeActivity getEntity(ChangeEventDTO changeEventDTO) {
    InternalChangeEventMetaData internalChangeEventMetaData =
        (InternalChangeEventMetaData) changeEventDTO.getMetadata();
    return InternalChangeActivity.builder()
        .type(internalChangeEventMetaData.getActivityType())
        .activityType(internalChangeEventMetaData.getActivityType())
        .internalChangeEvent(internalChangeEventMetaData.getInternalChangeEvent())
        .updatedBy(internalChangeEventMetaData.getUpdatedBy())
        .eventEndTime(internalChangeEventMetaData.getEventEndTime())
        .eventTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .activityStartTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .accountId(changeEventDTO.getAccountId())
        .orgIdentifier(changeEventDTO.getOrgIdentifier())
        .projectIdentifier(changeEventDTO.getProjectIdentifier())
        .changeSourceIdentifier(changeEventDTO.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
        .activityName(changeEventDTO.getName())
        .pipelineId(internalChangeEventMetaData.getPipelineId())
        .stageId(internalChangeEventMetaData.getStageId())
        .stageStepId(internalChangeEventMetaData.getStageStepId())
        .planExecutionId(internalChangeEventMetaData.getPlanExecutionId())
        .build();
  }

  @Override
  protected InternalChangeEventMetaData getMetadata(InternalChangeActivity activity) {
    return InternalChangeEventMetaData.builder()
        .internalChangeEvent(activity.getInternalChangeEvent())
        .updatedBy(activity.getUpdatedBy())
        .activityType(activity.getActivityType())
        .eventStartTime(activity.getEventTime().toEpochMilli())
        .eventEndTime(activity.getEventEndTime())
        .build();
  }
}
