/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.JsonUtils;

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

public class TemplateYamlUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testWriteString() throws IOException {
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
    assertThat(TemplateYamlUtils.writeString(map).replaceFirst("---\n", "")).isEqualTo(resolvedPipelineYaml);
    assertThat(TemplateYamlUtils.writeString(Map.of("k", "Some Name")).replaceFirst("---\n", ""))
        .isEqualTo("k: Some Name\n");
    assertThat(TemplateYamlUtils.writeString(Map.of("k", new IntNode(42))).replaceFirst("---\n", ""))
        .isEqualTo("k: 42\n");
    assertThat(TemplateYamlUtils.writeString(Map.of("k", new TextNode("42"))).replaceFirst("---\n", ""))
        .isEqualTo("k: \"42\"\n");
    map = new LinkedHashMap<String, Object>();
    map.put("k1", "abc");
    map.put("k2", new TextNode("42e4"));
    assertThat(TemplateYamlUtils.writeString(map).replaceFirst("---\n", "")).isEqualTo("k1: abc\nk2: \"42e4\"\n");
    assertThat(TemplateYamlUtils.writeString(Map.of("k", "true")).replaceFirst("---\n", "")).isEqualTo("k: \"true\"\n");
    assertThat(TemplateYamlUtils.writeString(Map.of("k", "Some \n Name")).replaceFirst("---\n", ""))
        .isEqualTo("k: \"Some \\n Name\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("abc"))).replaceFirst("---\n", ""))
        .isEqualTo("k: abc\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+1234")))).isEqualTo("k: \"+1234\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+1234.12")))).isEqualTo("k: \"+1234.12\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+1234.")))).isEqualTo("k: \"+1234.\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+.12")))).isEqualTo("k: \"+.12\"\n");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testWriteYamlString() throws IOException {
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
    assertThat(TemplateYamlUtils.writeYamlString(map)).isEqualTo(resolvedPipelineYaml);
    // should not quote a simple string
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", "foobar"))).isEqualTo("k: foobar\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", "Some Name"))).isEqualTo("k: Some Name\n");
    // should not quote an int node
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new IntNode(42)))).isEqualTo("k: 42\n");
    // should not remove quotes from a text node containing an int
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("42")))).isEqualTo("k: \"42\"\n");
    map = new LinkedHashMap<String, Object>();
    map.put("k1", "abc");
    map.put("k2", new TextNode("42e4"));
    assertThat(TemplateYamlUtils.writeYamlString(map)).isEqualTo("k1: abc\nk2: \"42e4\"\n");
    // should quote a boolean
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", "true"))).isEqualTo("k: \"true\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", "Some \n Name"))).isEqualTo("k: \"Some \\n Name\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k",
                   "abc\n"
                       + "foo\n"
                       + "bar")))
        .isEqualTo("k: |-\n"
            + "  abc\n"
            + "  foo\n"
            + "  bar\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("abc")))).isEqualTo("k: abc\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+1234")))).isEqualTo("k: \"+1234\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+1234.12")))).isEqualTo("k: \"+1234.12\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+1234.")))).isEqualTo("k: \"+1234.\"\n");
    assertThat(TemplateYamlUtils.writeYamlString(Map.of("k", new TextNode("+.12")))).isEqualTo("k: \"+.12\"\n");
  }
}