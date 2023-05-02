/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.calculation.InitMemoryCalculatorService;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class ContainerInitCpuMemHelper extends InitMemoryCalculatorService {
  @Inject ContainerExecutionConfig containerExecutionConfig;
  public static String UNRESOLVED_PARAMETER = "UNRESOLVED_PARAMETER";

  public Pair<Integer, Integer> getStepGroupRequest(InitContainerV2StepInfo initializeStepInfo, String accountId) {
    return this.getRequest(initializeStepInfo.getStepsExecutionConfig().getSteps(), accountId,
        initializeStepInfo.getStrategyExpansionMap(), initializeStepInfo.getPluginsData());
  }

  @Override
  public Integer getStepCpuLimit(
      ExecutionWrapperConfig stepElement, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    YamlNode yamlNode = new YamlNode(stepElement.getStep());
    Optional<Map.Entry<StepInfo, PluginCreationResponse>> creationResponseEntry =
        pluginsData.entrySet()
            .stream()
            .filter(pluginsDataMap -> pluginsDataMap.getKey().getStepUuid().equals(yamlNode.getUuid()))
            .findFirst();
    if (!creationResponseEntry.isPresent()) {
      throw new ContainerStepExecutionException("Couldn't find container cpu");
    }
    return creationResponseEntry.get().getValue().getPluginDetails().getResource().getCpu();
  }

  @Override
  public Integer getStepMemoryLimit(
      ExecutionWrapperConfig stepElement, String accountId, Map<StepInfo, PluginCreationResponse> pluginsData) {
    ContainerResource containerResource = getStepResources(stepElement, pluginsData);
    return getContainerMemoryLimit(containerResource, null, null, accountId, pluginsData);
  }

  private ContainerResource getStepResources(
      ExecutionWrapperConfig stepInfo, Map<StepInfo, PluginCreationResponse> pluginsData) {
    YamlNode yamlNode = new YamlNode(stepInfo.getStep());
    String uuid = yamlNode.getUuid();
    Optional<Map.Entry<StepInfo, PluginCreationResponse>> pluginCreationResponseEntry =
        pluginsData.entrySet()
            .stream()
            .filter(pluginsDataMap -> pluginsDataMap.getKey().getStepUuid().equals(yamlNode.getUuid()))
            .findFirst();

    if (!pluginCreationResponseEntry.isPresent()) {
      throw new ContainerPluginParseException("Cannot get container memory data");
    }
    PluginContainerResources resource = pluginCreationResponseEntry.get().getValue().getPluginDetails().getResource();
    return ContainerResource.builder()
        .limits(ContainerResource.Limits.builder()
                    .cpu(ParameterField.<String>builder().value(String.valueOf(resource.getCpu())).build())
                    .memory(ParameterField.<String>builder().value(String.valueOf(resource.getMemory())).build())
                    .build())
        .build();
  }

  private Integer getContainerMemoryLimit(ContainerResource resource, String stepType, String stepId, String accountID,
      Map<StepInfo, PluginCreationResponse> pluginsData) {
    Integer memoryLimit = containerExecutionConfig.getDefaultMemoryLimit();

    if (resource != null && resource.getLimits() != null && resource.getLimits().getMemory() != null) {
      memoryLimit = Integer.valueOf(resource.getLimits().getMemory().getValue());
    }
    return memoryLimit;
  }
}
