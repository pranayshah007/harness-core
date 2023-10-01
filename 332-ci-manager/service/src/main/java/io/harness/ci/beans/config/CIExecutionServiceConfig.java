/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import io.harness.annotation.RecasterAlias;
import io.harness.execution.ExecutionServiceConfig;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.sto.config.STOStepConfig;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("ciExecutionServiceConfig")
@RecasterAlias("io.harness.ci.config.CIExecutionServiceConfig")
public class CIExecutionServiceConfig extends ExecutionServiceConfig {
  String ciImageTag;
  CIStepConfig stepConfig;
  GitnessConfig gitnessConfig;
  CICacheIntelligenceConfig cacheIntelligenceConfig;
  CICacheIntelligenceS3Config cacheIntelligenceS3Config;
  ExecutionLimits executionLimits;
  QueueServiceClientConfig queueServiceClientConfig;
  HostedVmConfig hostedVmConfig;
  STOStepConfig stoStepConfig;
  // Base 64 encoded credentials for gcp
  MiningPatternConfig miningPatternConfig;
  Integer remoteDebugTimeout;
  CIDockerLayerCachingConfig dockerLayerCachingConfig;
  CIDockerLayerCachingGCSConfig dockerLayerCachingGCSConfig;
  String tmateEndpoint;

  @Builder
  public CIExecutionServiceConfig(String addonImageTag, String liteEngineImageTag, String defaultInternalImageConnector,
      String delegateServiceEndpointVariableValue, Integer defaultMemoryLimit, Integer defaultCPULimit,
      Integer pvcDefaultStorageSize, String addonImage, String liteEngineImage, boolean isLocal, String ciImageTag,
      CIStepConfig stepConfig, CICacheIntelligenceConfig cacheIntelligenceConfig,
      CICacheIntelligenceS3Config cacheIntelligenceS3Config, ExecutionLimits executionLimits,
      QueueServiceClientConfig queueServiceClientConfig, HostedVmConfig hostedVmConfig, STOStepConfig stoStepConfig,
      Integer remoteDebugTimeout, CIDockerLayerCachingConfig dockerLayerCachingConfig,
      CIDockerLayerCachingGCSConfig dockerLayerCachingGCSConfig, String tmateEndpoint) {
    super(addonImageTag, liteEngineImageTag, defaultInternalImageConnector, delegateServiceEndpointVariableValue,
        defaultMemoryLimit, defaultCPULimit, pvcDefaultStorageSize, addonImage, liteEngineImage, isLocal);
    this.ciImageTag = ciImageTag;
    this.stepConfig = stepConfig;
    this.cacheIntelligenceConfig = cacheIntelligenceConfig;
    this.cacheIntelligenceS3Config = cacheIntelligenceS3Config;
    this.executionLimits = executionLimits;
    this.stoStepConfig = stoStepConfig;
    this.queueServiceClientConfig = queueServiceClientConfig;
    this.hostedVmConfig = hostedVmConfig;
    this.remoteDebugTimeout = remoteDebugTimeout;
    this.dockerLayerCachingConfig = dockerLayerCachingConfig;
    this.dockerLayerCachingGCSConfig = dockerLayerCachingGCSConfig;
    this.tmateEndpoint = tmateEndpoint;
  }
}
