/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.PagerDutyChangeSourceSpec;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PagerDutyChangeSourceSpecTransformerTest extends CvNextGenTestBase {
  private PagerDutyChangeSourceSpecTransformer pagerDutyChangeSourceSpecTransformer;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    pagerDutyChangeSourceSpecTransformer = new PagerDutyChangeSourceSpecTransformer();
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void test_getEntity() {
    ChangeSourceDTO changeSourceDTO = builderFactory.getPagerDutyChangeSourceDTOBuilder().build();
    ChangeSource pagerDutyChangeSource = pagerDutyChangeSourceSpecTransformer.getEntity(
        builderFactory.getContext().getMonitoredServiceParams(), changeSourceDTO);
    assertThat(pagerDutyChangeSource.getClass()).isEqualTo(PagerDutyChangeSource.class);
    assertThat(pagerDutyChangeSource.getIdentifier()).isEqualTo(changeSourceDTO.getIdentifier());
    assertThat(pagerDutyChangeSource.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(pagerDutyChangeSource.getProjectIdentifier())
        .isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(pagerDutyChangeSource.getMonitoredServiceIdentifier())
        .isEqualTo(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
    assertThat(pagerDutyChangeSource.isEnabled()).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void test_getSpec() {
    PagerDutyChangeSource changeSource = builderFactory.getPagerDutyChangeSourceBuilder().build();
    PagerDutyChangeSourceSpec changeSourceSpec = pagerDutyChangeSourceSpecTransformer.getSpec(changeSource);
    assertThat(changeSourceSpec.getConnectorRef()).isEqualTo(changeSource.getConnectorIdentifier());
    assertThat(changeSourceSpec.getPagerDutyServiceId()).isEqualTo(changeSource.getPagerDutyServiceId());
  }
}
