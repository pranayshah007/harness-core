/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.CPU;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.MEMORY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.expression.ExpressionResolverUtils.resolveStringParameter;

import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.ci.utils.QuantityUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class ContainerInitCpuMemHelper {
  @Inject ContainerExecutionConfig containerExecutionConfig;
  public static String UNRESOLVED_PARAMETER = "UNRESOLVED_PARAMETER";

  public Pair<Integer, Integer> getStepGroupRequest(InitContainerV2StepInfo initializeStepInfo, String accountId) {
    Integer stepGroupCpuRequest = 0;
    Integer stepGroupMemoryRequest = 0;

    if (isEmpty(initializeStepInfo.getStrategyExpansionMap())) {
      stepGroupCpuRequest = getStepGroupCpuRequest(initializeStepInfo.getStepsExecutionConfig().getSteps(), accountId);
      stepGroupMemoryRequest =
          getStepGroupMemoryRequest(initializeStepInfo.getStepsExecutionConfig().getSteps(), accountId);
    } else {
      stepGroupCpuRequest = getStepGroupRequestWithStrategy(initializeStepInfo.getStepsExecutionConfig().getSteps(),
          initializeStepInfo.getStrategyExpansionMap(), accountId, CPU);
      stepGroupMemoryRequest = getStepGroupRequestWithStrategy(initializeStepInfo.getStepsExecutionConfig().getSteps(),
          initializeStepInfo.getStrategyExpansionMap(), accountId, MEMORY);
    }

    return Pair.of(stepGroupCpuRequest, stepGroupMemoryRequest);
  }

  public Integer getStepGroupRequestWithStrategy(List<ExecutionWrapperConfig> steps,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource) {
    return getRequestForSerialSteps(steps, strategy, accountId, resource);
  }

  public Integer getRequestForSerialSteps(List<ExecutionWrapperConfig> steps,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource) {
    Integer executionWrapperRequest = 0;

    Map<String, List<ExecutionWrapperConfig>> uuidStepsMap = getUUIDStepsMap(steps);
    for (String uuid : uuidStepsMap.keySet()) {
      List<ExecutionWrapperConfig> stepsWithSameUUID = uuidStepsMap.get(uuid);
      Integer request = getResourceRequestForStepsWithUUID(stepsWithSameUUID, uuid, strategy, accountId, resource);
      executionWrapperRequest = Math.max(executionWrapperRequest, request);
    }

    // For parallel steps, as they don't have uuid field
    for (ExecutionWrapperConfig step : steps) {
      if (Strings.isNullOrBlank(step.getUuid())) {
        Integer request = getExecutionWrapperRequestWithStrategy(step, strategy, accountId, resource);
        executionWrapperRequest = Math.max(executionWrapperRequest, request);
      }
    }
    return executionWrapperRequest;
  }

  public Integer getExecutionWrapperRequestWithStrategy(ExecutionWrapperConfig executionWrapper,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource) {
    Integer executionWrapperRequest = 0;

    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      if (resource.equals(MEMORY)) {
        executionWrapperRequest = getStepMemoryLimit(executionWrapper, accountId);
      } else if (resource.equals(CPU)) {
        executionWrapperRequest = getStepCpuLimit(executionWrapper, accountId);
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
          Integer request = getResourceRequestForStepsWithUUID(stepsWithSameUUID, uuid, strategy, accountId, resource);
          executionWrapperRequest += request;
        }
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement  are supported");
    }

    return executionWrapperRequest;
  }

  private Integer getResourceRequestForStepsWithUUID(List<ExecutionWrapperConfig> steps, String uuid,
      Map<String, StrategyExpansionData> strategy, String accountId, String resource) {
    List<ExecutionWrapperConfig> sortedSteps = decreasingSortWithResource(steps, accountId, resource);
    Integer maxConcurrency = strategy.get(uuid).getMaxConcurrency();

    Integer request = 0;
    for (int i = 0; i < Math.min(maxConcurrency, sortedSteps.size()); i++) {
      request += getExecutionWrapperRequestWithStrategy(sortedSteps.get(i), strategy, accountId, resource);
    }
    return request;
  }

  public List<ExecutionWrapperConfig> decreasingSortWithResource(
      List<ExecutionWrapperConfig> steps, String accountId, String resource) {
    if (resource.equals(MEMORY)) {
      steps = decreasingSortWithMemory(steps, accountId);
    } else if (resource.equals(CPU)) {
      steps = decreasingSortWithCpu(steps, accountId);
    } else {
      throw new InvalidRequestException("Invalid resource type : " + resource);
    }
    return steps;
  }

  public Map<String, List<ExecutionWrapperConfig>> getUUIDStepsMap(List<ExecutionWrapperConfig> steps) {
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

  public Integer getStepGroupMemoryRequest(List<ExecutionWrapperConfig> steps, String accountId) {
    Integer stepGroupMemoryRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperMemoryRequest = getExecutionWrapperMemoryRequest(step, accountId);
      stepGroupMemoryRequest = Math.max(stepGroupMemoryRequest, executionWrapperMemoryRequest);
    }
    return stepGroupMemoryRequest;
  }

  public Integer getStepGroupCpuRequest(List<ExecutionWrapperConfig> steps, String accountId) {
    Integer stepGroupCpuRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperCpuRequest = getExecutionWrapperCpuRequest(step, accountId);
      stepGroupCpuRequest = Math.max(stepGroupCpuRequest, executionWrapperCpuRequest);
    }
    return stepGroupCpuRequest;
  }

  public List<ExecutionWrapperConfig> decreasingSortWithMemory(List<ExecutionWrapperConfig> steps, String accountId) {
    Comparator<ExecutionWrapperConfig> decreasingSortWithMemory = (a, b) -> {
      if (getExecutionWrapperMemoryRequest(a, accountId) < getExecutionWrapperMemoryRequest(b, accountId)) {
        return 1;
      } else {
        return -1;
      }
    };
    Collections.sort(steps, decreasingSortWithMemory);

    return steps;
  }

  public List<ExecutionWrapperConfig> decreasingSortWithCpu(List<ExecutionWrapperConfig> steps, String accountId) {
    Comparator<ExecutionWrapperConfig> decreasingSortWithCpu = (a, b) -> {
      if (getExecutionWrapperCpuRequest(a, accountId) < getExecutionWrapperCpuRequest(b, accountId)) {
        return 1;
      } else {
        return -1;
      }
    };
    Collections.sort(steps, decreasingSortWithCpu);

    return steps;
  }

  private Integer getExecutionWrapperCpuRequest(ExecutionWrapperConfig executionWrapper, String accountId) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperCpuRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      executionWrapperCpuRequest = getStepCpuLimit(executionWrapper, accountId);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElement = getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElement.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallelStepElement.getSections()) {
          executionWrapperCpuRequest += getExecutionWrapperCpuRequest(wrapper, accountId);
        }
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement are supported");
    }
    return executionWrapperCpuRequest;
  }

  private Integer getStepCpuLimit(ExecutionWrapperConfig stepElement, String accountId) {
    // todo: implement this
    return 0;
  }

  private Integer getContainerCpuLimit(ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer cpuLimit = containerExecutionConfig.getDefaultCPULimit();

    if (resource != null && resource.getLimits() != null && resource.getLimits().getCpu() != null) {
      String cpuLimitQuantity = resolveStringParameter("cpu", stepType, stepId, resource.getLimits().getCpu(), false);
      if (isNotEmpty(cpuLimitQuantity) && !UNRESOLVED_PARAMETER.equals(cpuLimitQuantity)) {
        cpuLimit = QuantityUtils.getCpuQuantityValueInUnit(cpuLimitQuantity, DecimalQuantityUnit.m);
      }
    }
    return cpuLimit;
  }

  private Integer getExecutionWrapperMemoryRequest(ExecutionWrapperConfig executionWrapper, String accountId) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperMemoryRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      executionWrapperMemoryRequest = getStepMemoryLimit(executionWrapper, accountId);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallel = getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallel.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallel.getSections()) {
          executionWrapperMemoryRequest += getExecutionWrapperMemoryRequest(wrapper, accountId);
        }
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement are supported");
    }

    return executionWrapperMemoryRequest;
  }

  private Integer getStepMemoryLimit(ExecutionWrapperConfig stepElement, String accountId) {
    ContainerResource containerResource = getStepResources(stepElement);
    return getContainerMemoryLimit(containerResource, null, null, accountId);
  }

  private ContainerResource getStepResources(ExecutionWrapperConfig ciStepInfo) {
    // todo: implement this
    return null;
  }

  private Integer getContainerMemoryLimit(
      ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer memoryLimit = containerExecutionConfig.getDefaultMemoryLimit();

    if (resource != null && resource.getLimits() != null && resource.getLimits().getMemory() != null) {
      String memoryLimitMemoryQuantity =
          resolveStringParameter("memory", stepType, stepId, resource.getLimits().getMemory(), false);
      if (isNotEmpty(memoryLimitMemoryQuantity) && !UNRESOLVED_PARAMETER.equals(memoryLimitMemoryQuantity)) {
        memoryLimit = QuantityUtils.getStorageQuantityValueInUnit(memoryLimitMemoryQuantity, StorageQuantityUnit.Mi);
      }
    }
    return memoryLimit;
  }

  public ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new ContainerStepExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }
}
