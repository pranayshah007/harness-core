/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.ABORTING;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.NEW;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.PAUSING;
import static io.harness.beans.ExecutionStatus.QUEUED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;
import static software.wings.sm.StateType.SUB_WORKFLOW;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.ExecutionStatusCategory;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;

import software.wings.api.ExecutionDataValue;
import software.wings.beans.ExecutionStrategy;
import software.wings.beans.GraphGroup;
import software.wings.beans.GraphNode;
import software.wings.beans.GraphNode.GraphNodeBuilder;
import software.wings.common.WorkflowConstants;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.states.ElementStateExecutionData;
import software.wings.sm.states.ForkState.ForkStateExecutionData;
import software.wings.sm.states.RepeatState.RepeatStateExecutionData;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class GraphRenderer {
  public static final int AGGREGATION_LIMIT = 10;

  public static final long algorithmId = 0;

  @Inject private Injector injector;

  @Inject private WorkflowExecutionService workflowExecutionService;

  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;

  static boolean isSubWorkflow(StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance == null) {
      return false;
    }

    final String stateTypeName = stateExecutionInstance.getStateType();
    if (stateTypeName == null) {
      return false;
    }

    final StateType stateType = StateType.valueOf(stateTypeName);
    return stateType == SUB_WORKFLOW || stateType == PHASE_STEP || stateType == PHASE;
  }

  static ExecutionStatus aggregateStatus(List<ExecutionStatus> statuses) {
    if (isEmpty(statuses)) {
      return null;
    }

    List<ExecutionStatus> activeStatuses =
        statuses.stream().filter(status -> !ExecutionStatus.isFinalStatus(status)).collect(toList());

    for (ExecutionStatus status : asList(WAITING, PAUSED, PAUSING, RUNNING)) {
      if (activeStatuses.stream().anyMatch(active -> active == status)) {
        return status;
      }
    }
    final Optional<ExecutionStatus> notNewActiveStatuses =
        activeStatuses.stream().filter(status -> !asList(NEW, null).contains(status)).findFirst();
    if (notNewActiveStatuses.isPresent()) {
      return notNewActiveStatuses.get();
    }

    if (!activeStatuses.isEmpty()) {
      return NEW;
    }

    for (ExecutionStatus status : asList(REJECTED, EXPIRED, ABORTED, ABORTING, ERROR, FAILED)) {
      if (statuses.stream().anyMatch(active -> active == status)) {
        return status;
      }
    }

    return SUCCESS;
  }

  static ExecutionStatus aggregateDataStatus(ExecutionStatus current, List<StateExecutionData> executionDataList) {
    final List<ExecutionStatus> executionStatuses =
        executionDataList.stream().map(StateExecutionData::getStatus).collect(toList());
    if (current != null) {
      executionStatuses.add(0, current);
    }

    return aggregateStatus(executionStatuses);
  }

  static Long aggregateStartTs(Long current, List<StateExecutionData> executionDataList) {
    final Long startTs = executionDataList.stream()
                             .map(StateExecutionData::getStartTs)
                             .filter(Objects::nonNull)
                             .min(Long::compare)
                             .orElse(null);

    if (startTs == null) {
      return current;
    }
    if (current == null) {
      return startTs;
    }

    return current < startTs ? current : startTs;
  }

  static Long aggregateEndTs(Long current, List<StateExecutionData> executionDataList) {
    final Long endTs = executionDataList.stream()
                           .map(StateExecutionData::getEndTs)
                           .filter(Objects::nonNull)
                           .max(Long::compare)
                           .orElse(null);

    if (endTs == null) {
      return current;
    }
    if (current == null) {
      return endTs;
    }

    return current > endTs ? current : endTs;
  }

  static String aggregateErrorMessage(String current, List<StateExecutionData> executionDataList) {
    List<String> messages =
        executionDataList.stream().map(StateExecutionData::getErrorMsg).filter(Objects::nonNull).collect(toList());
    if (current != null) {
      messages.add(current);
    }

    messages = messages.stream().distinct().collect(toList());

    if (isEmpty(messages)) {
      return null;
    }

    if (messages.size() == 1) {
      return messages.get(0);
    }

    return "Multiple errors";
  }

  class Session {
    private Map<String, StateExecutionInstance> instanceIdMap;

    private Map<String, StateExecutionInstance> prevInstanceIdMap = new HashMap<>();
    private Map<String, Map<String, StateExecutionInstance>> parentIdElementsMap = new HashMap<>();

    Session(Map<String, StateExecutionInstance> instanceIdMap) {
      this.instanceIdMap = instanceIdMap;
    }

    void getHostElementStatus(StateExecutionInstance instance, StateExecutionData elementStateExecutionData) {
      if (elementStateExecutionData != null) {
        final StateExecutionData executionData = instance.fetchStateExecutionData();

        if (executionData != null) {
          elementStateExecutionData.setStatus(executionData.getStatus());
          elementStateExecutionData.setStartTs(executionData.getStartTs());
          elementStateExecutionData.setEndTs(executionData.getEndTs());
          elementStateExecutionData.setErrorMsg(executionData.getErrorMsg());
        }
      }

      final StateExecutionInstance nextInstance = prevInstanceIdMap.get(instance.getUuid());

      if (nextInstance != null) {
        getHostElementStatus(nextInstance, elementStateExecutionData);
      }
    }

    GraphNode generateNodeTree(StateExecutionInstance instance, StateExecutionData elementStateExecutionData) {
      GraphNode node = convertToNode(instance);

      if (elementStateExecutionData != null) {
        final StateExecutionData executionData = instance.fetchStateExecutionData();
        final List<StateExecutionData> executionDataList =
            executionData == null ? new ArrayList<>() : asList(executionData);
        elementStateExecutionData.setStatus(
            aggregateDataStatus(elementStateExecutionData.getStatus(), executionDataList));
        elementStateExecutionData.setStartTs(
            aggregateStartTs(elementStateExecutionData.getStartTs(), executionDataList));
        elementStateExecutionData.setEndTs(aggregateEndTs(elementStateExecutionData.getEndTs(), executionDataList));
        elementStateExecutionData.setErrorMsg(
            aggregateErrorMessage(elementStateExecutionData.getErrorMsg(), executionDataList));
      }

      if (parentIdElementsMap.get(instance.getUuid()) != null) {
        GraphGroup group = new GraphGroup();
        group.setId(node.getId() + "-group");
        log.debug("generateNodeTree group attached - group: {}, node: {}", group, node);
        node.setGroup(group);

        Collection<String> elements = null;
        Collection<String> aggregateElements = null;
        StateExecutionData sed = instance.fetchStateExecutionData();
        if (sed instanceof ForkStateExecutionData) {
          elements = ((ForkStateExecutionData) sed).getElements();
        } else if (sed instanceof RepeatStateExecutionData) {
          group.setExecutionStrategy(((RepeatStateExecutionData) sed).getExecutionStrategy());

          Collection<String> repeatedElements = ((RepeatStateExecutionData) sed)
                                                    .getRepeatElements()
                                                    .stream()
                                                    .map(ContextElement::getName)
                                                    .collect(toList());

          if (repeatedElements.size() < AGGREGATION_LIMIT) {
            elements = repeatedElements;
            aggregateElements = null;
          } else {
            elements = Collections.emptyList();
            aggregateElements = repeatedElements;
          }
        }

        log.debug("generateNodeTree processing group - node: {}", elements);
        if (elements == null) {
          elements = parentIdElementsMap.get(instance.getUuid()).keySet();
        }
        for (String element : elements) {
          generateElement(instance, group, element);
        }

        if (aggregateElements != null) {
          generateAggregateElement(instance, group, aggregateElements, aggregateElements.size() + elements.size());
        }
      }

      final StateExecutionInstance nextInstance = prevInstanceIdMap.get(instance.getUuid());

      if (nextInstance != null) {
        GraphNode nextNode = generateNodeTree(nextInstance, elementStateExecutionData);
        node.setNext(nextNode);
      }

      return node;
    }

    void generateElement(StateExecutionInstance instance, GraphGroup group, String element) {
      if (element.equals(WorkflowConstants.SUB_WORKFLOW)) {
        StateExecutionInstance elementRepeatInstance = parentIdElementsMap.get(instance.getUuid()).get(element);
        if (elementRepeatInstance != null) {
          final GraphNode elementRepeatNode = generateNodeTree(elementRepeatInstance, null);
          group.getElements().add(elementRepeatNode);
        }
        return;
      }

      GraphNode elementNode =
          GraphNode.builder().id(instance.getUuid() + "-" + element).name(element).type("ELEMENT").build();

      group.getElements().add(elementNode);

      StateExecutionInstance elementRepeatInstance = parentIdElementsMap.get(instance.getUuid()).get(element);
      StateExecutionData executionData = new StateExecutionData();
      if (elementRepeatInstance != null) {
        final GraphNode elementRepeatNode = generateNodeTree(elementRepeatInstance, executionData);
        elementNode.setNext(elementRepeatNode);
      }
      if (executionData.getStatus() == null) {
        executionData.setStatus(QUEUED);
      }
      elementNode.setStatus(executionData.getStatus().name());
      elementNode.setExecutionSummary(executionData.getExecutionSummary());
      elementNode.setExecutionDetails(executionData.getExecutionDetails());
    }

    void generateAggregateElement(
        StateExecutionInstance instance, GraphGroup group, Collection<String> elements, int total) {
      String name = total + " instances";

      GraphNode elementNode = GraphNode.builder()
                                  .id(instance.getUuid() + "-aggregate")
                                  .name(name)
                                  .type("ELEMENTS")
                                  .status(instance.getStatus().name())
                                  .build();

      group.getElements().add(elementNode);

      List<StateExecutionInstance> elementRepeatInstances =
          elements.stream().map(element -> parentIdElementsMap.get(instance.getUuid()).get(element)).collect(toList());
      StateExecutionData sed = instance.fetchStateExecutionData();
      ExecutionStrategy executionStrategy = ((RepeatStateExecutionData) sed).getExecutionStrategy();

      StateExecutionData executionData = new StateExecutionData();
      getAggregatedExecutionData(elementRepeatInstances, executionData, elementNode, executionStrategy);

      if (executionData.getStatus() == null) {
        executionData.setStatus(QUEUED);
      }
      elementNode.setStatus(executionData.getStatus().name());

      elementNode.setExecutionSummary(executionData.getExecutionSummary());
    }

    private void getAggregatedExecutionData(List<StateExecutionInstance> instances, StateExecutionData executionData,
        GraphNode elementNode, ExecutionStrategy executionStrategy) {
      if (instances.size() <= 1) {
        throw new UnexpectedException();
      }

      StateExecutionInstance instance = instances.get(0);
      if (instances.stream().noneMatch(item -> instance.getDisplayName().equals(item.getDisplayName()))) {
        throw new UnexpectedException();
      }
      if (instances.stream().noneMatch(item -> instance.getStateType().equals(item.getStateType()))) {
        throw new UnexpectedException();
      }
      if (instances.stream().noneMatch(item -> instance.isRollback() == item.isRollback())) {
        throw new UnexpectedException();
      }

      List<ExecutionStatus> collect = new ArrayList<>();
      final Multiset<ExecutionStatusCategory> multiset = HashMultiset.create();
      for (StateExecutionInstance stateExecutionInstance : instances) {
        if (stateExecutionInstance != null) {
          getHostElementStatus(stateExecutionInstance, executionData);
          ExecutionStatus stateExecutionInstanceStatus = executionData.getStatus();
          if (stateExecutionInstanceStatus == null) {
            log.info("Aggregating null status for StateExecutionInstance {}", stateExecutionInstance.getUuid());
          }
          collect.add(stateExecutionInstanceStatus);
          multiset.add(ExecutionStatus.getStatusCategory(stateExecutionInstanceStatus));
        } else {
          // Since there is no stateExecutionInstance created yet. That means, its still starting.
          collect.add(QUEUED);
          multiset.add(ExecutionStatus.getStatusCategory(QUEUED));
        }
      }

      Map<String, ExecutionDataValue> executionDetails = new LinkedHashMap<>();

      executionDetails.put("Total instances",
          ExecutionDataValue.builder().displayName("Total instances").value(instances.size()).build());

      for (ExecutionStatusCategory st : ExecutionStatusCategory.values()) {
        int count = multiset.count(st);
        if (count > 0) {
          String displayName = st.getDisplayName();
          executionDetails.put(displayName, ExecutionDataValue.builder().displayName(displayName).value(count).build());
        }
      }

      final List<StateExecutionData> executionDataList = new ArrayList<>();
      for (StateExecutionInstance stateExecutionInstance : instances) {
        if (stateExecutionInstance != null) {
          StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
          if (stateExecutionData != null) {
            executionDataList.add(stateExecutionData);
          }
        }
      }
      executionData.setStatus(aggregateStatus(collect));
      executionData.setStartTs(aggregateStartTs(executionData.getStartTs(), executionDataList));
      executionData.setEndTs(aggregateEndTs(executionData.getEndTs(), executionDataList));
      executionData.setErrorMsg(aggregateErrorMessage(executionData.getErrorMsg(), executionDataList));
      executionDetails.putAll(executionData.getExecutionDetails());
      executionDetails.put("Execution Strategy",
          ExecutionDataValue.builder().displayName("Execution Strategy").value(executionStrategy).build());
      elementNode.setExecutionDetails(executionDetails);
    }

    void populateParentAndPreviousForSubGraph() {
      if (isEmpty(instanceIdMap)) {
        return;
      }
      for (StateExecutionInstance instance : instanceIdMap.values()) {
        populateParentAndPrevious(instance);
      }
    }

    GraphNode generateHierarchyNode() {
      if (isEmpty(instanceIdMap)) {
        return null;
      }

      StateExecutionInstance origin = null;

      if (log.isDebugEnabled()) {
        log.debug("generateSubworkflows request received - instanceIdMap: {}", instanceIdMap);
      }

      for (StateExecutionInstance instance : instanceIdMap.values()) {
        if (instance.getPrevInstanceId() == null && instance.getParentInstanceId() == null) {
          origin = instance;
        }
        populateParentAndPrevious(instance);
      }

      if (log.isDebugEnabled()) {
        log.debug("generateNodeTree invoked - instanceIdMap: {}, prevInstanceIdMap: {}, parentIdElementsMap: {}",
            instanceIdMap, prevInstanceIdMap, parentIdElementsMap);
      }

      return generateNodeTree(origin, null);
    }

    private void populateParentAndPrevious(StateExecutionInstance instance) {
      final String parentInstanceId = instance.getParentInstanceId();
      if (parentInstanceId != null && instance.isContextTransition()) {
        Map<String, StateExecutionInstance> elementsMap =
            parentIdElementsMap.computeIfAbsent(parentInstanceId, key -> new HashMap<>());

        if (isSubWorkflow(instanceIdMap.get(parentInstanceId))) {
          elementsMap.put(WorkflowConstants.SUB_WORKFLOW, instance);
          return;
        }

        if (instance.getContextElement() == null) {
          return;
        }
        elementsMap.put(instance.getContextElement().getName(), instance);
      }

      if (instance.getPrevInstanceId() != null) {
        prevInstanceIdMap.put(instance.getPrevInstanceId(), instance);
      }
    }
  }

  /**
   * Generate hierarchy node node.
   *
   * @param instanceIdMap    the instance id map
   * @return the node
   */
  public GraphNode generateHierarchyNode(Map<String, StateExecutionInstance> instanceIdMap) {
    final Session session = new Session(instanceIdMap);

    return session.generateHierarchyNode();
  }

  public GraphGroup generateNodeSubGraph(
      LinkedHashMap<String, Map<String, StateExecutionInstance>> elementIdToInstanceIdMap,
      StateExecutionInstance repeatInstance) {
    GraphGroup group = new GraphGroup();
    List<GraphNode> nodeSubGraph = new ArrayList<>();
    group.setElements(nodeSubGraph);
    for (Entry<String, Map<String, StateExecutionInstance>> hostElement : elementIdToInstanceIdMap.entrySet()) {
      Map<String, StateExecutionInstance> instanceMap = hostElement.getValue();

      StateExecutionData sed = repeatInstance.fetchStateExecutionData();
      notNullCheck("stateExecutionData not populated for instance: " + repeatInstance.getUuid(), sed);
      if (!(sed instanceof RepeatStateExecutionData)) {
        throw new InvalidRequestException("Request for elements of instance that is not repeated", USER);
      }

      ContextElement element = ((RepeatStateExecutionData) sed)
                                   .getRepeatElements()
                                   .stream()
                                   .filter(contextElement -> contextElement.getUuid().equals(hostElement.getKey()))
                                   .findFirst()
                                   .orElse(null);
      if (element == null) {
        throw new InvalidRequestException("Couldnt find element for host: " + hostElement.getKey(), USER);
      }

      Session session = new Session(instanceMap);
      session.populateParentAndPreviousForSubGraph();
      session.generateElement(repeatInstance, group, element.getName());
    }
    return group;
  }

  GraphNode convertToNode(StateExecutionInstance instance) {
    GraphNodeBuilder builder =
        GraphNode.builder()
            .id(instance.getUuid())
            .name(instance.getDisplayName())
            .type(instance.getStateType())
            .rollback(instance.isRollback())
            .status(String.valueOf(instance.getStatus()).toUpperCase())
            .hasInspection(instance.isHasInspection())
            .selectionLogsTrackingForTasksEnabled(instance.isSelectionLogsTrackingForTasksEnabled());

    if (isNotEmpty(instance.getDelegateTasksDetails())) {
      instance.getDelegateTasksDetails()
          .stream()
          .map(delegateTaskDetails -> {
            Optional<DelegateSelectionLogParams> logParamsOptional =
                delegateSelectionLogsService.fetchSelectedDelegateForTask(
                    instance.getAccountId(), delegateTaskDetails.getDelegateTaskId());
            if (logParamsOptional.isPresent()) {
              delegateTaskDetails.setSelectedDelegateId(logParamsOptional.get().getDelegateId());
              delegateTaskDetails.setSelectedDelegateName(logParamsOptional.get().getDelegateName());
              delegateTaskDetails.setSelectedDelegateHostName(logParamsOptional.get().getDelegateHostName());
            }

            return delegateTaskDetails;
          })
          .collect(Collectors.toList());

      builder.delegateTasksDetails(instance.getDelegateTasksDetails());
    } else if (StringUtils.isNotBlank(instance.getDelegateTaskId())) {
      builder.delegateTasksDetails(
          Arrays.asList(DelegateTaskDetails.builder().delegateTaskId(instance.getDelegateTaskId()).build()));
    }

    if (instance.getStateExecutionDataHistory() != null) {
      builder.executionHistoryCount(instance.getStateExecutionDataHistory().size());
    }
    int interrupts = instance.getDedicatedInterruptCount() == null
        ? workflowExecutionService.getExecutionInterruptCount(instance.getUuid())
        : instance.getDedicatedInterruptCount();

    if (instance.getInterruptHistory() != null) {
      interrupts += instance.getInterruptHistory().size();
    }
    builder.interruptHistoryCount(interrupts);

    if (instance.fetchStateExecutionData() != null) {
      StateExecutionData executionData = instance.fetchStateExecutionData();
      injector.injectMembers(executionData);
      try {
        builder.executionSummary(executionData.getExecutionSummary());
      } catch (RuntimeException e) {
        log.error("Failed to get state execution summary for state instance id {} and state name {}",
            instance.getUuid(), instance.getDisplayName(), e);
      }
      try {
        builder.executionDetails(executionData.getExecutionDetails());
      } catch (RuntimeException e) {
        log.error("Failed to get state execution details for state instance id {} and state name {}",
            instance.getUuid(), instance.getDisplayName(), e);
      }

      if (executionData instanceof ElementStateExecutionData) {
        ElementStateExecutionData elementStateExecutionData = (ElementStateExecutionData) executionData;
        try {
          builder.elementStatusSummary(elementStateExecutionData.getElementStatusSummary());
        } catch (RuntimeException e) {
          log.error("Failed to get state element status summary for state instance id {} and state name {}",
              instance.getUuid(), instance.getDisplayName(), e);
        }
      }
    }
    if (instance.getStateParams() != null) {
      builder.properties(instance.getStateParams());
    }
    return builder.build();
  }
}
