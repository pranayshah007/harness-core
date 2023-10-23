/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GcsStoreConfigTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyOverrides() {
    GcsStoreConfig original = GcsStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                  .bucketName(ParameterField.createValueField("bucket-name"))
                                  .folderPath(ParameterField.createValueField("folder-path"))
                                  .build();

    GcsStoreConfig override = GcsStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref-override"))
                                  .bucketName(ParameterField.createValueField("bucket-name-override"))
                                  .folderPath(ParameterField.createValueField("folder-path-override"))
                                  .build();

    GcsStoreConfig result = (GcsStoreConfig) original.applyOverrides(override);
    assertThat(result.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref-override");
    assertThat(result.getBucketName().getValue()).isEqualTo("bucket-name-override");
    assertThat(result.getFolderPath().getValue()).isEqualTo("folder-path-override");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyOverridesEmpty() {
    GcsStoreConfig original = GcsStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                  .bucketName(ParameterField.createValueField("bucket-name"))
                                  .folderPath(ParameterField.createValueField("folder-path"))
                                  .build();

    GcsStoreConfig override = GcsStoreConfig.builder().build();

    GcsStoreConfig result = (GcsStoreConfig) original.applyOverrides(override);
    assertThat(result.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
    assertThat(result.getBucketName().getValue()).isEqualTo("bucket-name");
    assertThat(result.getFolderPath().getValue()).isEqualTo("folder-path");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testCloneInternal() {
    GcsStoreConfig origin = GcsStoreConfig.builder()
                                .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                .bucketName(ParameterField.createValueField("bucket-name"))
                                .folderPath(ParameterField.createValueField("folder-path"))
                                .build();

    GcsStoreConfig originClone = (GcsStoreConfig) origin.cloneInternal();

    assertThat(originClone.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
    assertThat(originClone.getBucketName().getValue()).isEqualTo("bucket-name");
    assertThat(originClone.getFolderPath().getValue()).isEqualTo("folder-path");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    GcsStoreConfig origin = GcsStoreConfig.builder()
                                .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                .bucketName(ParameterField.createValueField("bucket-name"))
                                .folderPath(ParameterField.createValueField("folder-path"))
                                .build();

    assertThat(origin.extractConnectorRefs().get("connectorRef").getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
  }
}
