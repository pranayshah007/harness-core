/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SimplifiedPipelineYamlHelperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetBasicPipeline() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    try {
      SimplifiedPipelineYamlHelper.getSimplifiedPipelineYaml(yamlContent);
    } catch (IOException ioException) {
      fail("Couldn't parse yaml into basic pipeline");
    }
  }
}
