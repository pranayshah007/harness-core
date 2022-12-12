/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.HarnessCDEventMetadata;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.deployment.DeploymentEventDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DeploymentChangeEventConsumerTest extends CvNextGenTestBase {
  @Inject DeploymentChangeEventConsumer deploymentChangeEventConsumer;

  @Mock ChangeEventService changeEventService;

  BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    FieldUtils.writeField(deploymentChangeEventConsumer, "changeEventService", changeEventService, true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testProcessMessage() throws InterruptedException {
    DeploymentEventDTO deploymentEventDTO = builderFactory.getDeploymentEventDTOBuilder().build();
    final ArgumentCaptor<ChangeEventDTO> captor = ArgumentCaptor.forClass(ChangeEventDTO.class);
    deploymentChangeEventConsumer.processMessage(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .setData(deploymentEventDTO.toByteString())
                            .build())
            .build());
    Mockito.verify(changeEventService).register(captor.capture());
    ChangeEventDTO changeEventDTO = captor.getValue();
    assertThat(changeEventDTO.getAccountId()).isEqualTo(deploymentEventDTO.getAccountId());
    assertThat(changeEventDTO.getOrgIdentifier()).isEqualTo(deploymentEventDTO.getOrgIdentifier());
    assertThat(changeEventDTO.getProjectIdentifier()).isEqualTo(deploymentEventDTO.getProjectIdentifier());
    assertThat(changeEventDTO.getServiceIdentifier()).isEqualTo(deploymentEventDTO.getServiceIdentifier());
    assertThat(changeEventDTO.getEnvIdentifier()).isEqualTo(deploymentEventDTO.getEnvironmentIdentifier());
    assertThat(changeEventDTO.getEventTime()).isEqualTo(deploymentEventDTO.getDeploymentEndTime());
    assertThat(changeEventDTO.getType()).isEqualTo(ChangeSourceType.HARNESS_CD);

    HarnessCDEventMetadata harnessCDEventMetaData = (HarnessCDEventMetadata) changeEventDTO.getMetadata();
    assertThat(harnessCDEventMetaData.getDeploymentEndTime()).isEqualTo(deploymentEventDTO.getDeploymentEndTime());
    assertThat(harnessCDEventMetaData.getDeploymentStartTime()).isEqualTo(deploymentEventDTO.getDeploymentStartTime());
    assertThat(harnessCDEventMetaData.getStatus()).isEqualTo(deploymentEventDTO.getDeploymentStatus());
    assertThat(harnessCDEventMetaData.getPipelineId())
        .isEqualTo(deploymentEventDTO.getExecutionDetails().getPipelineId());
    assertThat(harnessCDEventMetaData.getStageId()).isEqualTo(deploymentEventDTO.getExecutionDetails().getStageId());
    assertThat(harnessCDEventMetaData.getStageStepId())
        .isEqualTo(deploymentEventDTO.getExecutionDetails().getStageSetupId());
    assertThat(harnessCDEventMetaData.getPlanExecutionId())
        .isEqualTo(deploymentEventDTO.getExecutionDetails().getPlanExecutionId());
    assertThat(harnessCDEventMetaData.getArtifactTag())
        .isEqualTo(deploymentEventDTO.getArtifactDetails().getArtifactTag());
    assertThat(harnessCDEventMetaData.getArtifactType())
        .isEqualTo(deploymentEventDTO.getArtifactDetails().getArtifactType());
  }
}
