/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.schema.inputs;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.schema.inputs.beans.InputDetails;
import io.harness.yaml.schema.inputs.beans.SchemaInputType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class YamlInputUtilsTest extends CategoryTest {
  String yaml = "version: 1\n"
      + "kind: pipeline\n"
      + "spec:\n"
      + "  inputs:\n"
      + "    timeout:\n"
      + "      type: string\n"
      + "      desc: timeout of shell script step\n"
      + "      required: true\n"
      + "      default: 10m\n"
      + "      execution: false\n"
      + "      validator:\n"
      + "        allowed: \n"
      + "          - 10m\n"
      + "          - 20m\n"
      + "    script:\n"
      + "      type: string\n"
      + "      execution: true\n"
      + "      validator:\n"
      + "        regex: ^.*@harness.io\n"
      + "    jobParam:\n"
      + "      type: object\n"
      + "      execution: false\n"
      + "  stages:\n"
      + "    - type: custom\n"
      + "      name: s1\n"
      + "      spec:\n"
      + "        steps:\n"
      + "          - type: shell-script\n"
      + "            name: shell_script_1\n"
      + "            identifier: shell_script_1\n"
      + "            spec:\n"
      + "              shell: bash\n"
      + "              onDelegate: true\n"
      + "              source:\n"
      + "                type: inline\n"
      + "                spec:\n"
      + "                  script: <+inputs.script>\n"
      + "            timeout: <+inputs.timeout>\n"
      + "          - type: JenkinsBuild\n"
      + "            name: JenkinsBuild_1\n"
      + "            identifier: JenkinsBuild_1\n"
      + "            spec:\n"
      + "              consoleLogPollFrequency: 5s\n"
      + "              jobParameter: <+inputs.jobParam>\n"
      + "              unstableStatusAsSuccess: false\n"
      + "              useConnectorUrlForJobExecution: false\n"
      + "            timeout: 10m";
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetYamlInputList() {
    List<InputDetails> inputDetailsList = YamlInputUtils.getYamlInputList(yaml);
    assertThat(inputDetailsList).isNotNull();
    Map<String, InputDetails> inputDetailsMap = new HashMap<>();
    inputDetailsList.forEach(inputDetails -> inputDetailsMap.put(inputDetails.getName(), inputDetails));

    InputDetails inputDetails = inputDetailsMap.get("script");
    assertThat(inputDetails.getRegex()).isEqualTo("^.*@harness.io");
    assertThat(inputDetails.getType()).isEqualTo(SchemaInputType.STRING);
    assertThat(inputDetails.getExecution()).isTrue();
    assertThat(inputDetails.isRequired()).isFalse();
    assertThat(inputDetails.getAllowedValues()).isNull();

    inputDetails = inputDetailsMap.get("timeout");
    assertThat(inputDetails.getAllowedValues().size()).isEqualTo(2);
    assertThat(inputDetails.getAllowedValues().contains("10m")).isTrue();
    assertThat(inputDetails.getAllowedValues().contains("20m")).isTrue();
    assertThat(inputDetails.getDescription()).isEqualTo("timeout of shell script step");
    assertThat(inputDetails.isRequired()).isTrue();
    assertThat(inputDetails.getExecution()).isFalse();

    inputDetails = inputDetailsMap.get("jobParam");
    assertThat(inputDetails.getDescription()).isNull();
    assertThat(inputDetails.getRegex()).isNull();
    assertThat(inputDetails.getExecution()).isFalse();
  }
}
