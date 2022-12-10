/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.KubernetesChangeSourceSpec;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class KubernetesChangeSourceSpecTransformerTest extends CvNextGenTestBase {
  private BuilderFactory builderFactory;
  private KubernetesChangeSourceSpecTransformer kubernetesChangeSourceSpecTransformer;

  @Before
  public void setup() {
    kubernetesChangeSourceSpecTransformer = new KubernetesChangeSourceSpecTransformer();
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void test_getEntity() {
    ChangeSourceDTO changeSourceDTO = builderFactory.getKubernetesChangeSourceDTOBuilder().build();
    KubernetesChangeSource changeSource = kubernetesChangeSourceSpecTransformer.getEntity(
        builderFactory.getContext().getMonitoredServiceParams(), changeSourceDTO);
    assertThat(changeSource.getClass()).isEqualTo(KubernetesChangeSource.class);
    assertThat(changeSource.getIdentifier()).isEqualTo(changeSourceDTO.getIdentifier());
    assertThat(changeSource.getConnectorIdentifier())
        .isEqualTo(((KubernetesChangeSourceSpec) changeSourceDTO.getSpec()).getConnectorRef());
    assertThat(changeSource.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(changeSource.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(changeSource.getMonitoredServiceIdentifier())
        .isEqualTo(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
    assertThat(changeSource.isEnabled()).isTrue();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void test_getSpec() {
    KubernetesChangeSource changeSource = builderFactory.getKubernetesChangeSourceBuilder().build();
    KubernetesChangeSourceSpec changeSourceSpec = kubernetesChangeSourceSpecTransformer.getSpec(changeSource);
    assertThat(changeSourceSpec.getConnectorRef()).isEqualTo(changeSource.getConnectorIdentifier());
  }
}
