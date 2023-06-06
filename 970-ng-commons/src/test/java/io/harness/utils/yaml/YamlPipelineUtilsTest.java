/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.JsonUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class YamlPipelineUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testWriteYamlString() throws IOException {
    // Testing an entire pipeline yaml
    ClassLoader classLoader = this.getClass().getClassLoader();
    Map map = new LinkedHashMap<String, Object>();
    String resolvedPipelineJsonFilename = "resolved-pipeline.json";
    String resolvedPipelineJson = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(resolvedPipelineJsonFilename)), StandardCharsets.UTF_8);
    JsonNode jsonNode = JsonUtils.readTree(resolvedPipelineJson);
    map.put("dummy", jsonNode);
    String resolvedPipelineYamlFilename = "resolved-pipeline.yaml";
    String resolvedPipelineYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(resolvedPipelineYamlFilename)), StandardCharsets.UTF_8);
    assertThat(YamlPipelineUtils.writeYamlString(map)).isEqualTo(resolvedPipelineYaml);

    // should not quote a simple string
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", "foobar"))).isEqualTo("k: foobar\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", "Some Name"))).isEqualTo("k: Some Name\n");
    // should not quote an int node
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", new IntNode(42)))).isEqualTo("k: 42\n");
    // should not remove quotes from a text node containing an int
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", new TextNode("42")))).isEqualTo("k: \"42\"\n");
    // should not remove quotes from boolean as a simple string
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", "true"))).isEqualTo("k: \"true\"\n");
    // should not remove quotes from multi line string
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", "Some \n Name"))).isEqualTo("k: \"Some \\n Name\"\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k",
                   "abc\n"
                       + "foo\n"
                       + "bar")))
        .isEqualTo("k: |-\n"
            + "  abc\n"
            + "  foo\n"
            + "  bar\n");
    // should not quote text node with simple string
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", new TextNode("abc")))).isEqualTo("k: abc\n");

    // Testing edge case scenarios for strings like 23e43 and +234 which should be wrapped with quotes
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", "+123"))).isEqualTo("k: \"+123\"\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", "+123.44"))).isEqualTo("k: \"+123.44\"\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", "23e45"))).isEqualTo("k: \"23e45\"\n");
    map = new LinkedHashMap<String, Object>();
    map.put("k1", "abc");
    map.put("k2", new TextNode("42e4"));
    assertThat(YamlPipelineUtils.writeYamlString(map)).isEqualTo("k1: abc\nk2: \"42e4\"\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", new TextNode("+1234")))).isEqualTo("k: \"+1234\"\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", new TextNode("+1234.12")))).isEqualTo("k: \"+1234.12\"\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", new TextNode("+1234.")))).isEqualTo("k: \"+1234.\"\n");
    assertThat(YamlPipelineUtils.writeYamlString(Map.of("k", new TextNode("+.12")))).isEqualTo("k: \"+.12\"\n");
  }
}
