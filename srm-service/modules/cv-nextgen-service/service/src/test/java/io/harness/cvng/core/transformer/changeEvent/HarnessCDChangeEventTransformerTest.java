/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.HarnessCDEventMetadata;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HarnessCDChangeEventTransformerTest extends CvNextGenTestBase {
  @Inject HarnessCDChangeEventTransformer harnessCDChangeEventTransformer;
  BuilderFactory builderFactory;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceService.createDefault(builderFactory.getProjectParams(),
        builderFactory.getContext().getServiceIdentifier(), builderFactory.getContext().getEnvIdentifier());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetEntity() {
    ChangeEventDTO changeEventDTO = builderFactory.harnessCDChangeEventDTOBuilder().build();
    DeploymentActivity harnessCDActivity = harnessCDChangeEventTransformer.getEntity(changeEventDTO);
    assertThat(harnessCDActivity.getActivityName())
        .isEqualTo("Deployment of " + builderFactory.getContext().getServiceIdentifier() + " in "
            + builderFactory.getContext().getEnvIdentifier());
    verifyEqual(harnessCDActivity, changeEventDTO);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetadata() {
    DeploymentActivity harnessCDActivity = builderFactory.getDeploymentActivityBuilder().build();
    ChangeEventDTO changeEventDTO = harnessCDChangeEventTransformer.getDTO(harnessCDActivity);
    verifyEqual(harnessCDActivity, changeEventDTO);
    assertThat(changeEventDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(changeEventDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(harnessCDActivity.getUuid()).isEqualTo(changeEventDTO.getId());
  }

  private void verifyEqual(DeploymentActivity harnessCDActivity, ChangeEventDTO changeEventDTO) {
    assertThat(harnessCDActivity.getAccountId()).isEqualTo(changeEventDTO.getAccountId());
    assertThat(harnessCDActivity.getOrgIdentifier()).isEqualTo(changeEventDTO.getOrgIdentifier());
    assertThat(harnessCDActivity.getProjectIdentifier()).isEqualTo(changeEventDTO.getProjectIdentifier());
    assertThat(harnessCDActivity.getEventTime().toEpochMilli()).isEqualTo(changeEventDTO.getEventTime());
    assertThat(harnessCDActivity.getType()).isEqualTo(changeEventDTO.getType().getActivityType());
    HarnessCDEventMetadata metadata = (HarnessCDEventMetadata) changeEventDTO.getMetadata();
    assertThat(harnessCDActivity.getActivityEndTime().toEpochMilli()).isEqualTo(metadata.getDeploymentEndTime());
    assertThat(harnessCDActivity.getActivityStartTime().toEpochMilli()).isEqualTo(metadata.getDeploymentStartTime());
    assertThat(harnessCDActivity.getStageStepId()).isEqualTo(metadata.getStageStepId());
    assertThat(harnessCDActivity.getPlanExecutionId()).isEqualTo(metadata.getPlanExecutionId());
    assertThat(harnessCDActivity.getPipelineId()).isEqualTo(metadata.getPipelineId());
    assertThat(harnessCDActivity.getRunSequence()).isEqualTo(metadata.getRunSequence());
    assertThat(harnessCDActivity.getStageId()).isEqualTo(metadata.getStageId());
    assertThat(harnessCDActivity.getPlanExecutionId()).isEqualTo(metadata.getPlanExecutionId());
    assertThat(harnessCDActivity.getArtifactType()).isEqualTo(metadata.getArtifactType());
    assertThat(harnessCDActivity.getArtifactTag()).isEqualTo(metadata.getArtifactTag());
    assertThat(harnessCDActivity.getDeploymentStatus()).isEqualTo(metadata.getStatus());
  }
}
