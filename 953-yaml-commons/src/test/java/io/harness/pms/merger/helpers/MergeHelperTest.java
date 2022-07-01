/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.merger.helpers.MergeHelper.mergeRuntimeInputValuesIntoOriginalYaml;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class MergeHelperTest extends CategoryTest {
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeInputSetIntoPipeline() {
    String filename = "pipeline-extensive.yml";
    String yaml = readFile(filename);

    String inputSet = "runtimeInput1.yml";
    String inputSetYaml = readFile(inputSet);

    String res = mergeRuntimeInputValuesIntoOriginalYaml(yaml, inputSetYaml, false);
    String resYaml = res.replace("\"", "");

    String mergedYamlFile = "pipeline-extensive-merged.yml";
    String mergedYaml = readFile(mergedYamlFile);

    assertThat(resYaml).isEqualTo(mergedYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeYamlUpdates() throws IOException {
    String filename = "opa-pipeline.yaml";
    String pipeline = readFile(filename);

    String fqn1 = "pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connector";
    String exp1 = "{\n"
        + "  \"name\": \"jira basic\",\n"
        + "  \"identifier\": \"jira_basic\",\n"
        + "  \"description\": \"\",\n"
        + "  \"orgIdentifier\": \"default\",\n"
        + "  \"projectIdentifier\": \"Local_Dev\",\n"
        + "  \"tags\": {},\n"
        + "  \"type\": \"Jira\",\n"
        + "  \"spec\": {\n"
        + "    \"jiraUrl\": \"https://harness.atlassian.net/\",\n"
        + "    \"username\": \"namanvermah\",\n"
        + "    \"passwordRef\": \"nvh_docker_pass\",\n"
        + "    \"delegateSelectors\": []\n"
        + "  }\n"
        + "}";

    String fqn2 = "pipeline/stages/[1]/stage/spec/infrastructure/environment";
    String exp2 = "{\n"
        + "  \"identifier\": \"PR_ENV\",\n"
        + "  \"name\": \"PR ENV\",\n"
        + "  \"description\": \"\",\n"
        + "  \"type\": \"PreProduction\",\n"
        + "  \"accountIdentifier\": \"kmpySmUISimoRrJL6NL73w\",\n"
        + "  \"orgIdentifier\": \"default\",\n"
        + "  \"projectIdentifier\": \"Local_Dev\",\n"
        + "  \"tags\": {}\n"
        + "}";

    Map<String, String> fqnToJsonMap = new HashMap<>();
    fqnToJsonMap.put(fqn1, exp1);
    fqnToJsonMap.put(fqn2, exp2);
    String expandedPipeline = MergeHelper.mergeUpdatesIntoJson(pipeline, fqnToJsonMap);
    assertThat(expandedPipeline).isNotNull();
    String expandedPipelineExpected = readFile("opa-pipeline-with-expansions-no-removals.json");
    assertThat(expandedPipeline).isEqualTo(expandedPipelineExpected);
    YamlField yamlField = YamlUtils.readTree(expandedPipeline);
    YamlNode firstExp =
        yamlField.getNode().gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connector");
    YamlNode secondExp = yamlField.getNode().gotoPath("pipeline/stages/[1]/stage/spec/infrastructure/environment");
    assertThat(firstExp).isNotNull();
    assertThat(secondExp).isNotNull();

    String noUpdates = MergeHelper.mergeUpdatesIntoJson(expandedPipeline, null);
    assertThat(noUpdates).isEqualTo(expandedPipeline);

    String noUpdatesOnYaml = MergeHelper.mergeUpdatesIntoJson(pipeline, null);
    assertThat(noUpdatesOnYaml).isEqualTo(readFile("opa-pipeline.json"));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testMergeYamlUpdatesEmojis() throws IOException {
    String filename = "opa-pipeline.yaml";
    String pipeline = readFile(filename);

    String fqn1 = "pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connector";
    String exp1 = readFile("emojiExp1.txt");

    String fqn2 = "pipeline/stages/[1]/stage/spec/infrastructure/environment";
    String exp2 = readFile("emojiExp2.txt");

    Map<String, String> fqnToJsonMap = new HashMap<>();
    fqnToJsonMap.put(fqn1, exp1);
    fqnToJsonMap.put(fqn2, exp2);
    String expandedPipeline = MergeHelper.mergeUpdatesIntoJson(pipeline, fqnToJsonMap);
    assertThat(expandedPipeline).isNotNull();
    String expandedPipelineExpected = readFile("opa-pipeline-with-expansions-no-removals.json");
    assertThat(expandedPipeline).isEqualTo(expandedPipelineExpected);
    YamlField yamlField = YamlUtils.readTree(expandedPipeline);
    YamlNode firstExp =
        yamlField.getNode().gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connector");
    YamlNode secondExp = yamlField.getNode().gotoPath("pipeline/stages/[1]/stage/spec/infrastructure/environment");
    assertThat(firstExp).isNotNull();
    assertThat(secondExp).isNotNull();

    String noUpdates = MergeHelper.mergeUpdatesIntoJson(expandedPipeline, null);
    assertThat(noUpdates).isEqualTo(expandedPipeline);

    String noUpdatesOnYaml = MergeHelper.mergeUpdatesIntoJson(pipeline, null);
    assertThat(noUpdatesOnYaml).isEqualTo(readFile("opa-pipeline.json"));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testRemoveNonASCII() {
    String asciiString = "";
    for (int i = 0; i < 128; i++) {
      asciiString += (char) i;
    }
    assertThat(MergeHelper.removeNonASCII(asciiString)).isEqualTo(asciiString);
    assertThat(MergeHelper.removeNonASCII(asciiString + "\ud330\ud803\ud823")).isEqualTo(asciiString);
    assertThat(MergeHelper.removeNonASCII("\ud230\ud803\ud123" + asciiString)).isEqualTo(asciiString);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRemoveFQNs() {
    String expandedPipelineExpected = readFile("opa-pipeline-with-expansions-no-removals.json");
    String noRemovals = MergeHelper.removeFQNs(expandedPipelineExpected, null);
    assertThat(noRemovals).isEqualTo(expandedPipelineExpected);

    String fqn1 = "pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connectorRef";
    String fqn2 = "pipeline/stages/[1]/stage/spec/infrastructure/environmentRef";
    List<String> toBeRemoved = Arrays.asList(fqn1, fqn2);
    String removedFQNs = MergeHelper.removeFQNs(expandedPipelineExpected, toBeRemoved);
    assertThat(removedFQNs).isEqualTo(readFile("opa-pipeline-with-expansions-and-removals.json"));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeServiceYamlV2() {
    String serviceYaml = "service:\n"
        + "    serviceRef: <+input>\n"
        + "    serviceInputs: <+input>\n";

    String runtimeServiceYaml = "service:\n"
        + "    serviceRef: service1";

    String expectedMergeServiceYaml = "service:\n"
        + "  serviceRef: service1\n"
        + "  serviceInputs: <+input>\n";
    String res = mergeRuntimeInputValuesIntoOriginalYaml(serviceYaml, runtimeServiceYaml, false);
    String resYaml = res.replace("\"", "");

    assertThat(resYaml).isEqualTo(expectedMergeServiceYaml);

    // case2: non-null value of serviceInputs

    runtimeServiceYaml = "service:\n"
        + "  serviceRef: service1\n"
        + "  serviceInputs:\n"
        + "    serviceDefinition:\n"
        + "      type: Kubernetes\n";
    res = mergeRuntimeInputValuesIntoOriginalYaml(serviceYaml, runtimeServiceYaml, false);
    resYaml = res.replace("\"", "");

    assertThat(resYaml).isEqualTo(runtimeServiceYaml);
  }
}
