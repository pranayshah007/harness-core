/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InheritFromManifestStoreConfigTest extends CategoryTest {
  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testApplyOverrides() {
    InheritFromManifestStoreConfig original =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("file/path"))).build();

    InheritFromManifestStoreConfig override = InheritFromManifestStoreConfig.builder()
                                                  .paths(ParameterField.createValueField(asList("override/file/path")))
                                                  .build();

    InheritFromManifestStoreConfig result = (InheritFromManifestStoreConfig) original.applyOverrides(override);
    assertThat(result.getPaths().getValue().size()).isEqualTo(1);
    assertThat(result.getPaths().getValue().get(0)).isEqualTo("override/file/path");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testApplyOverridesEmpty() {
    InheritFromManifestStoreConfig original =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("file/path"))).build();

    InheritFromManifestStoreConfig override = InheritFromManifestStoreConfig.builder().build();

    InheritFromManifestStoreConfig result = (InheritFromManifestStoreConfig) original.applyOverrides(override);
    assertThat(result.getPaths().getValue().size()).isEqualTo(1);
    assertThat(result.getPaths().getValue().get(0)).isEqualTo("file/path");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testCloneInternal() {
    InheritFromManifestStoreConfig original =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("file/path"))).build();

    InheritFromManifestStoreConfig originClone = (InheritFromManifestStoreConfig) original.cloneInternal();
    assertThat(originClone.getPaths().getValue().size()).isEqualTo(1);
    assertThat(originClone.getPaths().getValue().get(0)).isEqualTo("file/path");
  }
}
