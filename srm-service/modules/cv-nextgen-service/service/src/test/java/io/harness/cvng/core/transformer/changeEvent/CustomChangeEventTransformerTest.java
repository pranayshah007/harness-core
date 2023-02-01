/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.cvng.activity.entities.CustomChangeActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.CustomChangeEventMetadata;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomChangeEventTransformerTest extends CvNextGenTestBase {
  @Inject CustomChangeEventTransformer customChangeEventTransformer;

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
  public void getEntity() {
    ChangeEventDTO changeEventDTO = builderFactory.getCustomChangeEventBuilder(ChangeSourceType.CUSTOM_DEPLOY).build();
    CustomChangeActivity customChangeActivity = customChangeEventTransformer.getEntity(changeEventDTO);
    verifyEqual(customChangeActivity, changeEventDTO);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void getMetadata() {
    CustomChangeActivity customChangeActivity =
        builderFactory.getCustomChangeActivity(ChangeSourceType.CUSTOM_DEPLOY).build();
    ChangeEventDTO changeEventDTO = customChangeEventTransformer.getDTO(customChangeActivity);
    verifyEqual(customChangeActivity, changeEventDTO);
  }

  private void verifyEqual(CustomChangeActivity customChangeActivity, ChangeEventDTO changeEventDTO) {
    assertThat(customChangeActivity.getAccountId()).isEqualTo(changeEventDTO.getAccountId());
    assertThat(customChangeActivity.getOrgIdentifier()).isEqualTo(changeEventDTO.getOrgIdentifier());
    assertThat(customChangeActivity.getProjectIdentifier()).isEqualTo(changeEventDTO.getProjectIdentifier());
    assertThat(customChangeActivity.getEventTime().toEpochMilli()).isEqualTo(changeEventDTO.getEventTime());
    assertThat(customChangeActivity.getType()).isEqualTo(changeEventDTO.getType().getActivityType());
    assertThat(customChangeActivity.getMonitoredServiceIdentifier())
        .isEqualTo(changeEventDTO.getMonitoredServiceIdentifier());
    assertThat(customChangeActivity.getChangeSourceIdentifier()).isEqualTo(changeEventDTO.getChangeSourceIdentifier());

    assertThat(customChangeActivity.getCustomChangeEvent())
        .isEqualTo(((CustomChangeEventMetadata) changeEventDTO.getMetadata()).getCustomChangeEvent());
    assertThat(customChangeActivity.getUser())
        .isEqualTo(((CustomChangeEventMetadata) changeEventDTO.getMetadata()).getUser());
    assertThat(customChangeActivity.getEndTime())
        .isEqualTo(((CustomChangeEventMetadata) changeEventDTO.getMetadata()).getEndTime());
  }
}
