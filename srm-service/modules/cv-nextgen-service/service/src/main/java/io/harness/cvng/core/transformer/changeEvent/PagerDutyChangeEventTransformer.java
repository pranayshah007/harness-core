/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.PagerDutyActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.PagerDutyEventMetaData;

import java.time.Instant;

public class PagerDutyChangeEventTransformer
    extends ChangeEventMetaDataTransformer<PagerDutyActivity, PagerDutyEventMetaData> {
  @Override
  public PagerDutyActivity getEntity(ChangeEventDTO changeEventDTO) {
    PagerDutyEventMetaData pagerDutyEventMetaData = (PagerDutyEventMetaData) changeEventDTO.getMetadata();
    return PagerDutyActivity.builder()
        .accountId(changeEventDTO.getAccountId())
        .activityName(pagerDutyEventMetaData.getTitle())
        .orgIdentifier(changeEventDTO.getOrgIdentifier())
        .projectIdentifier(changeEventDTO.getProjectIdentifier())
        .eventTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .activityStartTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .changeSourceIdentifier(changeEventDTO.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
        .type(changeEventDTO.getType().getActivityType())
        .pagerDutyUrl(pagerDutyEventMetaData.getPagerDutyUrl())
        .eventId(pagerDutyEventMetaData.getEventId())
        .status(pagerDutyEventMetaData.getStatus())
        .triggeredAt(pagerDutyEventMetaData.getTriggeredAt())
        .urgency(pagerDutyEventMetaData.getUrgency())
        .htmlUrl(pagerDutyEventMetaData.getHtmlUrl())
        .priority(pagerDutyEventMetaData.getPriority())
        .assignment(pagerDutyEventMetaData.getAssignment())
        .assignmentUrl(pagerDutyEventMetaData.getAssignmentUrl())
        .escalationPolicy(pagerDutyEventMetaData.getEscalationPolicy())
        .escalationPolicyUrl(pagerDutyEventMetaData.getEscalationPolicyUrl())
        .build();
  }

  @Override
  protected PagerDutyEventMetaData getMetadata(PagerDutyActivity activity) {
    return PagerDutyEventMetaData.builder()
        .pagerDutyUrl(activity.getPagerDutyUrl())
        .eventId(activity.getEventId())
        .title(activity.getActivityName())
        .status(activity.getStatus())
        .triggeredAt(activity.getTriggeredAt())
        .urgency(activity.getUrgency())
        .htmlUrl(activity.getHtmlUrl())
        .priority(activity.getPriority())
        .assignment(activity.getAssignment())
        .assignmentUrl(activity.getAssignmentUrl())
        .escalationPolicy(activity.getEscalationPolicy())
        .escalationPolicyUrl(activity.getEscalationPolicyUrl())
        .build();
  }
}
