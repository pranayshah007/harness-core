/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.PipelineSdkRedisEventsConfig;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.execution.ExecutionSummaryModuleInfoProvider;
import io.harness.pms.sdk.core.execution.events.node.facilitate.Facilitator;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.core.pipeline.filters.FilterCreationResponseMerger;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.redis.RedisConfig;
import io.harness.threading.ThreadPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class PmsSdkConfiguration {
  @Builder.Default SdkDeployMode deploymentMode = SdkDeployMode.LOCAL;
  @NonNull ModuleType moduleType;
  GrpcServerConfig grpcServerConfig;
  GrpcClientConfig pmsGrpcClientConfig;
  Class<? extends PipelineServiceInfoProvider> pipelineServiceInfoProviderClass;
  FilterCreationResponseMerger filterCreationResponseMerger;
  Map<StepType, Class<? extends Step>> engineSteps;
  Map<String, Class<? extends SdkFunctor>> sdkFunctors;
  Map<String, String> staticAliases;
  Map<AdviserType, Class<? extends Adviser>> engineAdvisers;
  Map<FacilitatorType, Class<? extends Facilitator>> engineFacilitators;
  Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> engineEventHandlersMap;
  Class<? extends ExecutionSummaryModuleInfoProvider> executionSummaryModuleInfoProviderClass;
  @Builder.Default ThreadPoolConfig executionPoolConfig = ThreadPoolConfig.builder().build();
  @Builder.Default ThreadPoolConfig orchestrationEventPoolConfig = ThreadPoolConfig.builder().build();
  @Builder.Default ThreadPoolConfig planCreatorServiceInternalConfig = ThreadPoolConfig.builder().build();
  @Default List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();

  @Default
  EventsFrameworkConfiguration eventsFrameworkConfiguration =
      EventsFrameworkConfiguration.builder()
          .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
          .build();

  @Default PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig = PipelineSdkRedisEventsConfig.builder().build();

  public String getServiceName() {
    return moduleType.name().toLowerCase();
  }
}
