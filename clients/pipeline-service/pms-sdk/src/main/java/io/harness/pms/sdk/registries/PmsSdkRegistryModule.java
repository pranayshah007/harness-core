/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.registries;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.GeneralException;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.execution.events.node.facilitate.Facilitator;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.core.registries.AdviserRegistry;
import io.harness.pms.sdk.core.registries.FacilitatorRegistry;
import io.harness.pms.sdk.core.registries.FunctorRegistry;
import io.harness.pms.sdk.core.registries.JsonExpansionHandlerRegistry;
import io.harness.pms.sdk.core.registries.OrchestrationEventHandlerRegistry;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.registries.registrar.local.PmsSdkAdviserRegistrar;
import io.harness.pms.sdk.registries.registrar.local.PmsSdkFacilitatorRegistrar;
import io.harness.pms.sdk.registries.registrar.local.PmsSdkOrchestrationEventRegistrars;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class PmsSdkRegistryModule extends AbstractModule {
  private final PmsSdkConfiguration config;

  private static PmsSdkRegistryModule instance;

  public static synchronized PmsSdkRegistryModule getInstance(PmsSdkConfiguration config) {
    if (instance == null) {
      instance = new PmsSdkRegistryModule(config);
    }
    return instance;
  }

  public PmsSdkRegistryModule(PmsSdkConfiguration config) {
    this.config = config;
  }

  public void configure() {}

  @Provides
  @Singleton
  StepRegistry providesStateRegistry(Injector injector) {
    StepRegistry stepRegistry = new StepRegistry();
    Map<StepType, Class<? extends Step>> engineSteps = config.getEngineSteps();
    List<String> stepsMissingStepCategory = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(engineSteps)) {
      engineSteps.forEach((stepType, v) -> {
        if (stepType.getStepCategory() == StepCategory.UNKNOWN) {
          stepsMissingStepCategory.add(stepType.getType());
        }
        stepRegistry.register(stepType, injector.getInstance(v));
      });
    }
    if (stepsMissingStepCategory.isEmpty()) {
      return stepRegistry;
    }
    throw new GeneralException(String.format("Following steps missing step Category, please add the category: [%s]",
        stepsMissingStepCategory.stream().collect(Collectors.joining(","))));
  }

  @Provides
  @Singleton
  FunctorRegistry providesFunctorRegistry(Injector injector) {
    FunctorRegistry functorRegistry = new FunctorRegistry();
    Map<String, Class<? extends SdkFunctor>> sdkFunctors = config.getSdkFunctors();
    if (EmptyPredicate.isNotEmpty(sdkFunctors)) {
      sdkFunctors.forEach((functorKey, v) -> { functorRegistry.register(functorKey, injector.getInstance(v)); });
    }
    return functorRegistry;
  }

  @Provides
  @Singleton
  JsonExpansionHandlerRegistry providesJsonExpansionHandlerRegistry(Injector injector) {
    JsonExpansionHandlerRegistry jsonExpansionHandlerRegistry = new JsonExpansionHandlerRegistry();
    List<JsonExpansionHandlerInfo> expansionHandlers = config.getJsonExpansionHandlers();
    if (EmptyPredicate.isNotEmpty(expansionHandlers)) {
      expansionHandlers.forEach(handlerInfo
          -> jsonExpansionHandlerRegistry.register(
              handlerInfo.getJsonExpansionInfo().getKey(), injector.getInstance(handlerInfo.getExpansionHandler())));
    }
    return jsonExpansionHandlerRegistry;
  }

  @Provides
  @Singleton
  AdviserRegistry providesAdviserRegistry(Injector injector) {
    AdviserRegistry adviserRegistry = new AdviserRegistry();
    Map<AdviserType, Class<? extends Adviser>> engineAdvisers = config.getEngineAdvisers();
    if (EmptyPredicate.isEmpty(engineAdvisers)) {
      engineAdvisers = new HashMap<>();
    }
    engineAdvisers.putAll(PmsSdkAdviserRegistrar.getEngineAdvisers());
    if (EmptyPredicate.isNotEmpty(engineAdvisers)) {
      engineAdvisers.forEach((k, v) -> adviserRegistry.register(k, injector.getInstance(v)));
    }
    return adviserRegistry;
  }

  @Provides
  @Singleton
  FacilitatorRegistry providesFacilitatorRegistry(Injector injector) {
    FacilitatorRegistry facilitatorRegistry = new FacilitatorRegistry();
    Map<FacilitatorType, Class<? extends Facilitator>> engineFacilitators = config.getEngineFacilitators();
    if (EmptyPredicate.isEmpty(engineFacilitators)) {
      engineFacilitators = new HashMap<>();
    }
    engineFacilitators.putAll(PmsSdkFacilitatorRegistrar.getEngineFacilitators());
    engineFacilitators.forEach((k, v) -> facilitatorRegistry.register(k, injector.getInstance(v)));
    return facilitatorRegistry;
  }

  @Provides
  @Singleton
  OrchestrationEventHandlerRegistry providesEventHandlerRegistry(Injector injector) {
    OrchestrationEventHandlerRegistry handlerRegistry = new OrchestrationEventHandlerRegistry();
    Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> engineEventHandlersMap =
        config.getEngineEventHandlersMap();
    if (EmptyPredicate.isNotEmpty(engineEventHandlersMap)) {
      mergeEventHandlers(engineEventHandlersMap, PmsSdkOrchestrationEventRegistrars.getHandlers());
      engineEventHandlersMap.forEach((key, value) -> {
        Set<OrchestrationEventHandler> eventHandlerSet = new HashSet<>();
        value.forEach(v -> eventHandlerSet.add(injector.getInstance(v)));
        handlerRegistry.register(key, eventHandlerSet);
      });
    } else {
      PmsSdkOrchestrationEventRegistrars.getHandlers().forEach((key, value) -> {
        Set<OrchestrationEventHandler> eventHandlerSet = new HashSet<>();
        value.forEach(v -> eventHandlerSet.add(injector.getInstance(v)));
        handlerRegistry.register(key, eventHandlerSet);
      });
    }
    return handlerRegistry;
  }

  private void mergeEventHandlers(
      Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> finalHandlers,
      Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> handlers) {
    for (Map.Entry<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> entry :
        handlers.entrySet()) {
      if (finalHandlers.containsKey(entry.getKey())) {
        Set<Class<? extends OrchestrationEventHandler>> existing = finalHandlers.get(entry.getKey());
        existing.addAll(entry.getValue());
        finalHandlers.put(entry.getKey(), existing);
      } else {
        finalHandlers.put(entry.getKey(), entry.getValue());
      }
    }
  }
}
