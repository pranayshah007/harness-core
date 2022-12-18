/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.HarnessCDCurrentGenActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;

import java.time.Instant;

public class HarnessCDCurrentGenChangeEventTransformer
    extends ChangeEventMetaDataTransformer<HarnessCDCurrentGenActivity, HarnessCDCurrentGenEventMetadata> {
  @Override
  public HarnessCDCurrentGenActivity getEntity(ChangeEventDTO changeEventDTO) {
    HarnessCDCurrentGenEventMetadata metaData = (HarnessCDCurrentGenEventMetadata) changeEventDTO.getMetadata();
    return HarnessCDCurrentGenActivity.builder()
        .accountId(changeEventDTO.getAccountId())
        .activityName("Deployment for " + changeEventDTO.getMonitoredServiceIdentifier())
        .orgIdentifier(changeEventDTO.getOrgIdentifier())
        .projectIdentifier(changeEventDTO.getProjectIdentifier())
        .eventTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .activityStartTime(Instant.ofEpochMilli(metaData.getWorkflowStartTime()))
        .workflowEndTime(Instant.ofEpochMilli(metaData.getWorkflowEndTime()))
        .changeSourceIdentifier(changeEventDTO.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
        .type(changeEventDTO.getType().getActivityType())
        .serviceId(metaData.getServiceId())
        .appId(metaData.getAppId())
        .environmentId(metaData.getEnvironmentId())
        .workflowId(metaData.getWorkflowId())
        .workflowExecutionId(metaData.getWorkflowExecutionId())
        .workflowStartTime(Instant.ofEpochMilli(metaData.getWorkflowStartTime()))
        .workflowEndTime(Instant.ofEpochMilli(metaData.getWorkflowEndTime()))
        .name(metaData.getName())
        .artifactType(metaData.getArtifactType())
        .artifactName(metaData.getArtifactName())
        .status(metaData.getStatus())
        .build();
  }

  @Override
  protected HarnessCDCurrentGenEventMetadata getMetadata(HarnessCDCurrentGenActivity activity) {
    return HarnessCDCurrentGenEventMetadata.builder()
        .accountId(activity.getAccountId())
        .appId(activity.getAppId())
        .serviceId(activity.getServiceId())
        .environmentId(activity.getServiceId())
        .workflowId(activity.getWorkflowId())
        .workflowExecutionId(activity.getWorkflowExecutionId())
        .workflowStartTime(activity.getWorkflowStartTime().toEpochMilli())
        .workflowEndTime(activity.getWorkflowEndTime().toEpochMilli())
        .name(activity.getName())
        .artifactType(activity.getArtifactType())
        .artifactName(activity.getArtifactName())
        .status(activity.getStatus())
        .build();
  }
}
