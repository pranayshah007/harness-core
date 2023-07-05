/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.OrchestrationPublisherName.PERSISTENCE_LAYER;
import static io.harness.OrchestrationPublisherName.PUBLISHER_NAME;

import static java.util.Arrays.asList;

import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.delay.AbstractOrchestrationDelayModule;
import io.harness.engine.GovernanceService;
import io.harness.engine.GovernanceServiceImpl;
import io.harness.engine.NoopTaskExecutor;
import io.harness.engine.OrchestrationService;
import io.harness.engine.OrchestrationServiceImpl;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.execution.ExecutionInputServiceImpl;
import io.harness.engine.executions.node.NodeExecutionMonitorService;
import io.harness.engine.executions.node.NodeExecutionMonitorServiceImpl;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionMetadataServiceImpl;
import io.harness.engine.executions.plan.PlanExecutionMonitorService;
import io.harness.engine.executions.plan.PlanExecutionMonitorServiceImpl;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanExecutionServiceImpl;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.executions.plan.PlanServiceImpl;
import io.harness.engine.executions.stage.StageExecutionEntityService;
import io.harness.engine.executions.stage.StageExecutionEntityServiceImpl;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.engine.executions.step.StepExecutionEntityServiceImpl;
import io.harness.engine.expressions.EngineExpressionServiceImpl;
import io.harness.engine.expressions.ExpressionEvaluatorProvider;
import io.harness.engine.expressions.usages.ExpressionUsageService;
import io.harness.engine.expressions.usages.ExpressionUsageServiceImpl;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisher;
import io.harness.engine.facilitation.facilitator.publisher.RedisFacilitateEventPublisher;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.InterruptServiceImpl;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.engine.interrupts.handlers.publisher.RedisInterruptEventPublisher;
import io.harness.engine.pms.advise.publisher.NodeAdviseEventPublisher;
import io.harness.engine.pms.advise.publisher.RedisNodeAdviseEventPublisher;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.pms.data.PmsEngineExpressionServiceImpl;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsOutcomeServiceImpl;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.data.PmsSweepingOutputServiceImpl;
import io.harness.engine.pms.resume.publisher.NodeResumeEventPublisher;
import io.harness.engine.pms.resume.publisher.RedisNodeResumeEventPublisher;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.engine.pms.tasks.TaskExecutor;
import io.harness.engine.progress.publisher.ProgressEventPublisher;
import io.harness.engine.progress.publisher.RedisProgressEventPublisher;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.execution.expansion.PlanExpansionServiceImpl;
import io.harness.govern.ServersModule;
import io.harness.graph.stepDetail.PmsGraphStepDetailsServiceImpl;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.pms.NoopFeatureFlagServiceImpl;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.queue.TimerScheduledExecutorService;
import io.harness.serializer.KryoSerializer;
import io.harness.testing.TestExecution;
import io.harness.threading.ThreadPool;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationModule extends AbstractModule implements ServersModule {
  private static OrchestrationModule instance;
  private final OrchestrationModuleConfig config;

  public static OrchestrationModule getInstance(OrchestrationModuleConfig orchestrationModuleConfig) {
    if (instance == null) {
      instance = new OrchestrationModule(orchestrationModuleConfig);
    }
    return instance;
  }

  private OrchestrationModule(OrchestrationModuleConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    install(ExceptionModule.getInstance());
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(PersistenceLayer.SPRING).build();
      }
    });
    install(new AbstractOrchestrationDelayModule() {
      @Override
      public boolean forNG() {
        return true;
      }
    });
    install(OrchestrationBeansModule.getInstance());
    if (!config.isUseFeatureFlagService()) {
      bind(PmsFeatureFlagService.class).to(NoopFeatureFlagServiceImpl.class);
      bind(PipelineSettingsService.class).to(NoopPipelineSettingServiceImpl.class).in(Singleton.class);

    } else {
      install(new AccountClientModule(
          config.getAccountServiceHttpClientConfig(), config.getAccountServiceSecret(), config.getAccountClientId()));
      // ng-license dependencies
      install(NgLicenseHttpClientModule.getInstance(
          config.getLicenseClientConfig(), config.getLicenseClientServiceSecret(), config.getAccountClientId()));
      bind(PmsFeatureFlagService.class).to(PmsFeatureFlagHelper.class);
      bind(PipelineSettingsService.class).to(PipelineSettingsServiceImpl.class).in(Singleton.class);
    }
    bind(PlanExpansionService.class).to(PlanExpansionServiceImpl.class).in(Singleton.class);

    bind(NodeExecutionService.class).to(NodeExecutionServiceImpl.class).in(Singleton.class);
    bind(PlanExecutionService.class).to(PlanExecutionServiceImpl.class).in(Singleton.class);
    bind(PlanExecutionMonitorService.class).to(PlanExecutionMonitorServiceImpl.class).in(Singleton.class);
    bind(NodeExecutionMonitorService.class).to(NodeExecutionMonitorServiceImpl.class).in(Singleton.class);
    bind(PmsGraphStepDetailsService.class).to(PmsGraphStepDetailsServiceImpl.class);
    bind(ExecutionInputService.class).to(ExecutionInputServiceImpl.class);
    bind(StepExecutionEntityService.class).to(StepExecutionEntityServiceImpl.class);
    bind(ExpressionUsageService.class).to(ExpressionUsageServiceImpl.class).in(Singleton.class);
    bind(StageExecutionEntityService.class).to(StageExecutionEntityServiceImpl.class);

    bind(PlanService.class).to(PlanServiceImpl.class).in(Singleton.class);

    bind(InterruptService.class).to(InterruptServiceImpl.class).in(Singleton.class);
    bind(OrchestrationService.class).to(OrchestrationServiceImpl.class).in(Singleton.class);
    bind(PlanExecutionMetadataService.class).to(PlanExecutionMetadataServiceImpl.class).in(Singleton.class);
    bind(GovernanceService.class).to(GovernanceServiceImpl.class).in(Singleton.class);

    MapBinder<TaskCategory, TaskExecutor> taskExecutorMap =
        MapBinder.newMapBinder(binder(), TaskCategory.class, TaskExecutor.class);
    taskExecutorMap.addBinding(TaskCategory.UNKNOWN_CATEGORY).to(NoopTaskExecutor.class);
    taskExecutorMap.addBinding(TaskCategory.DELEGATE_TASK_V2).to(NgDelegate2TaskExecutor.class);

    // PMS Services
    bind(PmsSweepingOutputService.class).to(PmsSweepingOutputServiceImpl.class).in(Singleton.class);
    bind(PmsOutcomeService.class).to(PmsOutcomeServiceImpl.class).in(Singleton.class);
    bind(PmsEngineExpressionService.class).to(PmsEngineExpressionServiceImpl.class).in(Singleton.class);

    if (!config.isWithPMS()) {
      bind(EngineExpressionService.class).to(EngineExpressionServiceImpl.class);
    }

    MapBinder<String, TestExecution> testExecutionMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TestExecution.class);
    Provider<KryoSerializer> kryoSerializerProvider = getProvider(Key.get(KryoSerializer.class));
    testExecutionMapBinder.addBinding("Callback Kryo Registration")
        .toInstance(() -> OrchestrationComponentTester.testKryoRegistration(kryoSerializerProvider));

    install(new OrchestrationEventsFrameworkModule(config.getEventsFrameworkConfiguration()));
    bind(InterruptEventPublisher.class).to(RedisInterruptEventPublisher.class);
    bind(FacilitateEventPublisher.class).to(RedisFacilitateEventPublisher.class).in(Singleton.class);
    bind(ProgressEventPublisher.class).to(RedisProgressEventPublisher.class).in(Singleton.class);
    bind(NodeAdviseEventPublisher.class).to(RedisNodeAdviseEventPublisher.class).in(Singleton.class);
    bind(NodeResumeEventPublisher.class).to(RedisNodeResumeEventPublisher.class).in(Singleton.class);
  }

  @Provides
  @Named(PERSISTENCE_LAYER)
  PersistenceLayer usedPersistenceLayer() {
    return PersistenceLayer.SPRING;
  }

  @Provides
  @Singleton
  @Named("EngineExecutorService")
  public ExecutorService engineExecutionServiceThreadPool() {
    return ThreadPool.create(config.getCorePoolSize(), config.getMaxPoolSize(), config.getIdleTimeInSecs(),
        TimeUnit.SECONDS, new ThreadFactoryBuilder().setNameFormat("EngineExecutorService-%d").build());
  }

  @Provides
  @Singleton
  public ExpressionEvaluatorProvider expressionEvaluatorProvider() {
    return config.getExpressionEvaluatorProvider();
  }

  @Provides
  @Named(PUBLISHER_NAME)
  public String publisherName() {
    return config.getPublisherName();
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(
      WaitNotifyEngine waitNotifyEngine, @Named(PUBLISHER_NAME) String publisherName) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, publisherName);
  }

  @Override
  public List<Closeable> servers(Injector injector) {
    return asList(() -> injector.getInstance(TimerScheduledExecutorService.class).shutdownNow());
  }

  @Provides
  @Singleton
  public OrchestrationModuleConfig orchestrationModuleConfig() {
    return config;
  }

  @Provides
  @Singleton
  @Named("orchestrationLogCache")
  public Cache<String, Long> orchestrationLogCache(HarnessCacheManager harnessCacheManager,
      VersionInfoManager versionInfoManager, OrchestrationLogCacheListener orchestrationLogCacheListener) {
    Cache<String, Long> cache = harnessCacheManager.getCache("orchestrationLogCache", String.class, Long.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
    MutableConfiguration<String, Long> config = new MutableConfiguration<>();
    config.setTypes(String.class, Long.class);
    cache.registerCacheEntryListener(
        new MutableCacheEntryListenerConfiguration(FactoryBuilder.factoryOf(orchestrationLogCacheListener),
            FactoryBuilder.factoryOf(orchestrationLogCacheListener), false, false));
    return cache;
  }

  @Provides
  @Singleton
  public OrchestrationLogConfiguration orchestrationLogConfiguration() {
    return config.getOrchestrationLogConfiguration();
  }

  @Provides
  @Singleton
  public OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration() {
    return config.getOrchestrationRestrictionConfiguration();
  }
}
