/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpStoreConfigTest extends CategoryTest {
  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testApplyOverrides() {
    HttpStoreConfig original =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref")).build();

    HttpStoreConfig override =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref-override")).build();

    HttpStoreConfig result = (HttpStoreConfig) original.applyOverrides(override);
    assertThat(result.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref-override");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testApplyOverridesEmpty() {
    HttpStoreConfig original =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref")).build();

    HttpStoreConfig override = HttpStoreConfig.builder().build();

    HttpStoreConfig result = (HttpStoreConfig) original.applyOverrides(override);
    assertThat(result.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testCloneInternal() {
    HttpStoreConfig original =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref")).build();

    HttpStoreConfig originClone = (HttpStoreConfig) original.cloneInternal();

    assertThat(originClone.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    HttpStoreConfig original =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref")).build();

    assertThat(original.extractConnectorRefs().get("connectorRef").getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
  }
}
