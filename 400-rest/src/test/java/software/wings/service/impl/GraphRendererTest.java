/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.DISCONTINUING;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.NEW;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.PAUSING;
import static io.harness.beans.ExecutionStatus.QUEUED;
import static io.harness.beans.ExecutionStatus.RESUMED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SCHEDULED;
import static io.harness.beans.ExecutionStatus.STARTING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.VINICIUS;

import static software.wings.beans.ExecutionStrategy.PARALLEL;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.COMMAND;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;
import static software.wings.sm.StateType.REPEAT;

import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ExecutionDataValue;
import software.wings.api.HostElement;
import software.wings.beans.GraphGroup;
import software.wings.beans.GraphNode;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.states.RepeatState.RepeatStateExecutionData;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
public class GraphRendererTest extends WingsBaseTest {
  @Inject @InjectMocks GraphRenderer graphRenderer;
  @Mock FeatureFlagService featureFlagService;
  @Mock AppService appService;
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock DelegateSelectionLogsService delegateSelectionLogsService;
  @Mock Injector injector;

  @Before
  public void setup() {
    when(workflowExecutionService.getExecutionInterruptCount(any())).thenReturn(2);
    when(appService.getAccountIdByAppId(any())).thenReturn("ACCOUNT_ID");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testIsSubWorkflow() {
    assertThat(GraphRenderer.isSubWorkflow(null)).isFalse();
    assertThat(GraphRenderer.isSubWorkflow(aStateExecutionInstance().build())).isFalse();
    assertThat(GraphRenderer.isSubWorkflow(aStateExecutionInstance().stateType("SUB_WORKFLOW").build())).isTrue();
    assertThat(GraphRenderer.isSubWorkflow(aStateExecutionInstance().stateType("PHASE").build())).isTrue();
    assertThat(GraphRenderer.isSubWorkflow(aStateExecutionInstance().stateType("PHASE_STEP").build())).isTrue();
    assertThat(GraphRenderer.isSubWorkflow(aStateExecutionInstance().stateType("COMMAND").build())).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSanity() {
    List<StateExecutionInstance> stateExecutionInstances = asList(aStateExecutionInstance()
                                                                      .displayName("origin")
                                                                      .uuid(generateUuid())
                                                                      .stateType("PHASE")
                                                                      .status(SUCCESS)
                                                                      .build());
    Map<String, StateExecutionInstance> stateExecutionInstanceMap =
        stateExecutionInstances.stream().collect(toMap(StateExecutionInstance::getUuid, identity()));

    final GraphNode node = graphRenderer.generateHierarchyNode(stateExecutionInstanceMap);
    assertThat(node).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGenerateHierarchyNode() {
    final StateExecutionInstance parent = aStateExecutionInstance()
                                              .displayName("Deploy Service")
                                              .uuid("deploy")
                                              .stateType(PHASE.name())
                                              .contextTransition(true)
                                              .status(SUCCESS)
                                              .build();

    final StateExecutionInstance repeat = aStateExecutionInstance()
                                              .displayName("Repeat deploy on hosts")
                                              .uuid("repeat")
                                              .stateType(REPEAT.name())
                                              .contextTransition(true)
                                              .status(SUCCESS)
                                              .parentInstanceId(parent.getUuid())
                                              .build();

    final StateExecutionInstance host1 = aStateExecutionInstance()
                                             .displayName("install on host1")
                                             .uuid("host1")
                                             .stateType(COMMAND.name())
                                             .contextElement(HostElement.builder().hostName("host1").build())
                                             .contextTransition(true)
                                             .status(SUCCESS)
                                             .parentInstanceId(repeat.getUuid())
                                             .build();

    final StateExecutionInstance host2 = aStateExecutionInstance()
                                             .displayName("install on host 2")
                                             .uuid("host2")
                                             .stateType(COMMAND.name())
                                             .contextElement(HostElement.builder().hostName("host2").build())
                                             .contextTransition(true)
                                             .status(SUCCESS)
                                             .parentInstanceId(repeat.getUuid())
                                             .build();

    List<StateExecutionInstance> stateExecutionInstances = asList(parent, repeat, host1, host2);
    Map<String, StateExecutionInstance> stateExecutionInstanceMap =
        stateExecutionInstances.stream().collect(toMap(StateExecutionInstance::getUuid, identity()));

    final GraphNode node = graphRenderer.generateHierarchyNode(stateExecutionInstanceMap);
    assertThat(node).isNotNull();
    assertThat(node.getName()).isEqualTo(parent.getDisplayName());

    final GraphGroup deployGroup = node.getGroup();
    assertThat(deployGroup).isNotNull();

    final List<GraphNode> deployChildElements = deployGroup.getElements();
    assertThat(deployChildElements.size()).isEqualTo(1);
    assertThat(deployChildElements.get(0).getName()).isEqualTo(repeat.getDisplayName());

    final GraphGroup repeatGroup = deployChildElements.get(0).getGroup();
    assertThat(repeatGroup).isNotNull();

    final List<GraphNode> repeatChildElements = repeatGroup.getElements();
    assertThat(repeatChildElements.size()).isEqualTo(2);
    assertThat(repeatChildElements.get(0).getName()).isEqualTo("host1");
    assertThat(repeatChildElements.get(1).getName()).isEqualTo("host2");

    assertThat(repeatChildElements.get(0).getNext().getName()).isEqualTo(host1.getDisplayName());
    assertThat(repeatChildElements.get(1).getNext().getName()).isEqualTo(host2.getDisplayName());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testConvertToNode() {
    String accountId = generateUuid();
    final StateExecutionInstance instance = aStateExecutionInstance()
                                                .accountId(accountId)
                                                .displayName("state name")
                                                .uuid("uuid")
                                                .stateType(PHASE_STEP.name())
                                                .contextTransition(true)
                                                .status(SUCCESS)
                                                .build();
    String taskId = generateUuid();
    String delegateId = generateUuid();
    String delegateName = "name";
    String delegateHostname = "hostname";

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("key1", "value1");
    setupAbstractions.put("key2", "value2");

    List<DelegateTaskDetails> delegateTaskDetailsList = new ArrayList<>();
    delegateTaskDetailsList.add(DelegateTaskDetails.builder()
                                    .delegateTaskId(taskId)
                                    .taskDescription("description")
                                    .setupAbstractions(setupAbstractions)
                                    .build());

    DelegateSelectionLogParams delegateSelectionLogParams = DelegateSelectionLogParams.builder()
                                                                .delegateId(delegateId)
                                                                .delegateName(delegateName)
                                                                .delegateHostName(delegateHostname)
                                                                .build();
    when(delegateSelectionLogsService.fetchSelectedDelegateForTask(accountId, taskId))
        .thenReturn(Optional.of(delegateSelectionLogParams));

    instance.setStateParams(ImmutableMap.of("key", "value"));
    instance.setSelectionLogsTrackingForTasksEnabled(true);
    instance.setDelegateTasksDetails(delegateTaskDetailsList);

    GraphNode node = graphRenderer.convertToNode(instance);

    assertThat(node.getId()).isEqualTo(instance.getUuid());
    assertThat(node.getName()).isEqualTo(instance.getDisplayName());
    assertThat(node.getType()).isEqualTo(instance.getStateType());
    assertThat(node.isRollback()).isEqualTo(instance.isRollback());
    assertThat(node.getStatus()).isEqualTo(instance.getStatus().name());
    assertThat(node.getProperties()).isEqualTo(instance.getStateParams());
    assertThat(node.isSelectionLogsTrackingForTasksEnabled())
        .isEqualTo(instance.isSelectionLogsTrackingForTasksEnabled());
    assertThat(node.getDelegateTasksDetails()).isEqualTo(instance.getDelegateTasksDetails());

    instance.setDelegateTasksDetails(null);
    instance.setDelegateTaskId(taskId);

    node = graphRenderer.convertToNode(instance);
    assertThat(node.getDelegateTasksDetails()).isNotNull();
    assertThat(node.getDelegateTasksDetails().get(0))
        .isEqualTo(DelegateTaskDetails.builder().delegateTaskId(taskId).build());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAggregateStatus() {
    assertThat(GraphRenderer.aggregateStatus(asList(NEW, NEW, NEW))).isEqualTo(NEW);
    assertThat(GraphRenderer.aggregateStatus(asList(STARTING, STARTING, STARTING))).isEqualTo(STARTING);
    assertThat(GraphRenderer.aggregateStatus(asList(RUNNING, RUNNING, RUNNING))).isEqualTo(RUNNING);
    assertThat(GraphRenderer.aggregateStatus(asList(SUCCESS, SUCCESS, SUCCESS))).isEqualTo(SUCCESS);
    assertThat(GraphRenderer.aggregateStatus(asList(DISCONTINUING, DISCONTINUING, DISCONTINUING)))
        .isEqualTo(DISCONTINUING);
    assertThat(GraphRenderer.aggregateStatus(asList(ABORTED, ABORTED, ABORTED))).isEqualTo(ABORTED);
    assertThat(GraphRenderer.aggregateStatus(asList(FAILED, FAILED, FAILED))).isEqualTo(FAILED);
    assertThat(GraphRenderer.aggregateStatus(asList(QUEUED, QUEUED, QUEUED))).isEqualTo(QUEUED);
    assertThat(GraphRenderer.aggregateStatus(asList(SCHEDULED, SCHEDULED, SCHEDULED))).isEqualTo(SCHEDULED);
    assertThat(GraphRenderer.aggregateStatus(asList(ERROR, ERROR, ERROR))).isEqualTo(ERROR);
    assertThat(GraphRenderer.aggregateStatus(asList(WAITING, WAITING, WAITING))).isEqualTo(WAITING);
    assertThat(GraphRenderer.aggregateStatus(asList(PAUSING, PAUSING, PAUSING))).isEqualTo(PAUSING);
    assertThat(GraphRenderer.aggregateStatus(asList(PAUSED, PAUSED, PAUSED))).isEqualTo(PAUSED);
    assertThat(GraphRenderer.aggregateStatus(asList(RESUMED, RESUMED, RESUMED))).isEqualTo(RESUMED);

    assertThat(GraphRenderer.aggregateStatus(asList(NEW, STARTING, RUNNING, SUCCESS, DISCONTINUING, ABORTED, FAILED,
                   QUEUED, SCHEDULED, ERROR, WAITING, PAUSING, PAUSED, RESUMED)))
        .isEqualTo(WAITING);
    assertThat(GraphRenderer.aggregateStatus(asList(NEW, STARTING, RUNNING, SUCCESS, DISCONTINUING, ABORTED, FAILED,
                   QUEUED, SCHEDULED, ERROR, PAUSING, PAUSED, RESUMED)))
        .isEqualTo(PAUSED);
    assertThat(GraphRenderer.aggregateStatus(asList(NEW, STARTING, RUNNING, SUCCESS, DISCONTINUING, ABORTED, FAILED,
                   QUEUED, SCHEDULED, ERROR, PAUSING, RESUMED)))
        .isEqualTo(PAUSING);
    assertThat(GraphRenderer.aggregateStatus(asList(
                   NEW, STARTING, RUNNING, SUCCESS, DISCONTINUING, ABORTED, FAILED, QUEUED, SCHEDULED, ERROR, RESUMED)))
        .isEqualTo(RUNNING);
    assertThat(GraphRenderer.aggregateStatus(
                   asList(NEW, STARTING, SUCCESS, DISCONTINUING, ABORTED, FAILED, QUEUED, SCHEDULED, ERROR, RESUMED)))
        .isIn(STARTING, DISCONTINUING, QUEUED, SCHEDULED, RESUMED);
    assertThat(GraphRenderer.aggregateStatus(asList(NEW, SUCCESS, ABORTED, FAILED, ERROR))).isEqualTo(NEW);

    assertThat(GraphRenderer.aggregateStatus(asList(SUCCESS, ABORTED, FAILED, ERROR))).isEqualTo(ABORTED);
    assertThat(GraphRenderer.aggregateStatus(asList(SUCCESS, FAILED, ERROR))).isEqualTo(ERROR);
    assertThat(GraphRenderer.aggregateStatus(asList(SUCCESS, FAILED))).isEqualTo(FAILED);
    assertThat(GraphRenderer.aggregateStatus(asList(SUCCESS))).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAggregateStatusWithNull() {
    assertThat(GraphRenderer.aggregateStatus(asList(NEW, null, NEW))).isEqualTo(NEW);
    assertThat(GraphRenderer.aggregateStatus(asList(STARTING, null, STARTING))).isEqualTo(STARTING);
    assertThat(GraphRenderer.aggregateStatus(asList(null, RUNNING, RUNNING))).isEqualTo(RUNNING);
    assertThat(GraphRenderer.aggregateStatus(asList(SUCCESS, null, SUCCESS))).isEqualTo(NEW);

    assertThat(GraphRenderer.aggregateStatus(asList(null, NEW, STARTING, RUNNING, SUCCESS, DISCONTINUING, ABORTED,
                   FAILED, QUEUED, SCHEDULED, ERROR, WAITING, PAUSING, PAUSED, RESUMED)))
        .isEqualTo(WAITING);
    assertThat(GraphRenderer.aggregateStatus(asList(null, NEW, STARTING, RUNNING, SUCCESS, DISCONTINUING, ABORTED,
                   FAILED, QUEUED, SCHEDULED, ERROR, PAUSING, PAUSED, RESUMED)))
        .isEqualTo(PAUSED);
    assertThat(GraphRenderer.aggregateStatus(asList(null, NEW, SUCCESS, ABORTED, FAILED, ERROR))).isEqualTo(NEW);
    assertThat(GraphRenderer.aggregateStatus(asList(null, SUCCESS, ABORTED, FAILED, ERROR))).isEqualTo(NEW);
    assertThat(GraphRenderer.aggregateStatus(asList(SUCCESS, null))).isEqualTo(NEW);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testGenerateHierarchyNodeWithAggregation() {
    final StateExecutionInstance parent = aStateExecutionInstance()
                                              .displayName("Deploy Service")
                                              .uuid("deploy")
                                              .stateType(PHASE.name())
                                              .contextTransition(true)
                                              .status(SUCCESS)
                                              .build();

    RepeatStateExecutionData sed = new RepeatStateExecutionData();
    sed.setExecutionStrategy(PARALLEL);
    List<ContextElement> contextElements = new ArrayList<>();
    contextElements.add(HostElement.builder().hostName("host1").build());
    contextElements.add(HostElement.builder().hostName("host2").build());
    contextElements.add(HostElement.builder().hostName("host3").build());
    contextElements.add(HostElement.builder().hostName("host4").build());
    contextElements.add(HostElement.builder().hostName("host5").build());
    contextElements.add(HostElement.builder().hostName("host6").build());
    contextElements.add(HostElement.builder().hostName("host7").build());
    contextElements.add(HostElement.builder().hostName("host8").build());
    contextElements.add(HostElement.builder().hostName("host9").build());
    contextElements.add(HostElement.builder().hostName("host10").build());

    sed.setRepeatElements(contextElements);
    sed.setStatus(SUCCESS);
    Map<String, StateExecutionData> stateExecutionDataMap = new HashMap<>();
    stateExecutionDataMap.put("Repeat deploy on hosts", sed);

    final StateExecutionInstance repeat = aStateExecutionInstance()
                                              .displayName("Repeat deploy on hosts")
                                              .uuid("repeat")
                                              .stateType(REPEAT.name())
                                              .contextTransition(true)
                                              .stateExecutionMap(stateExecutionDataMap)
                                              .status(SUCCESS)
                                              .parentInstanceId(parent.getUuid())
                                              .build();
    List<StateExecutionInstance> stateExecutionInstances = new ArrayList<>();
    stateExecutionInstances.add(parent);
    stateExecutionInstances.add(repeat);

    addHostElements(stateExecutionInstances, "host1", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host2", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host3", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host4", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host5", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host6", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host7", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host8", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host9", repeat.getUuid(), SUCCESS);
    addHostElements(stateExecutionInstances, "host10", repeat.getUuid(), FAILED);

    Map<String, StateExecutionInstance> stateExecutionInstanceMap =
        stateExecutionInstances.stream().collect(toMap(StateExecutionInstance::getUuid, identity()));

    final GraphNode node = graphRenderer.generateHierarchyNode(stateExecutionInstanceMap);
    assertThat(node).isNotNull();
    assertThat(node.getName()).isEqualTo(parent.getDisplayName());

    final GraphGroup deployGroup = node.getGroup();
    assertThat(deployGroup).isNotNull();

    final List<GraphNode> deployChildElements = deployGroup.getElements();
    assertThat(deployChildElements.size()).isEqualTo(1);
    assertThat(deployChildElements.get(0).getName()).isEqualTo(repeat.getDisplayName());

    final GraphGroup repeatGroup = deployChildElements.get(0).getGroup();
    assertThat(repeatGroup).isNotNull();

    final List<GraphNode> repeatChildElements = repeatGroup.getElements();
    assertThat(repeatChildElements.size()).isEqualTo(1);
    assertThat(repeatChildElements.get(0).getName()).isEqualTo("10 instances");
    Map<String, ExecutionDataValue> executionDetails =
        (Map<String, ExecutionDataValue>) repeatChildElements.get(0).getExecutionDetails();
    assertThat(executionDetails).isNotEmpty();
    assertThat(executionDetails.get("Total instances").getValue()).isEqualTo(10);
    assertThat(executionDetails.get("Succeeded").getValue()).isEqualTo(9);
    assertThat(executionDetails.get("Error").getValue()).isEqualTo(1);
    assertThat(executionDetails.get("startTs").getValue()).isEqualTo(123L);
    assertThat(executionDetails.get("endTs").getValue()).isEqualTo(456L);
    assertThat(executionDetails.get("Execution Strategy").getValue()).isEqualTo(PARALLEL);
  }

  private void addHostElements(
      List<StateExecutionInstance> stateExecutionInstances, String hostname, String repeatId, ExecutionStatus status) {
    StateExecutionData sed = new StateExecutionData();
    sed.setStatus(SUCCESS);
    sed.setStartTs(123L);
    sed.setEndTs(456L);

    Map<String, StateExecutionData> stateExecutionDataMap = new HashMap<>();
    stateExecutionDataMap.put("install1 on" + hostname, sed);
    final StateExecutionInstance command1 = aStateExecutionInstance()
                                                .displayName("install1 on" + hostname)
                                                .uuid(hostname + "_1")
                                                .stateType(COMMAND.name())
                                                .stateExecutionMap(stateExecutionDataMap)
                                                .contextElement(HostElement.builder().hostName(hostname).build())
                                                .contextTransition(true)
                                                .status(SUCCESS)
                                                .parentInstanceId(repeatId)
                                                .build();
    stateExecutionInstances.add(command1);

    sed.setStatus(status);
    Map<String, StateExecutionData> stateExecutionDataMap2 = new HashMap<>();
    stateExecutionDataMap.put("install2 on" + hostname, sed);
    final StateExecutionInstance command2 = aStateExecutionInstance()
                                                .displayName("install2 on" + hostname)
                                                .uuid(hostname + "_2")
                                                .stateType(COMMAND.name())
                                                .stateExecutionMap(stateExecutionDataMap2)
                                                .contextElement(HostElement.builder().hostName(hostname).build())
                                                .status(status)
                                                .parentInstanceId(repeatId)
                                                .prevInstanceId(command1.getUuid())
                                                .build();

    stateExecutionInstances.add(command2);
  }
}
