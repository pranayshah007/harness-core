/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sApplyStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExtractRefs() {
    K8sApplyStepInfo k8sApplyStepInfo =
        K8sApplyStepInfo.infoBuilder()
            .overrides(List.of(
                ManifestConfigWrapper.builder()
                    .manifest(
                        ManifestConfig.builder()
                            .identifier("values_test1")
                            .type(ManifestConfigType.VALUES)
                            .spec(K8sManifest.builder()
                                      .store(ParameterField.createValueField(
                                          StoreConfigWrapper.builder()
                                              .spec(HarnessStore.builder()
                                                        .files(ParameterField.createValueField(List.of("/script.sh")))
                                                        .build())
                                              .build()))

                                      .build())
                            .build())
                    .build()))
            .build();

    Map<String, ParameterField<List<String>>> fileMap;
    fileMap = k8sApplyStepInfo.extractFileRefs();
    assertThat(fileMap.get("overrides.manifest.values_test1.spec.store.spec.files").getValue().get(0))
        .isEqualTo("/script.sh");
  }
}
