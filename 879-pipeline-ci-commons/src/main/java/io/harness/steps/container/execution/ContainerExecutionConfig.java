/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import io.harness.annotation.RecasterAlias;
import io.harness.execution.ExecutionServiceConfig;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.steps.container.execution.plugin.PluginConfig;

import java.beans.ConstructorProperties;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@Data
@EqualsAndHashCode(callSuper = true)
@TypeAlias("ContainerExecutionConfig")
@RecasterAlias("io.harness.steps.container.execution.ContainerExecutionConfig")
public class ContainerExecutionConfig extends ExecutionServiceConfig implements ExecutionSweepingOutput {
  String logStreamingContainerStepBaseUrl;
  PluginConfig pluginConfig;
  Map<String, List<String>> moduleToSupportedSteps;

  @Builder
  @ConstructorProperties(
      {"addonImageTag", "liteEngineImageTag", "defaultInternalImageConnector", "delegateServiceEndpointVariableValue",
          "defaultMemoryLimit", "defaultCPULimit", "pvcDefaultStorageSize", "addonImage", "liteEngineImage", "isLocal",
          "logStreamingContainerStepBaseUrl", "pluginConfig", "containerStepGroupConfig"})
  public ContainerExecutionConfig(String addonImageTag, String liteEngineImageTag, String defaultInternalImageConnector,
      String delegateServiceEndpointVariableValue, Integer defaultMemoryLimit, Integer defaultCPULimit,
      Integer pvcDefaultStorageSize, String addonImage, String liteEngineImage, boolean isLocal,
      String logStreamingContainerStepBaseUrl, PluginConfig pluginConfig,
      Map<String, List<String>> moduleToSupportedSteps) {
    super(addonImageTag, liteEngineImageTag, defaultInternalImageConnector, delegateServiceEndpointVariableValue,
        defaultMemoryLimit, defaultCPULimit, pvcDefaultStorageSize, addonImage, liteEngineImage, isLocal);
    this.logStreamingContainerStepBaseUrl = logStreamingContainerStepBaseUrl;
    this.pluginConfig = pluginConfig;
    this.moduleToSupportedSteps = moduleToSupportedSteps;
  }
}
