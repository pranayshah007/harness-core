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
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.HarnessCDCurrentGenChangeSourceSpec;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HarnessCDCurrentGenChangeSourceSpecTransformerTest extends CvNextGenTestBase {
  private HarnessCDCurrentGenChangeSourceSpecTransformer harnessCDCurrentGenChangeSourceSpecTransformer;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    harnessCDCurrentGenChangeSourceSpecTransformer = new HarnessCDCurrentGenChangeSourceSpecTransformer();
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void test_getEntity() {
    ChangeSourceDTO changeSourceDTO = builderFactory.getHarnessCDCurrentGenChangeSourceDTOBuilder().build();
    ChangeSource harnessCDCurrentGenChangeSource = harnessCDCurrentGenChangeSourceSpecTransformer.getEntity(
        builderFactory.getContext().getMonitoredServiceParams(), changeSourceDTO);
    assertThat(harnessCDCurrentGenChangeSource.getClass()).isEqualTo(HarnessCDCurrentGenChangeSource.class);
    assertThat(harnessCDCurrentGenChangeSource.getIdentifier()).isEqualTo(changeSourceDTO.getIdentifier());
    assertThat(harnessCDCurrentGenChangeSource.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(harnessCDCurrentGenChangeSource.getProjectIdentifier())
        .isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(harnessCDCurrentGenChangeSource.getMonitoredServiceIdentifier())
        .isEqualTo(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
    assertThat(harnessCDCurrentGenChangeSource.isEnabled()).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void test_getSpec() {
    HarnessCDCurrentGenChangeSource changeSource = builderFactory.getHarnessCDCurrentGenChangeSourceBuilder().build();
    HarnessCDCurrentGenChangeSourceSpec changeSourceSpec =
        harnessCDCurrentGenChangeSourceSpecTransformer.getSpec(changeSource);
    assertThat(changeSourceSpec.getHarnessApplicationId()).isEqualTo(changeSource.getHarnessApplicationId());
    assertThat(changeSourceSpec.getHarnessServiceId()).isEqualTo(changeSource.getHarnessServiceId());
    assertThat(changeSourceSpec.getHarnessEnvironmentId()).isEqualTo(changeSource.getHarnessEnvironmentId());
  }
}
