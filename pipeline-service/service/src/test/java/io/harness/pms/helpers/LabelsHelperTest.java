/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.NGLabel;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LabelsHelperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetLabelsV1Yaml() throws IOException {
    List<NGLabel> expectedNgLabelList = Arrays.asList(
        NGLabel.builder().key("account").value("harness").build(), NGLabel.builder().key("org").value("cd").build());

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-v1.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    List<NGLabel> ngLabelList = LabelsHelper.getLabels(yamlContent, PipelineVersion.V1);
    assertThat(ngLabelList.size()).isNotZero();
    assertThat(ngLabelList.equals(expectedNgLabelList)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetLabelsV0Yaml() throws IOException {
    String yaml = "DUMMY";
    List<NGLabel> ngLabelList = LabelsHelper.getLabels(yaml, PipelineVersion.V0);
    assertThat(ngLabelList.size()).isZero();
  }
}
