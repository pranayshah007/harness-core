/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin.service;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PLUGIN_OUTPUT_FILE_PATHS_CONTENT;
import static io.harness.rule.OwnerRule.SAINATH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(CI)
public class PluginServiceImplTest extends io.harness.ContainerTestBase {
  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetPluginOutputFilePathsContent() {
    String stepIdentifier = "stepIdentifier";
    List<String> outputFilePathsContent = Arrays.asList("a", "b");
    ParameterField<List<String>> outputFilePathsContentParameterField =
        ParameterField.<List<String>>builder().value(outputFilePathsContent).build();
    Map<String, String> env =
        PluginServiceImpl.getPluginOutputFilePathsContent(outputFilePathsContentParameterField, stepIdentifier);

    Map<String, String> expected = new HashMap<>();
    expected.put(PLUGIN_OUTPUT_FILE_PATHS_CONTENT, "a,b");

    assertThat(env).isEqualTo(expected);

    // if outputFilePathsContent is null
    env = PluginServiceImpl.getPluginOutputFilePathsContent(null, stepIdentifier);

    expected = new HashMap<>();

    assertThat(env).isEqualTo(expected);
  }
}
