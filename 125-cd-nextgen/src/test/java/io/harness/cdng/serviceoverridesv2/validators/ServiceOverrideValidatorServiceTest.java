/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import static io.harness.cdng.manifest.ManifestConfigType.K8_MANIFEST;
import static io.harness.cdng.manifest.ManifestConfigType.VALUES;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceOverrideValidatorServiceTest extends CDNGTestBase {
  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateAllowedManifestTypesInOverrides() {
    ManifestConfigWrapper manifestConfigWrapper =
        ManifestConfigWrapper.builder()
            .manifest(ManifestConfig.builder().identifier("m1").type(VALUES).build())
            .build();

    List<ManifestConfigWrapper> manifestList = Collections.singletonList(manifestConfigWrapper);

    ServiceOverrideValidatorService.validateAllowedManifestTypesInOverrides(manifestList, "Environment override");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateAllowedManifestTypesInOverridesThrowsException() {
    ManifestConfigWrapper manifestConfigWrapper =
        ManifestConfigWrapper.builder()
            .manifest(ManifestConfig.builder().identifier("m1").type(K8_MANIFEST).build())
            .build();

    List<ManifestConfigWrapper> manifestList = Collections.singletonList(manifestConfigWrapper);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> ServiceOverrideValidatorService.validateAllowedManifestTypesInOverrides(
                            manifestList, "Environment override"))
        .withMessage("Unsupported Manifest Types: [K8sManifest] found for Environment override");
  }
}
