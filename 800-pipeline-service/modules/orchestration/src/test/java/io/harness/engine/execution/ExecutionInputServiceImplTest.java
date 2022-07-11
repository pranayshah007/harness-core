/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.ExecutionInputRepository;
import io.harness.repositories.ExecutionInputRepositoryCustomImpl;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
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
  @Mock private ExecutionInputRepository executionInputRepository;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks ExecutionInputRepositoryCustomImpl executionInputRepositoryCustom;
  @InjectMocks private ExecutionInputServiceImpl inputService;
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
        .when(executionInputRepository)
        .findByNodeExecutionId(nodeExecutionId);
    ExecutionInputInstance inputInstance = inputService.getExecutionInputInstance(nodeExecutionId);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getInputInstanceId(), inputInstanceId);
    assertEquals(inputInstance.getTemplate(), template);

    doReturn(Optional.empty()).when(executionInputRepository).findByNodeExecutionId("differentNodeExecutionId");
    assertThatThrownBy(() -> inputService.getExecutionInputInstance("differentNodeExecutionId"))
        .isInstanceOf(InvalidRequestException.class);
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
    doReturn(Optional.of(ExecutionInputInstance.builder().inputInstanceId(inputInstanceId).template(template).build()))
        .when(executionInputRepository)
        .findByNodeExecutionId(nodeExecutionId);
    Map<String, Object> fullExecutionInputYamlMap = getMapFromYaml(fullExecutionInputYaml);
    Map<String, Object> mergedTemplateForPartialInputMap = getMapFromYaml(mergedTemplateForPartialInput);

    ExecutionInputInstance executionInputInstance1 = ExecutionInputInstance.builder()
                                                         .mergedInputTemplate(fullExecutionInputYamlMap)
                                                         .inputInstanceId(inputInstanceId)
                                                         .template(template)
                                                         .build();
    doReturn(executionInputInstance1).when(executionInputRepository).save(any());

    ArgumentCaptor<ExecutionInputInstance> inputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInputInstance.class);
    ArgumentCaptor<String> inputInstanceIdCapture = ArgumentCaptor.forClass(String.class);
    // Providing executionInputYaml. It will be merged with user input template.
    assertTrue(inputService.continueExecution(nodeExecutionId, fullExecutionInputYaml));
    verify(executionInputRepository, times(1)).save(inputInstanceArgumentCaptor.capture());
    verify(waitNotifyEngine, times(1)).doneWith(inputInstanceIdCapture.capture(), any());

    ExecutionInputInstance savedEntity = inputInstanceArgumentCaptor.getValue();
    // MergedTemplate would be equals to executionInputYaml.
    assertEquals(savedEntity.getMergedInputTemplate(), fullExecutionInputYamlMap);
    // waitNotifyEngine.doneWith() should be called with inputInstanceId.
    assertEquals(inputInstanceIdCapture.getValue(), inputInstanceId);

    // Providing invalid input values. Would return false.
    assertFalse(inputService.continueExecution(nodeExecutionId, "a:b"));

    // Giving partial user input. MergedUserInput should contain provided field's value and other value should be
    // expression.
    assertTrue(inputService.continueExecution(nodeExecutionId, partialExecutionInputYaml));
    verify(executionInputRepository, times(2)).save(inputInstanceArgumentCaptor.capture());
    verify(waitNotifyEngine, times(2)).doneWith(inputInstanceIdCapture.capture(), any());

    savedEntity = inputInstanceArgumentCaptor.getValue();
    assertEquals(savedEntity.getMergedInputTemplate(), mergedTemplateForPartialInputMap);
    assertEquals(inputInstanceIdCapture.getValue(), inputInstanceId);

    // Should throw exception when InputInstanceId does not exist.
    doReturn(Optional.empty()).when(executionInputRepository).findByNodeExecutionId(nodeExecutionId);
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
        .when(executionInputRepository)
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
    doReturn(executionInputInstance).when(executionInputRepository).save(executionInputInstance);
    assertEquals(inputService.save(executionInputInstance), executionInputInstance);
  }

  private Map<String, Object> getMapFromYaml(String yaml) throws JsonProcessingException {
    return RecastOrchestrationUtils.fromJson(objectMapper.readTree(yaml).toString());
  }
}
