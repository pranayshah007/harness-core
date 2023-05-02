/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.calculation;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.CPU;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.MEMORY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.plugin.StepInfo;

import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public abstract class InitMemoryCalculatorService {
  public Pair<Integer, Integer> getRequest(List<ExecutionWrapperConfig> steps, String accountId,
      Map<String, StrategyExpansionData> strategyExpansionDataMap, Map<StepInfo, PluginCreationResponse> pluginsData) {
    Integer cpuRequest = 0;
    Integer memoryRequest = 0;

    if (isEmpty(strategyExpansionDataMap)) {
      cpuRequest = getCpuRequest(steps, accountId, pluginsData);
      memoryRequest = getStepGroupMemoryRequest(steps, accountId, pluginsData);
    } else {
      cpuRequest = getStepGroupRequestWithStrategy(steps, strategyExpansionDataMap, accountId, CPU, pluginsData);
      memoryRequest = getStepGroupRequestWithStrategy(steps, strategyExpansionDataMap, accountId, MEMORY, pluginsData);
    }

    return Pair.of(cpuRequest, memoryRequest);
  }

  private Integer getStepGroupRequestWithStrategy(List<ExecutionWrapperConfig> steps,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource,
      Map<StepInfo, PluginCreationResponse> pluginsData) {
    return getRequestForSerialSteps(steps, strategy, accountId, resource, pluginsData);
  }

  private Integer getExecutionWrapperMemoryRequest(
      ExecutionWrapperConfig executionWrapper, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperMemoryRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      executionWrapperMemoryRequest = getStepMemoryLimit(executionWrapper, accountId, pluginsData);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallel = getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallel.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallel.getSections()) {
          executionWrapperMemoryRequest += getExecutionWrapperMemoryRequest(wrapper, accountId, pluginsData);
        }
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement are supported");
    }

    return executionWrapperMemoryRequest;
  }

  private Integer getRequestForSerialSteps(List<ExecutionWrapperConfig> steps,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource,
      Map<StepInfo, PluginCreationResponse> pluginsData) {
    Integer executionWrapperRequest = 0;

    Map<String, List<ExecutionWrapperConfig>> uuidStepsMap = getUUIDStepsMap(steps);
    for (String uuid : uuidStepsMap.keySet()) {
      List<ExecutionWrapperConfig> stepsWithSameUUID = uuidStepsMap.get(uuid);
      Integer request =
          getResourceRequestForStepsWithUUID(stepsWithSameUUID, uuid, strategy, accountId, resource, pluginsData);
      executionWrapperRequest = Math.max(executionWrapperRequest, request);
    }

    // For parallel steps, as they don't have uuid field
    for (ExecutionWrapperConfig step : steps) {
      if (Strings.isNullOrBlank(step.getUuid())) {
        Integer request = getExecutionWrapperRequestWithStrategy(step, strategy, accountId, resource, pluginsData);
        executionWrapperRequest = Math.max(executionWrapperRequest, request);
      }
    }
    return executionWrapperRequest;
  }

  private Integer getResourceRequestForStepsWithUUID(List<ExecutionWrapperConfig> steps, String uuid,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource,
      Map<StepInfo, PluginCreationResponse> pluginsData) {
    List<ExecutionWrapperConfig> sortedSteps = decreasingSortWithResource(steps, accountId, resource, pluginsData);
    Integer maxConcurrency = strategy.get(uuid).getMaxConcurrency();

    Integer request = 0;
    for (int i = 0; i < Math.min(maxConcurrency, sortedSteps.size()); i++) {
      request += getExecutionWrapperRequestWithStrategy(sortedSteps.get(i), strategy, accountId, resource, pluginsData);
    }
    return request;
  }

  private Integer getExecutionWrapperRequestWithStrategy(ExecutionWrapperConfig executionWrapper,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource,
      Map<StepInfo, PluginCreationResponse> pluginsData) {
    Integer executionWrapperRequest = 0;

    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      if (resource.equals(MEMORY)) {
        executionWrapperRequest = getStepMemoryLimit(executionWrapper, accountId, pluginsData);
      } else if (resource.equals(CPU)) {
        executionWrapperRequest = getStepCpuLimit(executionWrapper, accountId, pluginsData);
      } else {
        throw new InvalidRequestException("Invalid resource type : " + resource);
      }
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallel = getParallelStepElementConfig(executionWrapper);
      List<ExecutionWrapperConfig> steps = parallel.getSections();
      if (isNotEmpty(steps)) {
        Map<String, List<ExecutionWrapperConfig>> uuidStepsMap = getUUIDStepsMap(steps);
        for (String uuid : uuidStepsMap.keySet()) {
          List<ExecutionWrapperConfig> stepsWithSameUUID = uuidStepsMap.get(uuid);
          Integer request =
              getResourceRequestForStepsWithUUID(stepsWithSameUUID, uuid, strategy, accountId, resource, pluginsData);
          executionWrapperRequest += request;
        }
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement  are supported");
    }

    return executionWrapperRequest;
  }

  private List<ExecutionWrapperConfig> decreasingSortWithResource(List<ExecutionWrapperConfig> steps, String accountId,
      String resource, Map<StepInfo, PluginCreationResponse> pluginsData) {
    if (resource.equals(MEMORY)) {
      steps = decreasingSortWithMemory(steps, accountId, pluginsData);
    } else if (resource.equals(CPU)) {
      steps = decreasingSortWithCpu(steps, accountId, pluginsData);
    } else {
      throw new InvalidRequestException("Invalid resource type : " + resource);
    }
    return steps;
  }

  private List<ExecutionWrapperConfig> decreasingSortWithMemory(
      List<ExecutionWrapperConfig> steps, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    Comparator<ExecutionWrapperConfig> decreasingSortWithMemory = (a, b) -> {
      if (getExecutionWrapperMemoryRequest(a, accountId, pluginsData)
          < getExecutionWrapperMemoryRequest(b, accountId, pluginsData)) {
        return 1;
      } else {
        return -1;
      }
    };
    Collections.sort(steps, decreasingSortWithMemory);

    return steps;
  }

  private List<ExecutionWrapperConfig> decreasingSortWithCpu(
      List<ExecutionWrapperConfig> steps, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    Comparator<ExecutionWrapperConfig> decreasingSortWithCpu = (a, b) -> {
      if (getExecutionWrapperCpuRequest(a, accountId, pluginsData)
          < getExecutionWrapperCpuRequest(b, accountId, pluginsData)) {
        return 1;
      } else {
        return -1;
      }
    };
    Collections.sort(steps, decreasingSortWithCpu);

    return steps;
  }

  private Map<String, List<ExecutionWrapperConfig>> getUUIDStepsMap(List<ExecutionWrapperConfig> steps) {
    Map<String, List<ExecutionWrapperConfig>> map = new HashMap<>();
    for (ExecutionWrapperConfig step : steps) {
      if (Strings.isNotBlank(step.getUuid())) {
        if (!map.containsKey(step.getUuid())) {
          map.put(step.getUuid(), new ArrayList<>());
        }
        map.get(step.getUuid()).add(step);
      }
    }
    return map;
  }

  private Integer getStepGroupMemoryRequest(
      List<ExecutionWrapperConfig> steps, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    Integer stepGroupMemoryRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperMemoryRequest = getExecutionWrapperMemoryRequest(step, accountId, pluginsData);
      stepGroupMemoryRequest = Math.max(stepGroupMemoryRequest, executionWrapperMemoryRequest);
    }
    return stepGroupMemoryRequest;
  }

  private Integer getCpuRequest(
      List<ExecutionWrapperConfig> steps, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    Integer cpuRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperCpuRequest = getExecutionWrapperCpuRequest(step, accountId, pluginsData);
      cpuRequest = Math.max(cpuRequest, executionWrapperCpuRequest);
    }
    return cpuRequest;
  }

  private Integer getExecutionWrapperCpuRequest(
      ExecutionWrapperConfig executionWrapper, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperCpuRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      executionWrapperCpuRequest = getStepCpuLimit(executionWrapper, accountId, pluginsData);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElement = getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElement.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallelStepElement.getSections()) {
          executionWrapperCpuRequest += getExecutionWrapperCpuRequest(wrapper, accountId, pluginsData);
        }
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement are supported");
    }
    return executionWrapperCpuRequest;
  }

  public abstract Integer getStepCpuLimit(
      ExecutionWrapperConfig stepElement, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData);

  public abstract Integer getStepMemoryLimit(
      ExecutionWrapperConfig stepElement, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData);

  private ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new ContainerStepExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }
}
