/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("ciExecutionServiceConfig")
@RecasterAlias("io.harness.ci.config.CIExecutionServiceConfig")
public class CIExecutionServiceConfig {
  String addonImageTag; // Deprecated
  String liteEngineImageTag; // Deprecated
  String defaultInternalImageConnector;
  String delegateServiceEndpointVariableValue;
  Integer defaultMemoryLimit;
  Integer defaultCPULimit;
  Integer pvcDefaultStorageSize;
  String addonImage;
  String liteEngineImage;
  String ciImageTag;
  CIStepConfig stepConfig;
  boolean isLocal;
}
