/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.cvng.activity.entities.HarnessCDCurrentGenActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HarnessCDCurrentGenChangeEventTransformerTest extends CvNextGenTestBase {
  @Inject HarnessCDCurrentGenChangeEventTransformer harnessCDCurrentGenChangeEventTransformer;
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
    ChangeEventDTO changeEventDTO = builderFactory.harnessCDCurrentGenChangeEventDTOBuilder()
                                        .serviceIdentifier(null)
                                        .envIdentifier(null)
                                        .monitoredServiceIdentifier("monitoredServiceIdentifier")
                                        .build();
    HarnessCDCurrentGenActivity harnessCDActivity = harnessCDCurrentGenChangeEventTransformer.getEntity(changeEventDTO);
    assertThat(harnessCDActivity.getActivityName()).isEqualTo("Deployment for monitoredServiceIdentifier");
    verifyEqual(harnessCDActivity, changeEventDTO);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetadata() {
    HarnessCDCurrentGenActivity harnessCDActivity = builderFactory.getHarnessCDCurrentGenActivityBuilder().build();
    ChangeEventDTO changeEventDTO = harnessCDCurrentGenChangeEventTransformer.getDTO(harnessCDActivity);
    verifyEqual(harnessCDActivity, changeEventDTO);
    assertThat(changeEventDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(changeEventDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(harnessCDActivity.getUuid()).isEqualTo(changeEventDTO.getId());
  }

  private void verifyEqual(HarnessCDCurrentGenActivity harnessCDActivity, ChangeEventDTO changeEventDTO) {
    assertThat(harnessCDActivity.getAccountId()).isEqualTo(changeEventDTO.getAccountId());
    assertThat(harnessCDActivity.getOrgIdentifier()).isEqualTo(changeEventDTO.getOrgIdentifier());
    assertThat(harnessCDActivity.getProjectIdentifier()).isEqualTo(changeEventDTO.getProjectIdentifier());
    assertThat(harnessCDActivity.getEventTime().toEpochMilli()).isEqualTo(changeEventDTO.getEventTime());
    assertThat(harnessCDActivity.getType()).isEqualTo(changeEventDTO.getType().getActivityType());
    assertThat(harnessCDActivity.getWorkflowStartTime().toEpochMilli())
        .isEqualTo(((HarnessCDCurrentGenEventMetadata) changeEventDTO.getMetadata()).getWorkflowStartTime());
    assertThat(harnessCDActivity.getWorkflowId())
        .isEqualTo(((HarnessCDCurrentGenEventMetadata) changeEventDTO.getMetadata()).getWorkflowId());
    assertThat(harnessCDActivity.getArtifactName())
        .isEqualTo(((HarnessCDCurrentGenEventMetadata) changeEventDTO.getMetadata()).getArtifactName());
    assertThat(harnessCDActivity.getArtifactType())
        .isEqualTo(((HarnessCDCurrentGenEventMetadata) changeEventDTO.getMetadata()).getArtifactType());
    assertThat(harnessCDActivity.getWorkflowExecutionId())
        .isEqualTo(((HarnessCDCurrentGenEventMetadata) changeEventDTO.getMetadata()).getWorkflowExecutionId());
    assertThat(harnessCDActivity.getWorkflowEndTime().toEpochMilli())
        .isEqualTo(((HarnessCDCurrentGenEventMetadata) changeEventDTO.getMetadata()).getWorkflowEndTime());
  }
}
