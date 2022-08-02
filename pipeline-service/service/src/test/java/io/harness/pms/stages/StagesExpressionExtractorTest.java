/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StagesExpressionExtractorTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testReplaceExpressions() {
    String pipelineYamlWithExpressions = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "      description: \"desc\"\n"
        + "      name: \"s one\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "      description: \"the description is <+pipeline.stages.s1.description>\"\n"
        + "      name: \"<+pipeline.stages.s1.name>\"\n";
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "      description: \"desc\"\n"
        + "      name: \"s one\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "      description: \"the description is desc value\"\n"
        + "      name: \"name value\"\n";
    String res1 = StagesExpressionExtractor.replaceExpressions(pipelineYamlWithExpressions, null);
    assertThat(res1).isEqualTo(pipelineYamlWithExpressions);

    Map<String, String> expressionValues = new HashMap<>();
    expressionValues.put("<+pipeline.stages.s1.description>", "desc value");
    expressionValues.put("<+pipeline.stages.s1.name>", "name value");
    String res2 = StagesExpressionExtractor.replaceExpressions(pipelineYamlWithExpressions, expressionValues);
    assertThat(res2).isEqualTo(pipelineYaml);

    String res3 = StagesExpressionExtractor.replaceExpressions(pipelineYaml, expressionValues);
    assertThat(res3).isEqualTo(pipelineYaml);

    expressionValues.put("pipeline.stages.s1.name", "name value");
    assertThatThrownBy(() -> StagesExpressionExtractor.replaceExpressions(pipelineYaml, expressionValues))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "pipeline.stages.s1.name is not a syntactically valid pipeline expression. Is the expression surrounded by <+ >?");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetNonLocalExpressions() {
    String pipelineYaml = getPipelineYaml();
    Set<String> nonLocalExpressions1 =
        StagesExpressionExtractor.getNonLocalExpressions(pipelineYaml, Arrays.asList("a1", "d1", "p_d1"));
    assertThat(nonLocalExpressions1).hasSize(1);
    assertThat(nonLocalExpressions1).contains("<+pipeline.stages.a2.name>");

    Set<String> nonLocalExpressions2 =
        StagesExpressionExtractor.getNonLocalExpressions(pipelineYaml, Arrays.asList("a2", "d1_again", "p_d2"));
    assertThat(nonLocalExpressions2).hasSize(0);

    Set<String> nonLocalExpressions3 =
        StagesExpressionExtractor.getNonLocalExpressions(pipelineYaml, Collections.singletonList("d1"));
    assertThat(nonLocalExpressions3).hasSize(1);
    assertThat(nonLocalExpressions3).contains("<+stages.a1.name>");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetAllExpressionsInListOfStages() {
    String pipelineYaml = getPipelineYaml();
    Map<String, List<String>> d1AndPD1 =
        StagesExpressionExtractor.getAllExpressionsInListOfStages(pipelineYaml, Arrays.asList("d1", "p_d1"));
    assertThat(d1AndPD1).hasSize(2);
    assertThat(d1AndPD1.containsKey("d1")).isTrue();
    List<String> d1 = d1AndPD1.get("d1");
    assertThat(d1).hasSize(1);
    assertThat(d1.contains("<+stages.a1.name>")).isTrue();
    assertThat(d1AndPD1.containsKey("p_d1")).isTrue();
    List<String> pd1 = d1AndPD1.get("p_d1");
    assertThat(pd1).hasSize(1);
    assertThat(pd1.contains("<+pipeline.stages.a2.name>")).isTrue();

    Map<String, List<String>> a1 =
        StagesExpressionExtractor.getAllExpressionsInListOfStages(pipelineYaml, Collections.singletonList("a1"));
    assertThat(a1).hasSize(1);
    assertThat(a1.get("a1")).hasSize(0);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageYamlList() {
    String invalidYaml = "pipeline:\nidentifier:s1";
    assertThatThrownBy(() -> StagesExpressionExtractor.getStageYamlList(invalidYaml, Collections.singletonList("a")))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Could not read pipeline yaml while extracting stage yaml list");

    String pipelineYaml = getPipelineYaml();
    List<String> stageYamlList =
        StagesExpressionExtractor
            .getStageYamlList(pipelineYaml, Arrays.asList("a1", "a2", "d1", "p_d1", "p_d2", "d1_again"))
            .stream()
            .map(BasicStageInfo::getYaml)
            .collect(Collectors.toList());
    assertThat(stageYamlList).hasSize(6);
    assertThat(stageYamlList.get(0)).isEqualTo(getStage("a1", "a1", "Approval", "notAnExpression"));
    assertThat(stageYamlList.get(1)).isEqualTo(getStage("a2", "a2", "Approval", "<+stage.name>"));
    assertThat(stageYamlList.get(2)).isEqualTo(getStage("d1", "d1", "Deployment", "<+stages.a1.name>"));
    assertThat(stageYamlList.get(3)).isEqualTo(getStage("p_d1", "p d1", "Deployment", "<+pipeline.stages.a2.name>"));
    assertThat(stageYamlList.get(4)).isEqualTo(getStage("p_d2", "p d2", "Deployment", "<+input>"));
    assertThat(stageYamlList.get(5)).isEqualTo(getStage("d1_again", "d1 again", "Deployment", "<+that.other.field>"));

    List<String> stageYamlListForD1 =
        StagesExpressionExtractor.getStageYamlList(pipelineYaml, Collections.singletonList("d1"))
            .stream()
            .map(BasicStageInfo::getYaml)
            .collect(Collectors.toList());
    assertThat(stageYamlListForD1).hasSize(1);
    assertThat(stageYamlListForD1.get(0)).isEqualTo(getStage("d1", "d1", "Deployment", "<+stages.a1.name>"));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetListOfExpressions() {
    String a1 = getStage("a1", "a1", "Approval", "notAnExpression");
    assertThat(StagesExpressionExtractor.getListOfExpressions(a1)).hasSize(0);

    String a2 = getStage("a2", "a2", "Approval", "<+stage.name>");
    List<String> a2Expressions = StagesExpressionExtractor.getListOfExpressions(a2);
    assertThat(a2Expressions).hasSize(1);
    assertThat(a2Expressions.get(0)).isEqualTo("<+stage.name>");

    String pd2 = getStage("p_d2", "<+a>", "Deployment", "<+input>");
    List<String> pd2Expressions = StagesExpressionExtractor.getListOfExpressions(pd2);
    assertThat(pd2Expressions).hasSize(2);
    assertThat(pd2Expressions.get(0)).isEqualTo("<+a>");
    assertThat(pd2Expressions.get(1)).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRemoveLocalExpressions() {
    String s1 = "s1";
    List<String> s1Expressions =
        Arrays.asList("<+pipeline.stages.s1.name>", "<+stages.s10.description>", "<+stages.s2.description>",
            "<+artifact.name>", "<+input>", "<+input>", "<+input>", "<+input>", "<+step.timeout>", "<+pipeline.name>");
    String s2 = "s2";
    List<String> s2Expressions = Arrays.asList("<+pipeline.stages.a1.name>", "<+stages.s1.description>",
        "<+stages.s20.description>", "<+pipeline.variables.v1>", "<+pipeline.properties.ci.codebase.connectorRef>",
        "<+step.timeout>", "<+pipeline.name>");
    String s3 = "s3";
    List<String> s3Expressions = Arrays.asList("<+pipeline.stages.s3.name>", "<+stage.name>");
    String s4 = "s4";
    List<String> s4Expressions = Collections.emptyList();
    Map<String, List<String>> expressionsMap = new LinkedHashMap<>();
    expressionsMap.put(s1, s1Expressions);
    expressionsMap.put(s2, s2Expressions);
    expressionsMap.put(s3, s3Expressions);
    expressionsMap.put(s4, s4Expressions);
    Set<String> expressionsToOtherStages = StagesExpressionExtractor.removeLocalExpressions(expressionsMap);
    assertThat(expressionsToOtherStages).hasSize(3);
    assertThat(expressionsToOtherStages)
        .contains("<+stages.s10.description>", "<+pipeline.stages.a1.name>", "<+stages.s20.description>");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsLocalToStage() {
    assertThat(StagesExpressionExtractor.isLocalToStage("<+pipeline.stages.s1.name>")).isFalse();
    assertThat(StagesExpressionExtractor.isLocalToStage("<+stages.s1.description>")).isFalse();
    assertThat(StagesExpressionExtractor.isLocalToStage("<+input>")).isTrue();
    assertThat(StagesExpressionExtractor.isLocalToStage("<+step.name>")).isTrue();
    assertThat(StagesExpressionExtractor.isLocalToStage("<+artifact.image>")).isTrue();
    assertThatThrownBy(() -> StagesExpressionExtractor.isLocalToStage("staticValue"))
        .hasMessage("staticValue is not a syntactically valid pipeline expression")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageIdentifierInExpression() {
    assertThat(StagesExpressionExtractor.getStageIdentifierInExpression("<+pipeline.stages.s1.name>")).isEqualTo("s1");
    assertThat(StagesExpressionExtractor.getStageIdentifierInExpression("<+stages.s1.name>")).isEqualTo("s1");
    assertThat(StagesExpressionExtractor.getStageIdentifierInExpression("<+pipeline.stages.s_2.name>"))
        .isEqualTo("s_2");
    assertThatThrownBy(() -> StagesExpressionExtractor.getStageIdentifierInExpression("<+artifact.image>"))
        .hasMessage("<+artifact.image> is not a pipeline level or stages level expression")
        .isInstanceOf(InvalidRequestException.class);
  }

  private String getPipelineYaml() {
    return "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "     identifier: a1\n"
        + "     name: a1\n"
        + "     type: Approval\n"
        + "     field: notAnExpression\n"
        + "  - stage:\n"
        + "     identifier: a2\n"
        + "     name: a2\n"
        + "     type: Approval\n"
        + "     field: <+stage.name>\n"
        + "  - stage:\n"
        + "      identifier: d1\n"
        + "      name: d1\n"
        + "      type: Deployment\n"
        + "      field: <+stages.a1.name>\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: p_d1\n"
        + "        name: p d1\n"
        + "        type: Deployment\n"
        + "        field: <+pipeline.stages.a2.name>\n"
        + "    - stage:\n"
        + "        identifier: p_d2\n"
        + "        name: p d2\n"
        + "        type: Deployment\n"
        + "        field: <+input>\n"
        + "  - stage:\n"
        + "      identifier: d1_again\n"
        + "      name: d1 again\n"
        + "      type: Deployment\n"
        + "      field: <+that.other.field>\n";
  }

  private String getStage(String identifier, String name, String type, String field) {
    return "stage:\n"
        + "  identifier: \"" + identifier + "\"\n"
        + "  name: \"" + name + "\"\n"
        + "  type: \"" + type + "\"\n"
        + "  field: \"" + field + "\"\n";
  }
}
