/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class JsonPipelineUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRead() throws IOException {
    // Items is a list of string
    String yaml = "repeat:\n"
        + "  items: [\"a\",\"b\"]\n";
    StrategyConfig strategyConfig =
        JsonPipelineUtils.read(YamlUtils.readAsJsonNode(yaml).toString(), StrategyConfig.class);
    assertEquals(strategyConfig.getRepeat().getItems().getValue(), List.of("a", "b"));
    // Items is a list of string wrapped within a string
    yaml = "repeat:\n"
        + "  items: \"[\\\"a\\\",\\\"b\\\"]\"\n";
    strategyConfig = JsonPipelineUtils.read(YamlUtils.readAsJsonNode(yaml).toString(), StrategyConfig.class);
    assertEquals(strategyConfig.getRepeat().getItems().getValue(), List.of("a", "b"));
    // start is an integer
    yaml = "repeat:\n"
        + "  start: \"1\"";
    strategyConfig = JsonPipelineUtils.read(YamlUtils.readAsJsonNode(yaml).toString(), StrategyConfig.class);
    assertEquals(strategyConfig.getRepeat().getStart().getValue(), Integer.valueOf(1));
    // start is an integer wrapped within a string
    yaml = "repeat:\n"
        + "  start: 1";
    strategyConfig = JsonPipelineUtils.read(YamlUtils.readAsJsonNode(yaml).toString(), StrategyConfig.class);
    assertEquals(strategyConfig.getRepeat().getStart().getValue(), Integer.valueOf(1));
  }
}
