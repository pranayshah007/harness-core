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
import static io.harness.rule.OwnerRule.SRIDHAR;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.YamlConfig;
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
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testMergeInputSetIntoPipelineRegex() {
    String filename = "pipeline-regex.yml";
    String yaml = readFile(filename);

    String inputSet = "pipeline-regex-input.yml";
    String inputSetYaml = readFile(inputSet);

    String res = mergeRuntimeInputValuesIntoOriginalYaml(yaml, inputSetYaml, true);
    String resYaml = res.replace("\"", "");

    String mergedYamlFile = "pipeline-regex-merged.yml";
    String mergedYaml = readFile(mergedYamlFile);

    assertEquals(removeWhiteSpaces(resYaml), removeWhiteSpaces(mergedYaml));
  }

  String removeWhiteSpaces(String input) {
    return input.replaceAll("\\s+", "");
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

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithExtraAllowedFields() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      service:\n"
        + "        serviceRef: <+input>\n"
        + "      environment:\n"
        + "        environmentRef: <+input>\n"
        + "        infrastructureDefinitions: <+input>\n"
        + "      variables:\n"
        + "        - name: var1\n"
        + "          value: <+input>";
    String runtimeInput = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "      ignoreThis: \"s1\"\n"
        + "      service:\n"
        + "        serviceRef: \"s1\"\n"
        + "      environment:\n"
        + "        environmentRef: \"e1\"\n"
        + "        infrastructureDefinitions:\n"
        + "        - identifier: \"i1\"\n"
        + "        - identifier: \"i2\"\n"
        + "          inputs:\n"
        + "            isThisCorrect: \"maybe\"\n"
        + "        randomFields:\n"
        + "        - parallel:\n"
        + "          - step:\n"
        + "              identifier: \"s1\"\n"
        + "              description: \"not valid\"\n"
        + "          - step:\n"
        + "              identifier: \"s2\"\n"
        + "              description: \"not valid\"\n"
        + "        - step:\n"
        + "            identifier: \"s3\"\n"
        + "            description: \"not valid\"\n"
        + "        - parallel:\n"
        + "          - step:\n"
        + "              identifier: \"s4\"\n"
        + "              description: \"not valid\"\n"
        + "          - step:\n"
        + "              identifier: \"s5\"\n"
        + "              description: \"not valid\"\n"
        + "      variables:\n"
        + "        - name: var1\n"
        + "          value: <+input>.executionInput()";
    String merged = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        service:\n"
        + "          serviceRef: s1\n"
        + "        environment:\n"
        + "          environmentRef: e1\n"
        + "          infrastructureDefinitions:\n"
        + "            - identifier: i1\n"
        + "            - identifier: i2\n"
        + "              inputs:\n"
        + "                isThisCorrect: maybe\n"
        + "          randomFields:\n"
        + "            - parallel:\n"
        + "                - step:\n"
        + "                    identifier: s1\n"
        + "                    description: not valid\n"
        + "                - step:\n"
        + "                    identifier: s2\n"
        + "                    description: not valid\n"
        + "            - step:\n"
        + "                identifier: s3\n"
        + "                description: not valid\n"
        + "            - parallel:\n"
        + "                - step:\n"
        + "                    identifier: s4\n"
        + "                    description: not valid\n"
        + "                - step:\n"
        + "                    identifier: s5\n"
        + "                    description: not valid\n"
        + "        variables:\n"
        + "          - name: var1\n"
        + "            value: <+input>.executionInput()\n";
    String result = mergeRuntimeInputValuesIntoOriginalYaml(pipelineYaml, runtimeInput, false);
    assertThat(result).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithServiceAsAxisName() {
    String baseYaml = "stage:\n"
        + "  strategy:\n"
        + "    matrix:\n"
        + "      service: <+input>\n"
        + "      env: <+input>\n";
    String runtimeInput = "stage:\n"
        + "  strategy:\n"
        + "    matrix:\n"
        + "      service:\n"
        + "      - svc1\n"
        + "      env:\n"
        + "      - env1\n"
        + "      - env2\n";
    String merged = "stage:\n"
        + "  strategy:\n"
        + "    matrix:\n"
        + "      service:\n"
        + "        - svc1\n"
        + "      env:\n"
        + "        - env1\n"
        + "        - env2\n";
    String result = mergeRuntimeInputValuesIntoOriginalYaml(baseYaml, runtimeInput, false);
    assertThat(result).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithEnvAsInputUnderEnvGroups() {
    String baseYaml = "stage:\n"
        + "  environmentGroup:\n"
        + "    environments: <+input>\n";
    String runtimeInput = "stage:\n"
        + "  junk: \"yes\"\n"
        + "  environmentGroup:\n"
        + "    environments:\n"
        + "      values:\n"
        + "      - environmentRef: \"Env2\"\n"
        + "        infrastructureDefinitions:\n"
        + "        - identifier: \"Infra2\"\n"
        + "      - environmentRef: \"Env3\"\n"
        + "        infrastructureDefinitions:\n"
        + "        - identifier: \"Infra3\"\n"
        + "    deployToAll: \"true\"\n";
    String merged = "stage:\n"
        + "  environmentGroup:\n"
        + "    environments:\n"
        + "      values:\n"
        + "        - environmentRef: Env2\n"
        + "          infrastructureDefinitions:\n"
        + "            - identifier: Infra2\n"
        + "        - environmentRef: Env3\n"
        + "          infrastructureDefinitions:\n"
        + "            - identifier: Infra3\n"
        + "    deployToAll: \"true\"\n";
    String result = mergeRuntimeInputValuesIntoOriginalYaml(baseYaml, runtimeInput, false);
    assertThat(result).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithAllowedValues() {
    String base = "stage:\n"
        + "  key: <+input>.allowedValues(a,b,c)\n";
    String runtime = "stage:\n"
        + "  key: d\n";
    assertThatThrownBy(() -> mergeRuntimeInputValuesIntoOriginalYaml(base, runtime, true))
        .isInstanceOf(InvalidRequestException.class);

    String correctRuntime = "stage:\n"
        + "  key: \"b\"\n";
    String merged = "stage:\n"
        + "  key: \"b.allowedValues(a,b,c)\"\n";
    assertThat(mergeRuntimeInputValuesIntoOriginalYaml(base, correctRuntime, true)).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithAllowedValuesAndExpressions() {
    String base = "stage:\n"
        + "  key: <+input>.allowedValues(a,b,c)\n";
    String withExpression = "stage:\n"
        + "  key: \"<+expr>\"\n";
    String merged = "stage:\n"
        + "  key: \"<+expr>.allowedValues(a,b,c)\"\n";
    assertThat(mergeRuntimeInputValuesIntoOriginalYaml(base, withExpression, true)).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithAllowedValuesOnListOfStrings() {
    String base = "stage:\n"
        + "  key: <+input>.allowedValues(a,b,c)\n";
    String runtime = "stage:\n"
        + "  key:\n"
        + "  - a\n"
        + "  - x\n";
    assertThatThrownBy(() -> mergeRuntimeInputValuesIntoOriginalYaml(base, runtime, true))
        .isInstanceOf(InvalidRequestException.class);

    String correctRuntime = "stage:\n"
        + "  key:\n"
        + "  - a\n"
        + "  - b\n";
    String merged = "stage:\n"
        + "  key:\n"
        + "    - \"a.allowedValues(a,b,c)\"\n"
        + "    - \"b.allowedValues(a,b,c)\"\n";
    assertThat(mergeRuntimeInputValuesIntoOriginalYaml(base, correctRuntime, true)).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithAllowedValuesDouble() {
    String base = "stage:\n"
        + "  key: <+input>.allowedValues(1.2,3.4,5.6)\n";
    String runtime = "stage:\n"
        + "  key: 1.1\n";
    assertThatThrownBy(() -> mergeRuntimeInputValuesIntoOriginalYaml(base, runtime, true))
        .isInstanceOf(InvalidRequestException.class);

    String correctRuntime = "stage:\n"
        + "  key: \"1.2\"\n";
    String merged = "stage:\n"
        + "  key: \"1.2.allowedValues(1.2,3.4,5.6)\"\n";
    assertThat(mergeRuntimeInputValuesIntoOriginalYaml(base, correctRuntime, true)).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithAllowedValuesAndExecutionInput() {
    String base = "stage:\n"
        + "  key: <+input>.allowedValues(a,b,c)\n";
    String correctRuntime = "stage:\n"
        + "  key: \"<+input>.executionInput()\"\n";
    String merged = "stage:\n"
        + "  key: \"<+input>.allowedValues(a,b,c).executionInput()\"\n";
    assertThat(mergeRuntimeInputValuesIntoOriginalYaml(base, correctRuntime, true)).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithAllowedValuesInTemplate() {
    String base = "stage:\n"
        + "  template:\n"
        + "    key: <+input>.allowedValues(a,b,c)\n";
    String runtime = "stage:\n"
        + "  template:\n"
        + "    key: d\n";
    assertThatThrownBy(() -> mergeRuntimeInputValuesIntoOriginalYaml(base, runtime, true))
        .isInstanceOf(InvalidRequestException.class);

    String correctRuntime = "stage:\n"
        + "  template:\n"
        + "    key: b\n";
    String merged = "stage:\n"
        + "  template:\n"
        + "    key: \"b.allowedValues(a,b,c)\"\n";
    assertThat(mergeRuntimeInputValuesIntoOriginalYaml(base, correctRuntime, true)).isEqualTo(merged);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeForServiceYaml() {
    String base = "service:\n"
        + "  field: leaf\n";
    String runtime = "service:\n"
        + "  field: <+input>\n";
    String merged = MergeHelper.mergeInputSetFormatYamlToOriginYaml(base, runtime);
    assertThat(merged).isEqualTo(base);

    base = "service:\n"
        + "  field:\n"
        + "    leaf: correct\n";
    runtime = "service:\n"
        + "  field: <+input>\n";
    merged = MergeHelper.mergeInputSetFormatYamlToOriginYaml(base, runtime);
    assertThat(merged).isEqualTo(base);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckIfPipelineValueIsRuntime() {
    String base = "stage:\n"
        + "  field: leaf\n";
    String runtime = "stage:\n"
        + "  field: not to be there\n";
    YamlConfig baseConfig = new YamlConfig(base);
    YamlConfig runtimeConfig = new YamlConfig(runtime);
    String merged =
        MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(baseConfig, runtimeConfig, true, true)
            .getYaml();
    assertThat(merged).isEqualTo(base);

    merged = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(baseConfig, runtimeConfig, true, false)
                 .getYaml();
    assertThat(merged).isEqualTo(runtime);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeWithServiceAsAxisNameAndRuntimeInput() {
    String base = "stage:\n"
        + "  strategy:\n"
        + "    matrix:\n"
        + "      service: <+input>\n";
    String runtime = "stage:\n"
        + "  strategy:\n"
        + "    matrix:\n"
        + "      service:\n"
        + "        - name: svc1\n"
        + "        - name: svc2\n"
        + "        - name: svc3\n"
        + "        - name: svc4\n";
    String result = mergeRuntimeInputValuesIntoOriginalYaml(base, runtime, false);
    assertThat(result).isEqualTo(runtime);
  }
}
