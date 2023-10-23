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

public class S3StoreConfigTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyOverrides() {
    S3StoreConfig origin = S3StoreConfig.builder()
                               .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                               .bucketName(ParameterField.createValueField("bucket-name"))
                               .region(ParameterField.createValueField("region"))
                               .folderPath(ParameterField.createValueField("folder-path"))
                               .build();

    S3StoreConfig override = S3StoreConfig.builder()
                                 .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref-override"))
                                 .bucketName(ParameterField.createValueField("bucket-name-override"))
                                 .region(ParameterField.createValueField("region-override"))
                                 .folderPath(ParameterField.createValueField("folder-path-override"))
                                 .build();

    S3StoreConfig result = (S3StoreConfig) origin.applyOverrides(override);
    assertThat(result.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref-override");
    assertThat(result.getBucketName().getValue()).isEqualTo("bucket-name-override");
    assertThat(result.getRegion().getValue()).isEqualTo("region-override");
    assertThat(result.getFolderPath().getValue()).isEqualTo("folder-path-override");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyOverridesEmpty() {
    S3StoreConfig origin = S3StoreConfig.builder()
                               .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                               .bucketName(ParameterField.createValueField("bucket-name"))
                               .region(ParameterField.createValueField("region"))
                               .folderPath(ParameterField.createValueField("folder-path"))
                               .build();

    S3StoreConfig override = S3StoreConfig.builder().build();

    S3StoreConfig result = (S3StoreConfig) origin.applyOverrides(override);

    assertThat(result.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
    assertThat(result.getBucketName().getValue()).isEqualTo("bucket-name");
    assertThat(result.getRegion().getValue()).isEqualTo("region");
    assertThat(result.getFolderPath().getValue()).isEqualTo("folder-path");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testCloneInternal() {
    S3StoreConfig origin = S3StoreConfig.builder()
                               .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                               .bucketName(ParameterField.createValueField("bucket-name"))
                               .region(ParameterField.createValueField("region"))
                               .folderPath(ParameterField.createValueField("folder-path"))
                               .build();

    S3StoreConfig originClone = (S3StoreConfig) origin.cloneInternal();

    assertThat(originClone.getConnectorRef().getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
    assertThat(originClone.getBucketName().getValue()).isEqualTo("bucket-name");
    assertThat(originClone.getRegion().getValue()).isEqualTo("region");
    assertThat(originClone.getFolderPath().getValue()).isEqualTo("folder-path");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    S3StoreConfig origin = S3StoreConfig.builder()
                               .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                               .bucketName(ParameterField.createValueField("bucket-name"))
                               .region(ParameterField.createValueField("region"))
                               .folderPath(ParameterField.createValueField("folder-path"))
                               .build();

    assertThat(origin.extractConnectorRefs().get("connectorRef").getValue()).isEqualTo("ConnectorDisconnectHandler-ref");
  }
}
