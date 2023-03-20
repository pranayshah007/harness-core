/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.encryption.Scope;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.tasks.Task;

import software.wings.beans.TaskType;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class StepUtilsTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void shouldTestGenerateLogAbstractionsBasicFunctionality() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(Level.newBuilder().setIdentifier("runStep1").setGroup("group1").build())
            .build();

    LinkedHashMap<String, String> expectedLogAbstractionMap = new LinkedHashMap<>();
    expectedLogAbstractionMap.put("accountId", "accountId");
    expectedLogAbstractionMap.put("projectId", "projectId");
    expectedLogAbstractionMap.put("orgId", "orgId");
    expectedLogAbstractionMap.put("pipelineId", "pipelineId");
    expectedLogAbstractionMap.put("runSequence", "1");
    expectedLogAbstractionMap.put("level0", "runStep1");

    assertThat(StepUtils.generateLogAbstractions(ambiance)).isEqualTo(expectedLogAbstractionMap);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void shouldTestGenerateLogAbstractionsLastGroupFunctionality() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(Level.newBuilder().setIdentifier("runStep1").setGroup("group1").build())
            .addLevels(Level.newBuilder().setIdentifier("runStep2").setGroup("group2").build())
            .build();

    LinkedHashMap<String, String> expectedLogAbstractionMap = new LinkedHashMap<>();
    expectedLogAbstractionMap.put("accountId", "accountId");
    expectedLogAbstractionMap.put("projectId", "projectId");
    expectedLogAbstractionMap.put("orgId", "orgId");
    expectedLogAbstractionMap.put("pipelineId", "pipelineId");
    expectedLogAbstractionMap.put("runSequence", "1");
    expectedLogAbstractionMap.put("level0", "runStep1");

    assertThat(StepUtils.generateLogAbstractions(ambiance, "group1")).isEqualTo(expectedLogAbstractionMap);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void shouldTestGenerateLogAbstractionsStepRetryFunctionality() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(Level.newBuilder().setIdentifier("runStep1").setGroup("group1").build())
            .addLevels(Level.newBuilder().setIdentifier("runStep2").setGroup("group2").setRetryIndex(2).build())
            .build();

    LinkedHashMap<String, String> expectedLogAbstractionMap = new LinkedHashMap<>();
    expectedLogAbstractionMap.put("accountId", "accountId");
    expectedLogAbstractionMap.put("projectId", "projectId");
    expectedLogAbstractionMap.put("orgId", "orgId");
    expectedLogAbstractionMap.put("pipelineId", "pipelineId");
    expectedLogAbstractionMap.put("runSequence", "1");
    expectedLogAbstractionMap.put("level0", "runStep1");
    expectedLogAbstractionMap.put("level1", "runStep2_2");

    assertThat(StepUtils.generateLogAbstractions(ambiance)).isEqualTo(expectedLogAbstractionMap);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetStepStatus() {
    assertThat(StepUtils.getStepStatus(null)).isEqualTo(null);

    for (CommandExecutionStatus commandExecutionStatus : CommandExecutionStatus.values()) {
      // asserts that all values are registered with the method, else exception should be thrown
      assertThat(StepUtils.getStepStatus(commandExecutionStatus)).isNotNull();
    }
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetTaskSelectors() {
    assertThat(StepUtils.getTaskSelectors(null)).isEmpty();

    ParameterField<List<String>> listParameterField = new ParameterField<>();
    assertThat(StepUtils.getTaskSelectors(listParameterField)).isEmpty();

    listParameterField = ParameterField.createExpressionField(true, "<+input>", null, true);
    assertThat(StepUtils.getTaskSelectors(listParameterField)).isEmpty();

    listParameterField = ParameterField.createValueField(Collections.singletonList("abc"));
    List<TaskSelector> taskSelectors = StepUtils.getTaskSelectors(listParameterField);
    assertThat(taskSelectors).hasSize(1);
    assertThat(taskSelectors.get(0).getSelector()).isEqualTo("abc");

    listParameterField = ParameterField.createValueField(Arrays.asList("s1", "s2"));
    taskSelectors = StepUtils.getTaskSelectors(listParameterField);
    assertThat(taskSelectors).hasSize(2);
    assertThat(taskSelectors.get(0).getSelector()).isEqualTo("s1");
    assertThat(taskSelectors.get(1).getSelector()).isEqualTo("s2");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void getDelegateSelectorSetTest() {
    assertThat(StepUtils.getDelegateSelectorList(null)).isEmpty();
    ParameterField<List<String>> delegateSelectors =
        ParameterField.<List<String>>builder().value(Arrays.asList("primary", "secondary")).build();
    assertThat(StepUtils.getDelegateSelectorList(delegateSelectors)).containsExactlyInAnyOrder("primary", "secondary");
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    String defaultTimeout = "10s";
    ParameterField<String> timeout = new ParameterField<>();
    timeout.setValue("1s");
    long millis = StepUtils.getTimeoutMillis(timeout, defaultTimeout);
    assertEquals(millis, 1000L);
    millis = StepUtils.getTimeoutMillis(null, defaultTimeout);
    assertEquals(millis, 10000L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGenerateLogKeys() {
    LinkedHashMap<String, String> logAbstractionMap = new LinkedHashMap<>();
    List<String> units = new ArrayList<>();
    assertEquals(StepUtils.generateLogKeys(logAbstractionMap, units), Collections.emptyList());
    logAbstractionMap.put("key1", "val1");
    assertEquals(StepUtils.generateLogKeys(logAbstractionMap, units).get(0), "key1:val1");
    units.add("unit1");
    assertEquals(StepUtils.generateLogKeys(logAbstractionMap, units).get(0), "key1:val1-commandUnit:unit1");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testPrepareDelegateTaskInput() {
    String accountId = "AccountId";
    Task task = StepUtils.prepareDelegateTaskInput(accountId, TaskData.builder().build(), new LinkedHashMap<>());
    assertTrue(task instanceof HDelegateTask);
    assertEquals(((HDelegateTask) task).getAccountId(), accountId);
    assertNotNull(((HDelegateTask) task).getData());
    assertNotNull(((HDelegateTask) task).getLogStreamingAbstractions());
    assertNotNull(((HDelegateTask) task).getSetupAbstractions());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateStepResponseFromChildResponse() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("key1", StepResponseNotifyData.builder().nodeUuid("nodeUuid").status(Status.SUCCEEDED).build());
    StepResponse stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
    assertNull(stepResponse.getFailureInfo());
    assertEquals(stepResponse.getStatus(), Status.SUCCEEDED);
    responseDataMap.put("key2",
        StepResponseNotifyData.builder().failureInfo(FailureInfo.newBuilder().build()).status(Status.FAILED).build());
    stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
    assertNotNull(stepResponse.getFailureInfo());
    assertEquals(stepResponse.getStatus(), Status.FAILED);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCreateStepResponseFromChildResponseMultipleFailures() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("key1",
        StepResponseNotifyData.builder()
            .nodeUuid("nodeUuid")
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("message1")
                             .addFailureData(FailureData.newBuilder().setMessage("message1").build())
                             .build())
            .nodeExecutionEndTs(100L)
            .build());
    StepResponse stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
    responseDataMap.put("key2",
        StepResponseNotifyData.builder()
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("message2")
                             .addFailureData(FailureData.newBuilder().setMessage("message2").build())
                             .build())
            .status(Status.FAILED)
            .nodeExecutionEndTs(50L)
            .build());
    stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
    assertNotNull(stepResponse.getFailureInfo());
    assertEquals(stepResponse.getFailureInfo().getFailureDataCount(), 2);
    assertEquals(stepResponse.getStatus(), Status.FAILED);
    // message1 is the latest errorMessage.
    assertEquals(stepResponse.getFailureInfo().getErrorMessage(), "message1");

    responseDataMap.put("key3",
        StepResponseNotifyData.builder()
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("message3")
                             .addFailureData(FailureData.newBuilder().setMessage("message3").build())
                             .build())
            .status(Status.FAILED)
            .nodeExecutionEndTs(150L)
            .build());
    stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
    // message3 is the latest errorMessage.
    assertEquals(stepResponse.getFailureInfo().getErrorMessage(), "message3");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testPrepareTaskRequest() {
    String accountId = "accountId";
    String projectId = "projectId";
    String orgId = "orgId";
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .build();

    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .dockerConnectorDTO(DockerConnectorDTO.builder().dockerRegistryUrl("index.docker.hub").build())
            .build();

    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {
                                ArtifactTaskParameters.builder().attributes(dockerArtifactDelegateRequest).build()})
                            .taskType(TaskType.DOCKER_ARTIFACT_TASK_NG.name())
                            .build();
    TaskCategory taskCategory = TaskCategory.DELEGATE_TASK_V1;
    String stageId = "stageId";
    TaskRequest taskRequest =
        StepUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory, null, false, "taskName", null,
            Scope.ACCOUNT, EnvironmentType.NON_PROD, false, Collections.emptyList(), true, stageId);
    assertEquals(taskRequest.getDelegateTaskRequest().getRequest().getAccountId().getId(), accountId);
    assertEquals(taskRequest.getTaskCategory(), taskCategory);
    assertEquals(taskRequest.getDelegateTaskRequest().getRequest().getEmitEvent(), true);
    assertEquals(taskRequest.getDelegateTaskRequest().getRequest().getStageId(), stageId);
    assertNotNull(taskRequest.getDelegateTaskRequest());
    assertEquals(taskRequest.getDelegateTaskRequest().getTaskName(), "taskName");

    taskRequest = StepUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory, null, false, null,
        null, Scope.ACCOUNT, EnvironmentType.NON_PROD, false, Collections.emptyList(), false, null);
    assertEquals(taskRequest.getTaskCategory(), taskCategory);
    assertEquals(taskRequest.getDelegateTaskRequest().getRequest().getAccountId().getId(), accountId);
    assertNotNull(taskRequest.getDelegateTaskRequest());
    assertEquals(taskRequest.getDelegateTaskRequest().getTaskName(), taskData.getTaskType());
    assertEquals(taskRequest.getDelegateTaskRequest().getRequest().getDetails().getMode(), TaskMode.SYNC);

    taskData.setAsync(true);
    taskRequest = StepUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory, null, false, null,
        null, Scope.ACCOUNT, EnvironmentType.NON_PROD, false, Collections.emptyList(), false, null);
    assertEquals(taskRequest.getDelegateTaskRequest().getRequest().getAccountId().getId(), accountId);
    assertNotNull(taskRequest.getDelegateTaskRequest());
    assertEquals(taskRequest.getDelegateTaskRequest().getTaskName(), taskData.getTaskType());
    assertEquals(taskRequest.getDelegateTaskRequest().getRequest().getDetails().getMode(), TaskMode.ASYNC);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetDelegateSelectorsFromPipeline() throws IOException {
    ParameterField<List<TaskSelectorYaml>> delegateSelectorsFromPipeline =
        StepUtils.delegateSelectorsFromFqn(planCreationContext(), YAMLFieldNameConstants.PIPELINE);
    assertEquals(delegateSelectorsFromPipeline.getValue().get(0).getDelegateSelectors(), "selector_pipeline");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetDelegateSelectorsFromStage() throws IOException {
    ParameterField<List<TaskSelectorYaml>> delegateSelectorsFromStage =
        StepUtils.delegateSelectorsFromFqn(planCreationContext(), YAMLFieldNameConstants.STAGE);
    assertEquals(delegateSelectorsFromStage.getValue().get(0).getDelegateSelectors(), "selector_stage");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetDelegateSelectorsFromStepGroup() throws IOException {
    ParameterField<List<TaskSelectorYaml>> delegateSelectorsFromStepGroup =
        StepUtils.delegateSelectorsFromFqn(planCreationContext(), YAMLFieldNameConstants.STEP_GROUP);
    assertEquals(delegateSelectorsFromStepGroup.getValue().get(0).getDelegateSelectors(), "selector_step_group");
  }

  private PlanCreationContext planCreationContext() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_delegate_selectors.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stagesNodes = stagesYamlField.getNode().asArray();
    YamlField approvalStageField = stagesNodes.get(0).getField("stage");
    YamlField approvalSpecField = Objects.requireNonNull(approvalStageField).getNode().getField("spec");
    YamlField executionField = Objects.requireNonNull(approvalSpecField).getNode().getField("execution");

    YamlField stepGroupYamlField =
        executionField.getNode().getField("steps").getNode().asArray().get(0).getField("stepGroup");
    assertThat(stepGroupYamlField).isNotNull();

    YamlField stepsYamlField = Objects.requireNonNull(stepGroupYamlField).getNode().getField("steps");

    PlanCreationContext context = PlanCreationContext.builder().currentField(stepsYamlField).build();
    return context;
  }
}
