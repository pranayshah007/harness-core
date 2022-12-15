/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.InternalChangeActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.InternalChangeEventMetaData;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InternalChangeEventTransformerTest extends CvNextGenTestBase {
  @Inject InternalChangeEventTransformer internalChangeEventTransformer;

  BuilderFactory builderFactory;

  @Inject private MonitoredServiceService monitoredServiceService;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceService.createDefault(builderFactory.getProjectParams(),
        builderFactory.getContext().getServiceIdentifier(), builderFactory.getContext().getEnvIdentifier());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetEntity() {
    ChangeEventDTO changeEventDTO = builderFactory.getInternalChangeEventDTO_FFBuilder().build();
    InternalChangeActivity internalChangeActivity = internalChangeEventTransformer.getEntity(changeEventDTO);
    verifyEqual(internalChangeActivity, changeEventDTO);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetMetadata() {
    InternalChangeActivity internalChangeActivity = builderFactory.getInternalChangeActivity_FFBuilder().build();
    ChangeEventDTO changeEventDTO = internalChangeEventTransformer.getDTO(internalChangeActivity);
    verifyEqual(internalChangeActivity, changeEventDTO);
    /*assertThat(changeEventDTO.getServiceName()).isEqualTo("Mocked service name");
    assertThat(changeEventDTO.getEnvironmentName()).isEqualTo("Mocked env name");
    assertThat(internalChangeActivity.getUuid()).isEqualTo(changeEventDTO.getId());*/
  }

  private void verifyEqual(InternalChangeActivity internalChangeActivity, ChangeEventDTO changeEventDTO) {
    assertThat(internalChangeActivity.getAccountId()).isEqualTo(changeEventDTO.getAccountId());
    assertThat(internalChangeActivity.getOrgIdentifier()).isEqualTo(changeEventDTO.getOrgIdentifier());
    assertThat(internalChangeActivity.getProjectIdentifier()).isEqualTo(changeEventDTO.getProjectIdentifier());
    assertThat(internalChangeActivity.getEventTime().toEpochMilli()).isEqualTo(changeEventDTO.getEventTime());
    assertThat(internalChangeActivity.getType()).isEqualTo(changeEventDTO.getType().getActivityType());
    assertThat(internalChangeActivity.getEventTime().toEpochMilli()).isEqualTo(changeEventDTO.getEventTime());
    assertThat(internalChangeActivity.getMonitoredServiceIdentifier())
        .isEqualTo(changeEventDTO.getMonitoredServiceIdentifier());
    assertThat(internalChangeActivity.getChangeSourceIdentifier())
        .isEqualTo(changeEventDTO.getChangeSourceIdentifier());

    assertThat(internalChangeActivity.getInternalChangeEvent())
        .isEqualTo(((InternalChangeEventMetaData) changeEventDTO.getMetadata()).getInternalChangeEvent());
    assertThat(internalChangeActivity.getUpdatedBy())
        .isEqualTo(((InternalChangeEventMetaData) changeEventDTO.getMetadata()).getUpdatedBy());
    assertThat(internalChangeActivity.getActivityType())
        .isEqualTo(((InternalChangeEventMetaData) changeEventDTO.getMetadata()).getActivityType());
    assertThat(internalChangeActivity.getEventEndTime())
        .isEqualTo(((InternalChangeEventMetaData) changeEventDTO.getMetadata()).getEventEndTime());
  }
}
