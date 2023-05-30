/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.ArrayList;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionInputServiceHelperlTest extends OrchestrationTestBase {
  @InjectMocks ExecutionInputServiceHelper executionInputServiceHelper;
  ObjectMapper objectMapper = new YAMLMapper();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testContinueExecution() throws JsonProcessingException {
    String template =
        "{\"step\":{\"identifier\":\"ss\",\"type\":\"ShellScript\",\"spec\":{\"source\":{\"type\":\"Inline\",\"spec\":{\"script\":\"<+input>.executionInput()\",\"temp\":\"<+input>.executionInput()\"}}}}}";

    String inputYaml = "step:\n"
        + "  identifier: \"ss\"\n"
        + "  type: \"ShellScript\"\n"
        + "  spec:\n"
        + "    source:\n"
        + "      type: \"Inline\"\n"
        + "      spec:\n"
        + "        script: \"echo Hi\"\n"
        + "        temp: \"tempValue\"\n";

    JsonNode inputJsonNode = objectMapper.readTree(inputYaml);
    Map<String, Object> responseMap =
        executionInputServiceHelper.getExecutionInputMap(YamlUtils.readAsJsonNode(template), inputJsonNode);
    assertEquals(getValueInMap("step.spec.source.spec.script", responseMap), "echo Hi");

    template = "stage:\n"
        + "  identifier: \"matrixWithParallel\"\n"
        + "  type: \"Approval\"\n"
        + "  variables:\n"
        + "  - name: \"var1\"\n"
        + "    type: \"String\"\n"
        + "    value: \"<+input>.executionInput()\"\n"
        + "  - name: \"var2\"\n"
        + "    type: \"Number\"\n"
        + "    value: \"<+input>.executionInput()\"\n"
        + "  - name: \"var3\"\n"
        + "    type: \"Boolean\"\n"
        + "    value: \"<+input>.executionInput()\"\n";
    inputYaml = "stage:\n"
        + "  identifier: \"matrixWithParallel\"\n"
        + "  type: \"Approval\"\n"
        + "  variables:\n"
        + "  - name: \"var1\"\n"
        + "    type: \"String\"\n"
        + "    value: \"echo ExecutionInputValue\"\n"
        + "  - name: \"var2\"\n"
        + "    type: \"Number\"\n"
        + "    value: 1.2\n"
        + "  - name: \"var3\"\n"
        + "    type: \"Boolean\"\n"
        + "    value: true\n";

    inputJsonNode = objectMapper.readTree(inputYaml);
    responseMap = executionInputServiceHelper.getExecutionInputMap(YamlUtils.readAsJsonNode(template), inputJsonNode);
    assertEquals(getValueInMap("stage.variables.var1", responseMap), "echo ExecutionInputValue");
    assertEquals(getValueInMap("stage.variables.var2", responseMap), 1.2);
    assertEquals(getValueInMap("stage.variables.var3", responseMap), true);

    // Testing the list as execution input value.
    template = "step:\n"
        + "  identifier: \"approval_step\"\n"
        + "  type: \"HarnessApproval\"\n"
        + "  spec:\n"
        + "    approvers:\n"
        + "      userGroups: \"<+input>.executionInput()\"\n";
    inputYaml = "step:\n"
        + "  identifier: approval_step\n"
        + "  type: HarnessApproval\n"
        + "  spec:\n"
        + "    approvers:\n"
        + "      userGroups:\n"
        + "        - account.LocalUserGroup\n";
    inputJsonNode = objectMapper.readTree(inputYaml);
    responseMap = executionInputServiceHelper.getExecutionInputMap(YamlUtils.readAsJsonNode(template), inputJsonNode);
    assertTrue(getValueInMap("step.spec.approvers.userGroups", responseMap) instanceof ArrayList);
    assertEquals(((ArrayList<?>) getValueInMap("step.spec.approvers.userGroups", responseMap)).size(), 1);
    assertEquals(
        ((ArrayList<?>) getValueInMap("step.spec.approvers.userGroups", responseMap)).get(0), "account.LocalUserGroup");
  }

  private Object getValueInMap(String fqn, Map<String, Object> map) {
    String[] fqnComponents = fqn.split("\\.");
    for (int i = 0; i < fqnComponents.length - 1; i++) {
      map = (Map<String, Object>) map.get(fqnComponents[i]);
    }
    return map.get(fqnComponents[fqnComponents.length - 1]);
  }
}
