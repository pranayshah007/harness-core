/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_INTERRUPT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_ADVISE_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.exception.InvalidRequestException;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.InitializeSdkRequest;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.plan.PmsServiceGrpc;
import io.harness.pms.contracts.plan.Redis;
import io.harness.pms.contracts.plan.SdkModuleInfo;
import io.harness.pms.contracts.plan.Types;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.exception.InitializeSdkException;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.redis.RedisConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkInitHelper {
  private static final int MAX_ATTEMPTS = 3;

  private static final long INITIAL_DELAY_MS = 100;
  private static final long MAX_DELAY_MS = 5000;
  private static final long DELAY_FACTOR = 5;
  private static final RetryPolicy<Object> RETRY_POLICY = createRetryPolicy();

  public static Map<String, Types> calculateSupportedTypes(PipelineServiceInfoProvider pipelineServiceInfoProvider) {
    List<PartialPlanCreator<?>> planCreators = pipelineServiceInfoProvider.getPlanCreators();
    if (EmptyPredicate.isEmpty(planCreators)) {
      return Collections.emptyMap();
    }

    Map<String, Set<String>> supportedTypes = PmsSdkInitValidator.validatePlanCreators(pipelineServiceInfoProvider);

    Map<String, Types> finalMap = new HashMap<>();
    supportedTypes.forEach((k, v) -> finalMap.put(k, Types.newBuilder().addAllTypes(v).build()));
    return finalMap;
  }

  public static void initializeSDKInstance(Injector injector, PmsSdkConfiguration pmsSdkConfiguration) {
    initialize(injector, pmsSdkConfiguration);
    initializeMetrics(injector);
  }

  private static void initialize(Injector injector, PmsSdkConfiguration config) {
    log.info("Initializing PMS SDK for module: {}", config.getModuleType());
    if (config.getDeploymentMode().isNonLocal()) {
      ServiceManager serviceManager =
          injector.getInstance(Key.get(ServiceManager.class, Names.named("pmsSDKServiceManager"))).startAsync();
      serviceManager.awaitHealthy();

      Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceManager.stopAsync().awaitStopped()));
      registerSdk(injector, config);
    }
  }

  private static void initializeMetrics(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private static void registerSdk(Injector injector, PmsSdkConfiguration sdkConfiguration) {
    try {
      PmsServiceGrpc.PmsServiceBlockingStub pmsClient =
          injector.getInstance(PmsServiceGrpc.PmsServiceBlockingStub.class);
      Failsafe.with(RETRY_POLICY)
          .get(() -> pmsClient.initializeSdk(buildInitializeSdkRequest(injector, sdkConfiguration)));
      log.info("Sdk Initialized for module {} Successfully", sdkConfiguration.getModuleType());
    } catch (StatusRuntimeException ex) {
      log.error("Sdk Initialization failed with StatusRuntimeException Status: {}", ex.getStatus());
      throw ex;
    } catch (Exception ex) {
      log.error("Sdk Initialization failed with Status: {}", ex.getMessage());
      throw ex;
    }
  }

  private static InitializeSdkRequest buildInitializeSdkRequest(
      Injector injector, PmsSdkConfiguration sdkConfiguration) {
    PipelineServiceInfoProvider infoProvider = injector.getInstance(PipelineServiceInfoProvider.class);
    ModuleType moduleType = sdkConfiguration.getModuleType();
    EventsFrameworkConfiguration eventsConfig = sdkConfiguration.getEventsFrameworkConfiguration();
    return InitializeSdkRequest.newBuilder()
        .setName(sdkConfiguration.getServiceName())
        .putAllSupportedTypes(PmsSdkInitHelper.calculateSupportedTypes(infoProvider))
        .addAllSupportedSteps(mapToSdkStep(calculateStepTypes(injector), infoProvider.getStepInfo()))
        .setSdkModuleInfo(SdkModuleInfo.newBuilder().setDisplayName(moduleType.getDisplayName()).build())
        .setInterruptConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.INTERRUPT_EVENT))
        .setOrchestrationEventConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.ORCHESTRATION_EVENT))
        .setFacilitatorEventConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.FACILITATOR_EVENT))
        .putAllStaticAliases(CollectionUtils.emptyIfNull(sdkConfiguration.getStaticAliases()))
        .addAllSdkFunctors(PmsSdkInitHelper.getSupportedSdkFunctorsList(sdkConfiguration))
        .addAllJsonExpansionInfo(getJsonExpansionInfo(sdkConfiguration))
        .setNodeStartEventConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.NODE_START))
        .setProgressEventConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.PROGRESS_EVENT))
        .setNodeAdviseEventConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.NODE_ADVISE))
        .setNodeResumeEventConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.NODE_RESUME))
        .setPlanCreationEventConsumerConfig(buildConsumerConfig(eventsConfig, PmsEventCategory.CREATE_PARTIAL_PLAN))
        .build();
  }

  static List<JsonExpansionInfo> getJsonExpansionInfo(PmsSdkConfiguration sdkConfiguration) {
    List<JsonExpansionHandlerInfo> expansionHandlers = sdkConfiguration.getJsonExpansionHandlers();
    if (EmptyPredicate.isEmpty(expansionHandlers)) {
      return new ArrayList<>();
    }
    return expansionHandlers.stream().map(JsonExpansionHandlerInfo::getJsonExpansionInfo).collect(Collectors.toList());
  }

  @VisibleForTesting
  static List<String> getSupportedSdkFunctorsList(PmsSdkConfiguration sdkConfiguration) {
    if (sdkConfiguration.getSdkFunctors() == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(sdkConfiguration.getSdkFunctors().keySet());
  }

  private static List<SdkStep> mapToSdkStep(List<StepType> stepTypeList, List<StepInfo> stepInfos) {
    Map<String, StepType> stepTypeStringToStepType = stepTypeList.stream().collect(
        Collectors.toMap(StepType::getType, stepType -> stepType, (stepType1, stepType2) -> stepType1));
    Map<String, StepInfo> stepTypeStringToStepInfo = new HashMap<>();
    for (StepInfo stepInfo : stepInfos) {
      stepTypeStringToStepInfo.put(stepInfo.getType(), stepInfo);
    }

    List<SdkStep> pmsSdkStepTypeWithInfos = new ArrayList<>();
    for (String stepType : stepTypeStringToStepType.keySet()) {
      SdkStep.Builder sdkStepWrapper = SdkStep.newBuilder();
      sdkStepWrapper.setStepType(stepTypeStringToStepType.get(stepType));
      if (stepTypeStringToStepInfo.containsKey(stepType)) {
        sdkStepWrapper.setIsPartOfStepPallete(true);
        sdkStepWrapper.setStepInfo(stepTypeStringToStepInfo.get(stepType));
      }
      pmsSdkStepTypeWithInfos.add(sdkStepWrapper.build());
    }
    return pmsSdkStepTypeWithInfos;
  }

  /**
   * Absorbing all of this logic inside the SDK. This felt like an overkill for now.
   * Things internally work exactly the same way. This makes the sdk initialization easier.
   *
   * If we feel the need (which i do not think) we would in near future we can expose this mechanism back
   *
   */
  private static ConsumerConfig buildConsumerConfig(
      EventsFrameworkConfiguration eventsConfig, PmsEventCategory eventCategory) {
    RedisConfig redisConfig = eventsConfig.getRedisConfig();
    if (redisConfig != null) {
      return ConsumerConfig.newBuilder().setRedis(buildConsumerRedisConfig(eventCategory)).build();
    }
    throw new UnsupportedOperationException("Only Redis is Supported as Back End");
  }

  /**
   * In future if events framework build support for Kafka or any other event backbone we just need
   * to add some logic here to init with a diff config
   *
   */
  private static Redis buildConsumerRedisConfig(PmsEventCategory eventCategory) {
    switch (eventCategory) {
      case INTERRUPT_EVENT:
        return Redis.newBuilder().setTopicName(PIPELINE_INTERRUPT_TOPIC).build();
      case ORCHESTRATION_EVENT:
        return Redis.newBuilder().setTopicName(PIPELINE_ORCHESTRATION_EVENT_TOPIC).build();
      case FACILITATOR_EVENT:
        return Redis.newBuilder().setTopicName(PIPELINE_FACILITATOR_EVENT_TOPIC).build();
      case NODE_START:
        return Redis.newBuilder().setTopicName(PIPELINE_NODE_START_EVENT_TOPIC).build();
      case PROGRESS_EVENT:
        return Redis.newBuilder().setTopicName(PIPELINE_PROGRESS_EVENT_TOPIC).build();
      case NODE_ADVISE:
        return Redis.newBuilder().setTopicName(PIPELINE_NODE_ADVISE_EVENT_TOPIC).build();
      case NODE_RESUME:
        return Redis.newBuilder().setTopicName(PIPELINE_NODE_RESUME_EVENT_TOPIC).build();
      case CREATE_PARTIAL_PLAN:
        return Redis.newBuilder().setTopicName(START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC).build();
      default:
        throw new InvalidRequestException("Not a valid Event Category");
    }
  }

  private static List<StepType> calculateStepTypes(Injector injector) {
    StepRegistry stepRegistry = injector.getInstance(StepRegistry.class);
    Map<StepType, Step> registry = stepRegistry.getRegistry();
    return registry == null ? Collections.emptyList() : new ArrayList<>(registry.keySet());
  }

  private static RetryPolicy<Object> createRetryPolicy() {
    return new RetryPolicy<>()
        .withBackoff(INITIAL_DELAY_MS, MAX_DELAY_MS, ChronoUnit.MILLIS, DELAY_FACTOR)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event
            -> log.warn(
                String.format("Pms sdk grpc retry attempt: %d", event.getAttemptCount()), event.getLastFailure()))
        .onFailure(event
            -> log.error(String.format("Pms sdk grpc retry failed after attempts: %d", event.getAttemptCount()),
                event.getFailure()))
        .handleIf(throwable -> {
          if (!(throwable instanceof StatusRuntimeException) && !(throwable instanceof InitializeSdkException)) {
            return false;
          }
          if (throwable instanceof InitializeSdkException) {
            return true;
          }
          StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
          return statusRuntimeException.getStatus().getCode() == Status.Code.UNAVAILABLE
              || statusRuntimeException.getStatus().getCode() == Status.Code.UNKNOWN
              || statusRuntimeException.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED
              || statusRuntimeException.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
        });
  }
}
