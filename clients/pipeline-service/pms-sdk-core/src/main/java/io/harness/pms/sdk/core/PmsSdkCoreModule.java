/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.pms.sdk.PmsSdkModuleUtils;
import io.harness.pms.sdk.core.exception.InvalidYamlExceptionHandler;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataServiceImpl;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionServiceImpl;
import io.harness.pms.sdk.core.interrupt.PMSInterruptService;
import io.harness.pms.sdk.core.interrupt.PMSInterruptServiceGrpcImpl;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeGrpcServiceImpl;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingGrpcOutputService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.response.publishers.RedisSdkResponseEventPublisher;
import io.harness.pms.sdk.core.response.publishers.SdkResponseEventPublisher;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkCoreModule extends AbstractModule {
  private static PmsSdkCoreModule instance;
  private final PmsSdkCoreConfig config;

  public static PmsSdkCoreModule getInstance(PmsSdkCoreConfig config) {
    if (instance == null) {
      instance = new PmsSdkCoreModule(config);
    }
    return instance;
  }

  private PmsSdkCoreModule(PmsSdkCoreConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    if (config.getSdkDeployMode().isNonLocal()) {
      install(PmsSdkGrpcModule.getInstance(config));
    } else {
      install(PmsSdkDummyGrpcModule.getInstance());
    }

    bind(PMSInterruptService.class).to(PMSInterruptServiceGrpcImpl.class).in(Singleton.class);
    bind(OutcomeService.class).to(OutcomeGrpcServiceImpl.class).in(Singleton.class);
    bind(ExecutionSweepingOutputService.class).to(ExecutionSweepingGrpcOutputService.class).in(Singleton.class);
    bind(SdkNodeExecutionService.class).to(SdkNodeExecutionServiceImpl.class).in(Singleton.class);
    bind(SdkGraphVisualizationDataService.class).to(SdkGraphVisualizationDataServiceImpl.class).in(Singleton.class);

    install(PmsSdkCoreEventsFrameworkModule.getInstance(
        config.getEventsFrameworkConfiguration(), config.getPipelineSdkRedisEventsConfig(), config.getServiceName()));
    bind(SdkResponseEventPublisher.class).to(RedisSdkResponseEventPublisher.class);
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
    InvalidYamlExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(InvalidYamlExceptionHandler.class));
  }

  @Provides
  @Singleton
  @Named(PmsSdkModuleUtils.SDK_SERVICE_NAME)
  public String serviceName() {
    return config.getServiceName();
  }

  @Provides
  @Singleton
  @Named(PmsSdkModuleUtils.CORE_EXECUTOR_NAME)
  public ExecutorService coreExecutorService() {
    return ThreadPool.create(config.getExecutionPoolConfig(),
        new ThreadFactoryBuilder().setNameFormat("PmsSdkCoreEventListener-%d").build());
  }

  @Provides
  @Singleton
  @Named(PmsSdkModuleUtils.PLAN_CREATOR_SERVICE_EXECUTOR)
  public Executor planCreatorInternalExecutorService() {
    return ThreadPool.create(config.getPlanCreatorServicePoolConfig(),
        new ThreadFactoryBuilder().setNameFormat("PlanCreatorInternalExecutorService-%d").build());
  }

  @Provides
  @Singleton
  @Named(PmsSdkModuleUtils.ORCHESTRATION_EVENT_EXECUTOR_NAME)
  public ExecutorService orchestrationEventExecutorService() {
    return ThreadPool.create(config.getOrchestrationEventPoolConfig(),
        new ThreadFactoryBuilder().setNameFormat("PmsSdkOrchestrationEventListener-%d").build());
  }
}
