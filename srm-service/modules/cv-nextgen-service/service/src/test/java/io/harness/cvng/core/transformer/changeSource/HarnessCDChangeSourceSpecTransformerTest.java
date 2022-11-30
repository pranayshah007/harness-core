/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.HarnessCDChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HarnessCDChangeSourceSpecTransformerTest {
  ChangeSourceSpecTransformer changeSourceSpecTransformer;

  MonitoredServiceParams monitoredServiceParams;
  BuilderFactory builderFactory;

  @Before
  public void setup() {
    changeSourceSpecTransformer = new HarnessCDChangeSourceSpecTransformer();
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceParams = builderFactory.getContext().getMonitoredServiceParams();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void test_getEntity() {
    ChangeSourceDTO changeSourceDTO = builderFactory.getHarnessCDChangeSourceDTOBuilder().build();
    ChangeSource cdngChangeSource = changeSourceSpecTransformer.getEntity(monitoredServiceParams, changeSourceDTO);
    assertThat(cdngChangeSource.getClass()).isEqualTo(HarnessCDChangeSource.class);
    assertThat(cdngChangeSource.getIdentifier()).isEqualTo(changeSourceDTO.getIdentifier());
    assertThat(cdngChangeSource.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(cdngChangeSource.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(cdngChangeSource.getMonitoredServiceIdentifier())
        .isEqualTo(monitoredServiceParams.getMonitoredServiceIdentifier());
    assertThat(cdngChangeSource.isEnabled()).isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void test_getSpec() {
    assertThat(changeSourceSpecTransformer.getSpec(builderFactory.getHarnessCDChangeSourceBuilder().build()))
        .isEqualTo(new HarnessCDChangeSourceSpec());
  }
}
