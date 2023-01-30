/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.CustomChangeActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.CustomChangeEventMetadata;

import java.time.Instant;

public class CustomChangeEventTransformer
    extends ChangeEventMetaDataTransformer<CustomChangeActivity, CustomChangeEventMetadata> {
  @Override
  public CustomChangeActivity getEntity(ChangeEventDTO changeEventDTO) {
    CustomChangeEventMetadata customChangeEventMetadata = (CustomChangeEventMetadata) changeEventDTO.getMetadata();
    return CustomChangeActivity.builder()
        .activitySourceId(changeEventDTO.getId())
        .activityType(changeEventDTO.getType().getActivityType())
        .type(changeEventDTO.getType().getActivityType())
        .user(customChangeEventMetadata.getUser())
        .endTime(customChangeEventMetadata.getEndTime())
        .customChangeEvent(customChangeEventMetadata.getCustomChangeEvent())
        .eventTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .activityStartTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .activityEndTime(Instant.ofEpochMilli(customChangeEventMetadata.getEndTime()))
        .accountId(changeEventDTO.getAccountId())
        .orgIdentifier(changeEventDTO.getOrgIdentifier())
        .projectIdentifier(changeEventDTO.getProjectIdentifier())
        .changeSourceIdentifier(changeEventDTO.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
        .activityName(changeEventDTO.getName())
        .build();
  }

  @Override
  protected CustomChangeEventMetadata getMetadata(CustomChangeActivity activity) {
    return CustomChangeEventMetadata.builder()
        .startTime(activity.getEventTime().toEpochMilli())
        .endTime(activity.getEndTime())
        .user(activity.getUser())
        .customChangeEvent(activity.getCustomChangeEvent())
        .type(ChangeSourceType.ofActivityType(activity.getActivityType()))
        .build();
  }
}
