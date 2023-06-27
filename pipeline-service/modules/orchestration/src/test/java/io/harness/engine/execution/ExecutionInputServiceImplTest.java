/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.ExecutionInputRepository;
import io.harness.repositories.ExecutionInputRepositoryCustomImpl;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionInputServiceImplTest extends OrchestrationTestBase {
  @Mock private ExecutionInputRepository executionInputRepositoryMock;
  @Inject private ExecutionInputRepository executionInputRepository;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock ExecutionInputServiceHelper executionInputServiceHelper;
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks ExecutionInputRepositoryCustomImpl executionInputRepositoryCustom;
  @InjectMocks private ExecutionInputServiceImpl inputService;
  @Mock PmsEngineExpressionService pmsEngineExpressionService;
  @Mock NodeExecutionService nodeExecutionService;
  ObjectMapper objectMapper = new YAMLMapper();
  String nodeExecutionId = "nodeExecutionId";
  String inputInstanceId = "inputInstanceId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetExecutionInputInstance() {
    String template = "template";
    doReturn(Optional.of(ExecutionInputInstance.builder()
                             .inputInstanceId(inputInstanceId)
                             .nodeExecutionId(nodeExecutionId)
                             .template(template)
                             .build()))
        .when(executionInputRepositoryMock)
        .findByNodeExecutionId(nodeExecutionId);
    ExecutionInputInstance inputInstance = inputService.getExecutionInputInstance(nodeExecutionId);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getInputInstanceId(), inputInstanceId);
    assertEquals(inputInstance.getTemplate(), template);

    doReturn(Optional.empty()).when(executionInputRepositoryMock).findByNodeExecutionId("differentNodeExecutionId");
    assertNull(inputService.getExecutionInputInstance("differentNodeExecutionId"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testContinueExecution() throws JsonProcessingException {
    String template =
        "{\"step\":{\"identifier\":\"ss\",\"type\":\"ShellScript\",\"spec\":{\"source\":{\"type\":\"Inline\",\"spec\":{\"script\":\"<+input>.executionInput()\",\"temp\":\"<+input>.executionInput()\"}}}}}";

    String fullExecutionInputYaml = "step:\n"
        + "  identifier: \"ss\"\n"
        + "  type: \"ShellScript\"\n"
        + "  spec:\n"
        + "    source:\n"
        + "      type: \"Inline\"\n"
        + "      spec:\n"
        + "        script: \"echo Hi\"\n"
        + "        temp: \"tempValue\"\n";

    String partialExecutionInputYaml = "step:\n"
        + "  identifier: \"ss\"\n"
        + "  type: \"ShellScript\"\n"
        + "  spec:\n"
        + "    source:\n"
        + "      type: \"Inline\"\n"
        + "      spec:\n"
        + "        script: \"echo Hi\"\n";
    String mergedTemplateForPartialInput = "step:\n"
        + "  identifier: \"ss\"\n"
        + "  type: \"ShellScript\"\n"
        + "  spec:\n"
        + "    source:\n"
        + "      type: \"Inline\"\n"
        + "      spec:\n"
        + "        script: \"echo Hi\"\n"
        + "        temp: \"<+input>.executionInput()\"\n";
    doReturn(
        Optional.of(
            ExecutionInputInstance.builder().inputInstanceId(inputInstanceId).template(template).fieldYaml("").build()))
        .when(executionInputRepositoryMock)
        .findByNodeExecutionId(nodeExecutionId);
    Map<String, Object> fullExecutionInputYamlMap = getMapFromYaml(fullExecutionInputYaml);
    Map<String, Object> mergedTemplateForPartialInputMap = getMapFromYaml(mergedTemplateForPartialInput);

    ExecutionInputInstance executionInputInstance1 = ExecutionInputInstance.builder()
                                                         .mergedInputTemplate(fullExecutionInputYamlMap)
                                                         .inputInstanceId(inputInstanceId)
                                                         .template(template)
                                                         .build();
    doReturn(executionInputInstance1).when(executionInputRepositoryMock).save(any());
    doReturn(fullExecutionInputYamlMap)
        .when(executionInputServiceHelper)
        .getExecutionInputMap(eq(YamlUtils.readAsJsonNode(template)), any());
    doReturn(NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbianceAndStatus);
    doReturn(YamlUtils.readAsJsonNode(fullExecutionInputYaml))
        .when(pmsEngineExpressionService)
        .resolve(any(), any(), any());

    ArgumentCaptor<ExecutionInputInstance> inputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInputInstance.class);
    ArgumentCaptor<String> inputInstanceIdCapture = ArgumentCaptor.forClass(String.class);
    // Providing executionInputYaml. It will be merged with user input template.
    assertTrue(inputService.continueExecution(nodeExecutionId, fullExecutionInputYaml));
    verify(executionInputRepositoryMock, times(1)).save(inputInstanceArgumentCaptor.capture());
    verify(waitNotifyEngine, times(1)).doneWith(inputInstanceIdCapture.capture(), any());
    verify(pmsEngineExpressionService, times(1)).resolve(any(), any(), any());

    ExecutionInputInstance savedEntity = inputInstanceArgumentCaptor.getValue();
    // MergedTemplate would be equals to executionInputYaml.
    assertEquals(savedEntity.getMergedInputTemplate(), fullExecutionInputYamlMap);
    // waitNotifyEngine.doneWith() should be called with inputInstanceId.
    assertEquals(inputInstanceIdCapture.getValue(), inputInstanceId);

    // Providing invalid input values. Would return false.
    doReturn(YamlUtils.readAsJsonNode("a:b")).when(pmsEngineExpressionService).resolve(any(), any(), any());
    assertFalse(inputService.continueExecution(nodeExecutionId, "a:b"));

    // Giving partial user input. MergedUserInput should contain provided field's value and other value should be
    // expression.
    doReturn(mergedTemplateForPartialInputMap)
        .when(executionInputServiceHelper)
        .getExecutionInputMap(eq(YamlUtils.readAsJsonNode(template)), any());

    doReturn(YamlUtils.readAsJsonNode(partialExecutionInputYaml))
        .when(pmsEngineExpressionService)
        .resolve(any(), any(), any());
    assertTrue(inputService.continueExecution(nodeExecutionId, partialExecutionInputYaml));
    verify(executionInputRepositoryMock, times(2)).save(inputInstanceArgumentCaptor.capture());
    verify(waitNotifyEngine, times(2)).doneWith(inputInstanceIdCapture.capture(), any());

    savedEntity = inputInstanceArgumentCaptor.getValue();
    assertEquals(savedEntity.getMergedInputTemplate(), mergedTemplateForPartialInputMap);
    assertEquals(inputInstanceIdCapture.getValue(), inputInstanceId);

    // Should throw exception when InputInstanceId does not exist.
    doReturn(Optional.empty()).when(executionInputRepositoryMock).findByNodeExecutionId(nodeExecutionId);
    assertThatThrownBy(() -> inputService.continueExecution(nodeExecutionId, fullExecutionInputYaml))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetExecutionInputInstances() {
    Set<String> nodeExecutionIds = new HashSet<>();
    nodeExecutionIds.add("id1");
    nodeExecutionIds.add("id2");
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    on(executionInputRepositoryCustom).set("mongoTemplate", mongoTemplate);
    doReturn(
        Arrays.asList(ExecutionInputInstance.builder().nodeExecutionId("id1").inputInstanceId("instanceId1").build(),
            ExecutionInputInstance.builder().nodeExecutionId("id2").inputInstanceId("instanceId2").build()))
        .when(mongoTemplate)
        .find(any(), eq(ExecutionInputInstance.class));
    doReturn(executionInputRepositoryCustom.findByNodeExecutionIds(nodeExecutionIds))
        .when(executionInputRepositoryMock)
        .findByNodeExecutionIds(nodeExecutionIds);
    List<ExecutionInputInstance> executionInputInstances = inputService.getExecutionInputInstances(nodeExecutionIds);

    verify(mongoTemplate, times(1)).find(queryArgumentCaptor.capture(), any());

    Query query = queryArgumentCaptor.getValue();
    assertEquals(
        query.toString(), "Query: { \"nodeExecutionId\" : { \"$in\" : [\"id2\", \"id1\"]}}, Fields: {}, Sort: {}");
    assertEquals(executionInputInstances.size(), 2);
    assertEquals(executionInputInstances.get(0).getNodeExecutionId(), "id1");
    assertEquals(executionInputInstances.get(1).getNodeExecutionId(), "id2");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSave() {
    ExecutionInputInstance executionInputInstance = ExecutionInputInstance.builder().build();
    doReturn(executionInputInstance).when(executionInputRepositoryMock).save(executionInputInstance);
    assertEquals(inputService.save(executionInputInstance), executionInputInstance);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteExecutionInputInstance() {
    on(inputService).set("executionInputRepository", executionInputRepository);

    String nodeExecutionId = generateUuid();
    ExecutionInputInstance executionInputInstance =
        ExecutionInputInstance.builder().inputInstanceId(generateUuid()).nodeExecutionId(nodeExecutionId).build();
    inputService.save(executionInputInstance);
    inputService.deleteExecutionInputInstanceForGivenNodeExecutionIds(Sets.newHashSet(nodeExecutionId));

    ExecutionInputInstance expectedInputInstance = inputService.getExecutionInputInstance(nodeExecutionId);
    assertThat(expectedInputInstance).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckValueForRequiredVariablesProvided() {
    String executionInputYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      value: \"1234\"\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: \"\"\n";

    String fieldYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  name: cs\n"
        + "  description: \"\"\n"
        + "  spec:\n"
        + "    execution:\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            identifier: ShellScript_1\n"
        + "            type: ShellScript\n"
        + "            name: ShellScript_1\n"
        + "            spec:\n"
        + "              shell: Bash\n"
        + "              onDelegate: true\n"
        + "              source:\n"
        + "                type: Inline\n"
        + "                spec:\n"
        + "                  script: <+input>.executionInput().default(exit 0)\n"
        + "            timeout: 10m\n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      default: xyz\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput().default(1234)\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: false\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var3\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: value3\n";

    JsonNode executionInputNode = YamlUtils.readAsJsonNode(executionInputYaml);
    assertThatCode(() -> inputService.checkValueForRequiredVariablesProvided(fieldYaml, executionInputNode, false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckValueForRequiredVariablesProvided2() {
    String executionInputYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      value: \"\"\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: \"value2\"\n";

    String fieldYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  name: cs\n"
        + "  description: \"\"\n"
        + "  spec:\n"
        + "    execution:\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            identifier: ShellScript_1\n"
        + "            type: ShellScript\n"
        + "            name: ShellScript_1\n"
        + "            spec:\n"
        + "              shell: Bash\n"
        + "              onDelegate: true\n"
        + "              source:\n"
        + "                type: Inline\n"
        + "                spec:\n"
        + "                  script: <+input>.executionInput().default(exit 0)\n"
        + "            timeout: 10m\n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      default: xyz\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput().default(1234)\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: false\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var3\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: value3\n";

    JsonNode executionInputNode = YamlUtils.readAsJsonNode(executionInputYaml);
    assertThatThrownBy(() -> inputService.checkValueForRequiredVariablesProvided(fieldYaml, executionInputNode, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("var1 is a required variable .Value or expression not provided for the variable : var1");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckValueForRequiredVariablesProvided3() {
    String executionInputYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      value: \"<+input>.executionInput()\"\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: \"value2\"\n";

    String fieldYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  name: cs\n"
        + "  description: \"\"\n"
        + "  spec:\n"
        + "    execution:\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            identifier: ShellScript_1\n"
        + "            type: ShellScript\n"
        + "            name: ShellScript_1\n"
        + "            spec:\n"
        + "              shell: Bash\n"
        + "              onDelegate: true\n"
        + "              source:\n"
        + "                type: Inline\n"
        + "                spec:\n"
        + "                  script: <+input>.executionInput().default(exit 0)\n"
        + "            timeout: 10m\n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      default: xyz\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var3\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: value3\n";

    JsonNode executionInputNode = YamlUtils.readAsJsonNode(executionInputYaml);
    assertThatThrownBy(() -> inputService.checkValueForRequiredVariablesProvided(fieldYaml, executionInputNode, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "var1 is a required variable .Default value is empty or not provided for the variable : var1 or the execution input yaml provided by user is empty");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckValueForRequiredVariablesProvided4() {
    String executionInputYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      value: \"<+input>.executionInput()\"\n";

    String fieldYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  name: cs\n"
        + "  description: \"\"\n"
        + "  spec:\n"
        + "    execution:\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            identifier: ShellScript_1\n"
        + "            type: ShellScript\n"
        + "            name: ShellScript_1\n"
        + "            spec:\n"
        + "              shell: Bash\n"
        + "              onDelegate: true\n"
        + "              source:\n"
        + "                type: Inline\n"
        + "                spec:\n"
        + "                  script: <+input>.executionInput().default(exit 0)\n"
        + "            timeout: 10m\n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      default: xyz\n"
        + "      description: \"\"\n"
        + "      required: false\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var3\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: value3\n";

    JsonNode executionInputNode = YamlUtils.readAsJsonNode(executionInputYaml);
    assertThatThrownBy(() -> inputService.checkValueForRequiredVariablesProvided(fieldYaml, executionInputNode, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("var2 is a required variable .Value or expression not provided for the variable : var2");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckValueForRequiredVariablesProvided5() {
    String executionInputYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      value: \"<+input>.executionInput()\"\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: \"<+input>.executionInput().default(1234)\"\n";

    String fieldYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  name: cs\n"
        + "  description: \"\"\n"
        + "  spec:\n"
        + "    execution:\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            identifier: ShellScript_1\n"
        + "            type: ShellScript\n"
        + "            name: ShellScript_1\n"
        + "            spec:\n"
        + "              shell: Bash\n"
        + "              onDelegate: true\n"
        + "              source:\n"
        + "                type: Inline\n"
        + "                spec:\n"
        + "                  script: <+input>.executionInput().default(exit 0)\n"
        + "            timeout: 10m\n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      default: xyz\n"
        + "      description: \"\"\n"
        + "      required: false\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput().default(1234)\n";

    JsonNode executionInputNode = YamlUtils.readAsJsonNode(executionInputYaml);
    assertThatCode(() -> inputService.checkValueForRequiredVariablesProvided(fieldYaml, executionInputNode, true))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckValueForRequiredVariablesProvided6() {
    String executionInputYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      value: \"<+input>.executionInput()\"\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: \"<+input>.executionInput().default(1234)\"\n";

    String fieldYaml = "stage:\n"
        + "  identifier: cs\n"
        + "  type: Custom\n"
        + "  name: cs\n"
        + "  description: \"\"\n"
        + "  spec:\n"
        + "    execution:\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            identifier: ShellScript_1\n"
        + "            type: ShellScript\n"
        + "            name: ShellScript_1\n"
        + "            spec:\n"
        + "              shell: Bash\n"
        + "              onDelegate: true\n"
        + "              source:\n"
        + "                type: Inline\n"
        + "                spec:\n"
        + "                  script: <+input>.executionInput().default(exit 0)\n"
        + "            timeout: 10m\n"
        + "  tags: {}\n"
        + "  variables:\n"
        + "    - name: var1\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput()\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      required: true\n"
        + "      value: <+input>.executionInput().default(1234)\n";

    JsonNode executionInputNode = YamlUtils.readAsJsonNode(executionInputYaml);
    assertThatThrownBy(() -> inputService.checkValueForRequiredVariablesProvided(fieldYaml, executionInputNode, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "var1 is a required variable .Default value is empty or not provided for the variable : var1 or the execution input yaml provided by user is empty");
  }

  private Map<String, Object> getMapFromYaml(String yaml) throws JsonProcessingException {
    return RecastOrchestrationUtils.fromJson(objectMapper.readTree(yaml).toString());
  }
}
