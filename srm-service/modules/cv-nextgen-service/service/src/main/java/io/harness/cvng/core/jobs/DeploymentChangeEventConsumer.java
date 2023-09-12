/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.HarnessCDEventMetadata;
import io.harness.cvng.beans.change.HarnessCDEventMetadata.HarnessCDEventMetadataBuilder;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.deployment.DeploymentEventDTO;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DeploymentChangeEventConsumer extends AbstractStreamConsumer {
  private static final int MAX_WAIT_TIME_SEC = 10;
  ChangeEventService changeEventService;

  @Inject
  public DeploymentChangeEventConsumer(@Named(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT) Consumer consumer,
      QueueController queueController, ChangeEventService changeEventService) {
    super(MAX_WAIT_TIME_SEC, consumer, queueController);
    this.changeEventService = changeEventService;
  }

  @Override
  protected boolean processMessage(Message message) {
    DeploymentEventDTO deploymentEventDTO;
    try {
      deploymentEventDTO = DeploymentEventDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking DeploymentInfoDTO for key {}", message.getId(), e);
      throw new IllegalStateException(e);
    }
    registerChangeEvent(deploymentEventDTO);

    return true;
  }

  private void registerChangeEvent(DeploymentEventDTO deploymentEventDTO) {
    HarnessCDEventMetadataBuilder harnessCDEventMetaDataBuilder =
        HarnessCDEventMetadata.builder()
            .deploymentEndTime(deploymentEventDTO.getDeploymentEndTime())
            .deploymentStartTime(deploymentEventDTO.getDeploymentStartTime())
            .status(deploymentEventDTO.getDeploymentStatus());
    if (deploymentEventDTO.hasExecutionDetails()) {
      harnessCDEventMetaDataBuilder.stageStepId(deploymentEventDTO.getExecutionDetails().getStageSetupId());
      harnessCDEventMetaDataBuilder.planExecutionId(deploymentEventDTO.getExecutionDetails().getPlanExecutionId());
      harnessCDEventMetaDataBuilder.stageId(deploymentEventDTO.getExecutionDetails().getStageId());
      harnessCDEventMetaDataBuilder.pipelineId(deploymentEventDTO.getExecutionDetails().getPipelineId());
      harnessCDEventMetaDataBuilder.runSequence(deploymentEventDTO.getExecutionDetails().getRunSequence());
    }
    if (deploymentEventDTO.hasArtifactDetails()) {
      harnessCDEventMetaDataBuilder.artifactType(deploymentEventDTO.getArtifactDetails().getArtifactType());
      harnessCDEventMetaDataBuilder.artifactTag(deploymentEventDTO.getArtifactDetails().getArtifactTag());
    }
    ChangeEventDTO changeEventDTO = ChangeEventDTO.builder()
                                        .accountId(deploymentEventDTO.getAccountId())
                                        .orgIdentifier(deploymentEventDTO.getOrgIdentifier())
                                        .projectIdentifier(deploymentEventDTO.getProjectIdentifier())
                                        .serviceIdentifier(deploymentEventDTO.getServiceIdentifier())
                                        .envIdentifier(deploymentEventDTO.getEnvironmentIdentifier())
                                        .eventTime(deploymentEventDTO.getDeploymentEndTime())
                                        .type(ChangeSourceType.HARNESS_CD)
                                        .metadata(harnessCDEventMetaDataBuilder.build())
                                        .build();
    changeEventService.register(changeEventDTO);
  }
}
