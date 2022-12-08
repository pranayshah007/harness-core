/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import io.harness.annotation.RecasterAlias;
import io.harness.execution.ExecutionServiceConfig;

import java.beans.ConstructorProperties;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@Data
@EqualsAndHashCode(callSuper = true)
@TypeAlias("ContainerExecutionConfig")
@RecasterAlias("io.harness.steps.container.execution.ContainerExecutionConfig")
public class ContainerExecutionConfig extends ExecutionServiceConfig {
  @Builder
  @ConstructorProperties(
      {"addonImageTag", "liteEngineImageTag", "defaultInternalImageConnector", "delegateServiceEndpointVariableValue",
          "defaultMemoryLimit", "defaultCPULimit", "pvcDefaultStorageSize", "addonImage", "liteEngineImage", "isLocal"})
  public ContainerExecutionConfig(String addonImageTag, String liteEngineImageTag, String defaultInternalImageConnector,
      String delegateServiceEndpointVariableValue, Integer defaultMemoryLimit, Integer defaultCPULimit,
      Integer pvcDefaultStorageSize, String addonImage, String liteEngineImage, boolean isLocal) {
    super(addonImageTag, liteEngineImageTag, defaultInternalImageConnector, delegateServiceEndpointVariableValue,
        defaultMemoryLimit, defaultCPULimit, pvcDefaultStorageSize, addonImage, liteEngineImage, isLocal);
  }
}
