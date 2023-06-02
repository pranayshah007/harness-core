/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipeline;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSets;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipelineForGivenStages;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InputSetMergeHelperTest extends CategoryTest {
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private void assertStringEqualToFile(String result, String filename) {
    String expected = readFile(filename);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateTemplateFromPipelineForGivenStages() {
    String pipeline1 = "pipeline-extensive.yml";
    String pipelineYaml1 = readFile(pipeline1);
    String templateForQaStage4 =
        createTemplateFromPipelineForGivenStages(pipelineYaml1, Collections.singletonList("qaStage4"));
    assertStringEqualToFile(templateForQaStage4, "templateForQaStage4.yaml");
    String templateForQaStage3 =
        createTemplateFromPipelineForGivenStages(pipelineYaml1, Collections.singletonList("qaStage3"));
    assertStringEqualToFile(templateForQaStage3, "templateForQaStage3.yaml");
    String templateForQaStageAndQaStage3 =
        createTemplateFromPipelineForGivenStages(pipelineYaml1, Arrays.asList("qaStage", "qaStage3"));
    assertStringEqualToFile(templateForQaStageAndQaStage3, "templateForQaStageAndQaStage3.yaml");

    String pipeline2 = "pipeline-2.yaml";
    String pipelineYaml2 = readFile(pipeline2);
    String template = createTemplateFromPipelineForGivenStages(pipelineYaml2, Collections.singletonList("qaStage4"));
    assertThat(template).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeInputSetIntoPipelineForGivenStages() {
    String pipeline1 = "pipeline-2.yaml";
    String pipelineYaml1 = readFile(pipeline1);

    String runtimeInputForQaStageAndAppFile = "runtimeInputForQaStageAndAppAndPQ2.yaml";
    String runtimeInputForQaStageAndApp = readFile(runtimeInputForQaStageAndAppFile);

    String qaStageResult = mergeInputSetIntoPipelineForGivenStages(
        pipelineYaml1, runtimeInputForQaStageAndApp, false, Collections.singletonList("qaStage"));
    assertStringEqualToFile(qaStageResult, "mergedPipeline2ForQaStage.yaml");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeInputSetsForGivenStages() {
    String pipeline = "pipeline-2.yaml";
    String pipelineYaml = readFile(pipeline);

    String template = InputSetTemplateHelper.createTemplateFromPipeline(pipelineYaml);

    String runtimeInputForQaStageAndAppAndPQ2File = "runtimeInputForQaStageAndAppAndPQ2.yaml";
    String runtimeInputForQaStageAndAppAndPQ2 = readFile(runtimeInputForQaStageAndAppAndPQ2File);

    String runtimeInputForAppAndPQ2File = "runtimeInputForAppAndPQ2.yaml";
    String runtimeInputForAppAndPQ2 = readFile(runtimeInputForAppAndPQ2File);

    String mergedResult1 = InputSetMergeHelper.mergeInputSetsForGivenStages(template,
        Arrays.asList(runtimeInputForQaStageAndAppAndPQ2, runtimeInputForAppAndPQ2), false,
        Collections.singletonList("qaStage"));
    assertStringEqualToFile(mergedResult1, "mergedRuntimeInputsForQAStage.yaml");

    String mergedResult2 = InputSetMergeHelper.mergeInputSetsForGivenStages(template,
        Arrays.asList(runtimeInputForQaStageAndAppAndPQ2, runtimeInputForAppAndPQ2), false,
        Arrays.asList("qaStage", "pq2"));
    assertStringEqualToFile(mergedResult2, "mergedRuntimeInputForQAStageAndPQ2.yaml");

    String mergedResult3 = InputSetMergeHelper.mergeInputSetsForGivenStages(template,
        Arrays.asList(runtimeInputForQaStageAndAppAndPQ2, runtimeInputForAppAndPQ2), false,
        Arrays.asList("qaStage4", "pq2"));
    assertStringEqualToFile(mergedResult3, "mergedRuntimeInputForQAStage4AndPQ2.yaml");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeInputSetsForGivenStagesWithNoValueForGivenStages() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      key1: <+input>\n"
        + "      key2: <+input>\n"
        + "      key3: <+input>";
    String yamlForS1 = "inputSet:\n"
        + "  pipeline:\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: s1Value1";
    String mergedYaml = InputSetMergeHelper.mergeInputSetsForGivenStages(
        pipelineYaml, Collections.singletonList(yamlForS1), false, Collections.singletonList("s2"));
    assertThat(mergedYaml).isEqualTo(pipelineYaml);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testMergeInputSetIntoPipelineInputSetValidator() {
    String filename = "pipeline-inputset-validator.yml";
    String yaml = readFile(filename);

    String inputSet = "runtime-inputset-validator.yml";
    String inputSetYaml = readFile(inputSet);

    String res = mergeInputSetIntoPipeline(yaml, inputSetYaml, true);
    String resYaml = res.replace("\"", "");

    String mergedYamlFile = "pipeline-inputset-validator-merged.yml";
    String mergedYaml = readFile(mergedYamlFile);

    assertThat(resYaml).isEqualTo(mergedYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeInputSetIntoServiceDependenciesPipeline() {
    String filename = "service-dependencies-pipeline.yaml";
    String yaml = readFile(filename);

    String inputSet = "service-dependencies-runtime-input.yaml";
    String inputSetYaml = readFile(inputSet);

    String res = mergeInputSetIntoPipeline(yaml, inputSetYaml, false);
    String resYaml = res.replace("\"", "");

    String mergedYamlFile = "service-dependencies-pipeline-merged.yaml";
    String mergedYaml = readFile(mergedYamlFile);

    assertThat(resYaml).isEqualTo(mergedYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeInputSets() {
    String inputSet1 = "inputSet1.yml";
    String inputSetYaml1 = readFile(inputSet1);
    String inputSet2 = "inputSet2.yml";
    String inputSetYaml2 = readFile(inputSet2);
    List<String> inputSetYamlList = new ArrayList<>();
    inputSetYamlList.add(inputSetYaml1);
    inputSetYamlList.add(inputSetYaml2);

    String filename = "pipeline-extensive.yml";
    String yaml = readFile(filename);
    String templateYaml = createTemplateFromPipeline(yaml);

    String mergedYaml = mergeInputSets(templateYaml, inputSetYamlList, false);

    String inputSetMerged = "input12-merged.yml";
    String inputSetYamlMerged = readFile(inputSetMerged);
    assertThat(mergedYaml).isEqualTo(inputSetYamlMerged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeOnYamlWithFailureStrategies() {
    String fullYamlFile = "failure-strategy.yaml";
    String fullYaml = readFile(fullYamlFile);
    String templateOfFull = createTemplateFromPipeline(fullYaml);
    assertThat(templateOfFull).isNull();

    String yamlWithRuntimeFile = "failure-strategy-with-runtime-input.yaml";
    String yamlWithRuntime = readFile(yamlWithRuntimeFile);
    String template = createTemplateFromPipeline(yamlWithRuntime);

    String templateFile = "failure-strategy-template.yaml";
    String templateActual = readFile(templateFile);
    assertThat(template.replace("\"", "")).isEqualTo(templateActual);

    String runtimeInputFile = "failure-strategy-runtime-input.yaml";
    String runtimeInput = readFile(runtimeInputFile);
    String mergedYaml = mergeInputSetIntoPipeline(yamlWithRuntime, runtimeInput, false);
    assertThat(mergedYaml.replace("\"", "")).isEqualTo(fullYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeOnCIPipelineYaml() {
    String fullYamlFile = "ci-pipeline-with-reports.yaml";
    String fullYaml = readFile(fullYamlFile);
    String templateOfFull = createTemplateFromPipeline(fullYaml);
    assertThat(templateOfFull).isNull();

    String yamlWithRuntimeFile = "ci-pipeline-runtime-input.yaml";
    String yamlWithRuntime = readFile(yamlWithRuntimeFile);
    String template = createTemplateFromPipeline(yamlWithRuntime);

    String templateFile = "ci-pipeline-template.yaml";
    String templateActual = readFile(templateFile);
    assertThat(template.replace("\"", "")).isEqualTo(templateActual);

    String runtimeInputFile = "ci-runtime-input-yaml.yaml";
    String runtimeInput = readFile(runtimeInputFile);
    String mergedYaml = mergeInputSetIntoPipeline(yamlWithRuntime, runtimeInput, false);
    assertThat(mergedYaml.replace("\"", "")).isEqualTo(fullYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeOnPipelineWithEmptyListAndObject() {
    String yamlFile = "empty-object-and-list-with-runtime.yaml";
    String yaml = readFile(yamlFile);
    String template = createTemplateFromPipeline(yaml);
    assertThat(template).isNotNull();

    String runtimeInputFile = "empty-object-and-list-runtime.yaml";
    String runtimeInput = readFile(runtimeInputFile);
    String mergedYaml = mergeInputSetIntoPipeline(yaml, runtimeInput, false);

    String fullYamlFile = "empty-object-and-list.yaml";
    String fullYaml = readFile(fullYamlFile);
    assertThat(mergedYaml).isEqualTo(fullYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeOnPipelineWithHelmCommandFlags() {
    String yamlFile = "helm-command-flags-pipeline.yaml";
    String yaml = readFile(yamlFile);
    String template = createTemplateFromPipeline(yaml);
    assertThat(template).isNotNull();

    String runtimeInputFile = "helm-command-flags-runtime-input.yaml";
    String runtimeInput = readFile(runtimeInputFile);
    String mergedYaml = mergeInputSetIntoPipeline(yaml, runtimeInput, false);
    String fullYamlFile = "helm-command-flags-merged-pipeline.yaml";
    String fullYaml = readFile(fullYamlFile);
    assertThat(mergedYaml).isEqualTo(fullYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeOnPipelineVMInfrastructure() {
    String pipelineYaml = "pipeline:\n"
        + "    identifier: cipipeline2GDdkmQLfb\n"
        + "    name: run pipeline with output variable success\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              identifier: outputvar\n"
        + "              name: output variable\n"
        + "              type: CI\n"
        + "              spec:\n"
        + "                  execution:\n"
        + "                      steps:\n"
        + "                          - step:\n"
        + "                                identifier: two\n"
        + "                                name: two\n"
        + "                                type: Run\n"
        + "                                spec:\n"
        + "                                    command: <+input>\n"
        + "                                    shell: Powershell\n"
        + "                  infrastructure:\n"
        + "                      type: VM\n"
        + "                      spec:\n"
        + "                          type: Pool\n"
        + "                          spec:\n"
        + "                              identifier: windows\n"
        + "                  cloneCodebase: false\n"
        + "    projectIdentifier: Plain_Old_Project\n"
        + "    orgIdentifier: default\n";
    String runtimeInput = "pipeline:\n"
        + "    identifier: cipipeline2GDdkmQLfb\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              identifier: outputvar\n"
        + "              type: CI\n"
        + "              spec:\n"
        + "                  execution:\n"
        + "                      steps:\n"
        + "                          - step:\n"
        + "                                identifier: two\n"
        + "                                type: Run\n"
        + "                                spec:\n"
        + "                                    command: echo done\n";
    String mergedYaml = mergeInputSetIntoPipeline(pipelineYaml, runtimeInput, false);
    String expectedMergedYaml = "pipeline:\n"
        + "  identifier: cipipeline2GDdkmQLfb\n"
        + "  name: run pipeline with output variable success\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: outputvar\n"
        + "        type: CI\n"
        + "        name: output variable\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: two\n"
        + "                  type: Run\n"
        + "                  name: two\n"
        + "                  spec:\n"
        + "                    command: echo done\n"
        + "                    shell: Powershell\n"
        + "          infrastructure:\n"
        + "            type: VM\n"
        + "            spec:\n"
        + "              type: Pool\n"
        + "              spec:\n"
        + "                identifier: windows\n"
        + "          cloneCodebase: false\n"
        + "  projectIdentifier: Plain_Old_Project\n"
        + "  orgIdentifier: default\n";
    assertThat(mergedYaml).isEqualTo(expectedMergedYaml);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testMergeInputSetsV1() {
    List<String> inputSetYamlList = new ArrayList<>();
    inputSetYamlList.add("version: 1\n"
        + "name: partialset1\n"
        + "inputs:\n"
        + "  image: alpine\n"
        + "  repo: harness-core\n"
        + "  count: 0\n"
        + "options:\n"
        + "  clone:\n"
        + "    ref:\n"
        + "      type: commit\n"
        + "      name: asdf");

    inputSetYamlList.add("version: 1\n"
        + "name: partialset2\n"
        + "inputs:\n"
        + "  count: 1\n"
        + "  tag: latest\n"
        + "options:\n"
        + "  clone:\n"
        + "    ref:\n"
        + "      type: tag\n"
        + "      name: main");

    Set<String> possibleResponses = Set.of("options:\n"
            + "  clone:\n"
            + "    ref:\n"
            + "      type: \"tag\"\n"
            + "      name: \"main\"\n"
            + "inputs:\n"
            + "  image: \"alpine\"\n"
            + "  repo: \"harness-core\"\n"
            + "  count: 1\n"
            + "  tag: \"latest\"\n",
        "inputs:\n"
            + "  image: alpine\n"
            + "  repo: harness-core\n"
            + "  count: 1\n"
            + "  tag: latest\n"
            + "options:\n"
            + "  clone:\n"
            + "    ref:\n"
            + "      type: tag\n"
            + "      name: main\n");
    String mergedInputSetYaml = InputSetMergeHelper.mergeInputSetsV1(inputSetYamlList);
    assertThat(possibleResponses.contains(mergedInputSetYaml)).isTrue();

    inputSetYamlList = Arrays.asList("inputs:\n  a: a", "inputs:\n  b: b", "inputs:\n  c: c");
    assertThat(InputSetMergeHelper.mergeInputSetsV1(inputSetYamlList))
        .isEqualTo("inputs:\n"
            + "  a: a\n"
            + "  b: b\n"
            + "  c: c\n");

    inputSetYamlList = Arrays.asList("options:\n  clone:\n    ref:\n      type: branch\n      name: harness-core",
        "options:\n  clone:\n    ref:\n      type: tag");
    assertThat(InputSetMergeHelper.mergeInputSetsV1(inputSetYamlList))
        .isEqualTo("options:\n"
            + "  clone:\n"
            + "    ref:\n"
            + "      type: tag\n"
            + "      name: harness-core\n");
  }
}
