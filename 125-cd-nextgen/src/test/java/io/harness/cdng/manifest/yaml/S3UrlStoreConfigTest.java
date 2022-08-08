/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class S3UrlStoreConfigTest extends CategoryTest {
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testApplyOverrides() {
    ParameterField<List<String>> urls = ParameterField.createValueField(Collections.singletonList("url1"));
    ParameterField<List<String>> urlsOverride =
        ParameterField.createValueField(Collections.singletonList("url-override"));

    S3UrlStoreConfig origin = S3UrlStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("connector-ref"))
                                  .urls(urls)
                                  .region(ParameterField.createValueField("region"))
                                  .build();

    S3UrlStoreConfig override = S3UrlStoreConfig.builder()
                                    .connectorRef(ParameterField.createValueField("connector-ref-override"))
                                    .urls(urlsOverride)
                                    .region(ParameterField.createValueField("region-override"))
                                    .build();

    S3UrlStoreConfig result = (S3UrlStoreConfig) origin.applyOverrides(override);
    assertThat(result.getConnectorRef().getValue()).isEqualTo("connector-ref-override");
    assertThat(result.getUrls().getValue().get(0)).isEqualTo("url-override");
    assertThat(result.getRegion().getValue()).isEqualTo("region-override");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testApplyOverridesEmpty() {
    ParameterField<List<String>> urls = ParameterField.createValueField(Collections.singletonList("url1"));
    S3UrlStoreConfig origin = S3UrlStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("connector-ref"))
                                  .urls(urls)
                                  .region(ParameterField.createValueField("region"))
                                  .build();

    S3UrlStoreConfig override = S3UrlStoreConfig.builder().build();

    S3UrlStoreConfig result = (S3UrlStoreConfig) origin.applyOverrides(override);

    assertThat(result.getConnectorRef().getValue()).isEqualTo("connector-ref");
    assertThat(result.getUrls().getValue().get(0)).isEqualTo("url1");
    assertThat(result.getRegion().getValue()).isEqualTo("region");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCloneInternal() {
    ParameterField<List<String>> urls = ParameterField.createValueField(Collections.singletonList("url1"));
    S3UrlStoreConfig origin = S3UrlStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("connector-ref"))
                                  .urls(urls)
                                  .region(ParameterField.createValueField("region"))
                                  .build();

    S3UrlStoreConfig originClone = (S3UrlStoreConfig) origin.cloneInternal();

    assertThat(originClone.getConnectorRef().getValue()).isEqualTo("connector-ref");
    assertThat(originClone.getUrls().getValue().get(0)).isEqualTo("url1");
    assertThat(originClone.getRegion().getValue()).isEqualTo("region");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    ParameterField<List<String>> urls = ParameterField.createValueField(Collections.singletonList("url1"));
    S3UrlStoreConfig origin = S3UrlStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("connector-ref"))
                                  .urls(urls)
                                  .region(ParameterField.createValueField("region"))
                                  .build();

    assertThat(origin.extractConnectorRefs().get("connectorRef").getValue()).isEqualTo("connector-ref");
  }
}
