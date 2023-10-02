/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifestConfigs.ManifestConfigurations;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.beans.EcsServiceSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ManifestFilterHelperTest extends CategoryTest {
  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestConfigurations() {
    ServiceSpec spec =
        KubernetesServiceSpec.builder().manifestConfigurations(ManifestConfigurations.builder().build()).build();
    ManifestConfigurations manifestConfigurations =
        ManifestFilterHelper.getManifestConfigurationsFromKubernetesAndNativeHelm(spec);
    assertThat(manifestConfigurations).isNotNull();

    spec = KubernetesServiceSpec.builder().build();
    manifestConfigurations = ManifestFilterHelper.getManifestConfigurationsFromKubernetesAndNativeHelm(spec);
    assertThat(manifestConfigurations).isNull();

    spec = NativeHelmServiceSpec.builder().manifestConfigurations(ManifestConfigurations.builder().build()).build();
    manifestConfigurations = ManifestFilterHelper.getManifestConfigurationsFromKubernetesAndNativeHelm(spec);
    assertThat(manifestConfigurations).isNotNull();

    spec = EcsServiceSpec.builder().build();
    manifestConfigurations = ManifestFilterHelper.getManifestConfigurationsFromKubernetesAndNativeHelm(spec);
    assertThat(manifestConfigurations).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testHasPrimaryManifestRef() {
    assertThat(ManifestFilterHelper.hasPrimaryManifestRef(null)).isFalse();
    assertThat(ManifestFilterHelper.hasPrimaryManifestRef(ManifestConfigurations.builder().build())).isFalse();
    assertThat(
        ManifestFilterHelper.hasPrimaryManifestRef(
            ManifestConfigurations.builder().primaryManifestRef(ParameterField.createValueField("manifest")).build()))
        .isTrue();
  }
}
