/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.AuthorizationServiceHeader.MANAGER;
import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.lock.DistributedLockImplementation.MONGO;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.cache.HarnessCacheManager;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.client.DelegateSelectionLogHttpClientModule;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.datastructures.DistributedBackend;
import io.harness.datastructures.EphemeralServiceModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.server.PipelineServiceGrpcModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngtriggers.outbox.TriggerOutboxEventHandler;
import io.harness.opaclient.OpaClientModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.pms.Dashboard.PMSLandingDashboardService;
import io.harness.pms.Dashboard.PMSLandingDashboardServiceImpl;
import io.harness.pms.approval.ApprovalResourceService;
import io.harness.pms.approval.ApprovalResourceServiceImpl;
import io.harness.pms.approval.jira.JiraApprovalHelperServiceImpl;
import io.harness.pms.approval.notification.ApprovalNotificationHandlerImpl;
import io.harness.pms.approval.servicenow.ServiceNowApprovalHelperServiceImpl;
import io.harness.pms.barriers.service.PMSBarrierService;
import io.harness.pms.barriers.service.PMSBarrierServiceImpl;
import io.harness.pms.event.entitycrud.PipelineEntityCRUDStreamListener;
import io.harness.pms.event.entitycrud.ProjectEntityCrudStreamListener;
import io.harness.pms.event.pollingevent.PollingEventStreamListener;
import io.harness.pms.expressions.PMSExpressionEvaluatorProvider;
import io.harness.pms.jira.JiraStepHelperServiceImpl;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetServiceImpl;
import io.harness.pms.opa.service.PMSOpaService;
import io.harness.pms.opa.service.PMSOpaServiceImpl;
import io.harness.pms.outbox.PMSOutboxEventHandler;
import io.harness.pms.outbox.PipelineOutboxEventHandler;
import io.harness.pms.pipeline.mappers.PipelineFilterPropertiesMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceImpl;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PMSYamlSchemaServiceImpl;
import io.harness.pms.pipeline.service.PipelineDashboardService;
import io.harness.pms.pipeline.service.PipelineDashboardServiceImpl;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.pipeline.service.PipelineEnforcementServiceImpl;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.service.PipelineMetadataServiceImpl;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaServiceImpl;
import io.harness.pms.pipeline.service.yamlschema.cache.PartialSchemaDTOWrapperValue;
import io.harness.pms.pipeline.service.yamlschema.cache.YamlSchemaDetailsWrapperValue;
import io.harness.pms.pipeline.service.yamlschema.customstage.CustomStageYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.customstage.CustomStageYamlSchemaServiceImpl;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlService;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlServiceImpl;
import io.harness.pms.plan.creation.NodeTypeLookupService;
import io.harness.pms.plan.creation.NodeTypeLookupServiceImpl;
import io.harness.pms.plan.execution.mapper.PipelineExecutionFilterPropertiesMapper;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.execution.service.PMSExecutionServiceImpl;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryServiceImpl;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.pms.preflight.service.PreflightServiceImpl;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.rbac.validator.PipelineRbacServiceImpl;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintService;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintServiceImpl;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.servicenow.ServiceNowStepHelperServiceImpl;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.pms.template.service.PipelineRefreshServiceImpl;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionService;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImpl;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImplV2;
import io.harness.polling.client.PollResourceClientModule;
import io.harness.project.ProjectClientModule;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGTriggerRegistrars;
import io.harness.serializer.OrchestrationStepsModuleRegistrars;
import io.harness.serializer.PipelineServiceModuleRegistrars;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.jira.JiraApprovalHelperService;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.jira.JiraStepHelperService;
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptHelperServiceImpl;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.template.TemplateResourceClientModule;
import io.harness.threading.ThreadPool;
import io.harness.time.TimeModule;
import io.harness.timescaledb.JooqModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.timescaledb.metrics.HExecuteListener;
import io.harness.token.TokenClientModule;
import io.harness.tracing.AbstractPersistenceTracerModule;
import io.harness.user.UserClientModule;
import io.harness.usergroups.UserGroupClientModule;
import io.harness.variable.VariableClientModule;
import io.harness.version.VersionInfoManager;
import io.harness.webhook.WebhookEventClientModule;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.client.YamlSchemaClientModule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteListener;
import org.mongodb.morphia.converters.TypeConverter;
import org.redisson.api.RedissonClient;
import org.springframework.core.convert.converter.Converter;

@OwnedBy(PIPELINE)
@Slf4j
public class PipelineServiceModule extends AbstractModule {
  private final PipelineServiceConfiguration configuration;

  private static PipelineServiceModule instance;
  // TODO: Take this from application.

  private PipelineServiceModule(PipelineServiceConfiguration configuration) {
    this.configuration = configuration;
  }

  public static PipelineServiceModule getInstance(PipelineServiceConfiguration appConfig) {
    if (instance == null) {
      instance = new PipelineServiceModule(appConfig);
    }
    return instance;
  }

  @Override
  protected void configure() {
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    install(new AbstractPersistenceTracerModule() {
      @Override
      protected RedisConfig redisConfigProvider() {
        return configuration.getEventsFrameworkConfiguration().getRedisConfig();
      }

      @Override
      protected String serviceIdProvider() {
        return PIPELINE_SERVICE.getServiceId();
      }
    });
    install(PipelineServiceGrpcModule.getInstance());
    install(new PipelinePersistenceModule());
    install(DelegateServiceDriverModule.getInstance(true, false));
    install(OrchestrationModule.getInstance(
        OrchestrationModuleConfig.builder()
            .serviceName("PIPELINE")
            .expressionEvaluatorProvider(new PMSExpressionEvaluatorProvider())
            .withPMS(false)
            .isPipelineService(true)
            .corePoolSize(configuration.getOrchestrationPoolConfig().getCorePoolSize())
            .maxPoolSize(configuration.getOrchestrationPoolConfig().getMaxPoolSize())
            .idleTimeInSecs(configuration.getOrchestrationPoolConfig().getIdleTime())
            .eventsFrameworkConfiguration(configuration.getEventsFrameworkConfiguration())
            .accountClientId(PIPELINE_SERVICE.getServiceId())
            .accountServiceHttpClientConfig(configuration.getManagerClientConfig())
            .accountServiceSecret(configuration.getManagerServiceSecret())
            .useFeatureFlagService(true)
            .orchestrationRedisEventsConfig(configuration.getOrchestrationRedisEventsConfig())
            .orchestrationLogConfiguration(configuration.getOrchestrationLogConfiguration())
            .build()));
    install(OrchestrationStepsModule.getInstance(configuration.getOrchestrationStepConfig()));
    install(OrchestrationVisualizationModule.getInstance(configuration.getEventsFrameworkConfiguration(),
        configuration.getOrchestrationVisualizationThreadPoolConfig()));
    install(PrimaryVersionManagerModule.getInstance());
    install(new DelegateServiceDriverGrpcClientModule(configuration.getManagerServiceSecret(),
        configuration.getManagerTarget(), configuration.getManagerAuthority(), true));
    install(new ConnectorResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new TemplateResourceClientModule(configuration.getTemplateServiceClientConfig(),
        configuration.getTemplateServiceSecret(), PIPELINE_SERVICE.toString()));
    install(NGTriggersModule.getInstance(configuration.getTriggerConfig(),
        configuration.getPipelineServiceClientConfig(), configuration.getPipelineServiceSecret()));
    install(PersistentLockModule.getInstance());
    install(EphemeralServiceModule.getInstance());
    install(TimeModule.getInstance());
    install(FiltersModule.getInstance());
    install(YamlSdkModule.getInstance());
    install(JooqModule.getInstance());
    install(AccessControlClientModule.getInstance(
        configuration.getAccessControlClientConfiguration(), PIPELINE_SERVICE.getServiceId()));
    install(new PollResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), MANAGER.getServiceId()));

    install(new OrganizationClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new ProjectClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(
        YamlSchemaClientModule.getInstance(configuration.getYamlSchemaClientConfig(), PIPELINE_SERVICE.getServiceId()));
    install(new UserClientModule(configuration.getManagerClientConfig(), configuration.getManagerServiceSecret(),
        PIPELINE_SERVICE.getServiceId()));
    install(new UserGroupClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new DelegateSelectionLogHttpClientModule(configuration.getManagerClientConfig(),
        configuration.getManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new PipelineServiceEventsFrameworkModule(
        configuration.getEventsFrameworkConfiguration(), configuration.getPipelineRedisEventsConfig()));
    install(new EntitySetupUsageClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new LogStreamingModule(configuration.getLogStreamingServiceConfig().getBaseUrl()));
    install(new OpaClientModule(
        configuration.getOpaServerConfig().getBaseUrl(), configuration.getOpaServerConfig().getSecret()));
    install(
        new AuditClientModule(this.configuration.getAuditClientConfig(), this.configuration.getManagerServiceSecret(),
            PIPELINE_SERVICE.getServiceId(), this.configuration.isEnableAudit()));
    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, PIPELINE_SERVICE.getServiceId(), false));
    install(new TokenClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new WebhookEventClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return configuration.getSegmentConfiguration();
      }
    });
    install(new VariableClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(PMSOutboxEventHandler.class);
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(PipelineMetadataService.class).to(PipelineMetadataServiceImpl.class);

    bind(PMSPipelineService.class).to(PMSPipelineServiceImpl.class);
    bind(PmsExecutionSummaryService.class).to(PmsExecutionSummaryServiceImpl.class);

    bind(PreflightService.class).to(PreflightServiceImpl.class);
    bind(PipelineRbacService.class).to(PipelineRbacServiceImpl.class);
    bind(PMSInputSetService.class).to(PMSInputSetServiceImpl.class);
    bind(PMSExecutionService.class).to(PMSExecutionServiceImpl.class);
    bind(PMSYamlSchemaService.class).to(PMSYamlSchemaServiceImpl.class);
    bind(ApprovalNotificationHandler.class).to(ApprovalNotificationHandlerImpl.class);
    bind(PMSOpaService.class).to(PMSOpaServiceImpl.class);
    bind(ShellScriptHelperService.class).to(ShellScriptHelperServiceImpl.class);
    bind(ApprovalYamlSchemaService.class).to(ApprovalYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(CustomStageYamlSchemaService.class).to(CustomStageYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(FeatureFlagYamlService.class).to(FeatureFlagYamlServiceImpl.class).in(Singleton.class);
    bind(PipelineEnforcementService.class).to(PipelineEnforcementServiceImpl.class).in(Singleton.class);

    bind(PipelineRefreshService.class).to(PipelineRefreshServiceImpl.class);
    bind(NodeTypeLookupService.class).to(NodeTypeLookupServiceImpl.class);

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("progressUpdateServiceExecutor"))
        .toInstance(new ManagedScheduledExecutorService("ProgressUpdateServiceExecutor-Thread"));
    bind(TriggerWebhookExecutionService.class).to(TriggerWebhookExecutionServiceImpl.class);
    bind(TriggerWebhookExecutionServiceV2.class).to(TriggerWebhookExecutionServiceImplV2.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("telemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("pipeline-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));

    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.PIPELINESETUP.toString()).to(PipelineFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.PIPELINEEXECUTION.toString())
        .to(PipelineExecutionFilterPropertiesMapper.class);

    bind(PMSBarrierService.class).to(PMSBarrierServiceImpl.class);
    bind(ApprovalResourceService.class).to(ApprovalResourceServiceImpl.class);
    bind(JiraApprovalHelperService.class).to(JiraApprovalHelperServiceImpl.class);
    bind(JiraStepHelperService.class).to(JiraStepHelperServiceImpl.class);
    bind(PMSResourceConstraintService.class).to(PMSResourceConstraintServiceImpl.class);
    bind(PMSLandingDashboardService.class).to(PMSLandingDashboardServiceImpl.class);
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder()
                        .logStreamingServiceBaseUrl(configuration.getLogStreamingServiceConfig().getBaseUrl())
                        .build());

    bind(PipelineDashboardService.class).to(PipelineDashboardServiceImpl.class);
    bind(ServiceNowApprovalHelperService.class).to(ServiceNowApprovalHelperServiceImpl.class);
    bind(ServiceNowStepHelperService.class).to(ServiceNowStepHelperServiceImpl.class);
    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }

    if (configuration.getEnableDashboardTimescale() != null && configuration.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(configuration.getTimeScaleDBConfig() != null ? configuration.getTimeScaleDBConfig()
                                                                   : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }
    install(EnforcementClientModule.getInstance(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId(),
        configuration.getEnforcementClientConfiguration()));
    registerEventsFrameworkMessageListeners();
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ResourceTypeConstants.TRIGGER).to(TriggerOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ResourceTypeConstants.PIPELINE).to(PipelineOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ResourceTypeConstants.INPUT_SET).to(PipelineOutboxEventHandler.class);
  }

  private void registerEventsFrameworkMessageListeners() {
    bind(MessageListener.class)
        .annotatedWith(Names.named(PIPELINE_ENTITY + ENTITY_CRUD))
        .to(PipelineEntityCRUDStreamListener.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(PROJECT_ENTITY + ENTITY_CRUD))
        .to(ProjectEntityCrudStreamListener.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.POLLING_EVENTS_STREAM))
        .to(PollingEventStreamListener.class);
  }

  @Provides
  @Singleton
  public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(PipelineServiceModuleRegistrars.kryoRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(PipelineServiceModuleRegistrars.morphiaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends TypeConverter>> morphiaConverters() {
    return ImmutableSet.<Class<? extends TypeConverter>>builder()
        .addAll(PipelineServiceModuleRegistrars.morphiaConverters)
        .build();
  }

  @Provides
  @Singleton
  List<Class<? extends Converter<?, ?>>> springConverters() {
    return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
        .addAll(PipelineServiceModuleRegistrars.springConverters)
        .build();
  }

  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder()
        .addAll(OrchestrationStepsModuleRegistrars.yamlSchemaRegistrars)
        .addAll(NGTriggerRegistrars.yamlSchemaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  @Named("cacheRedissonClient")
  RedissonClient cacheRedissonClient() {
    return RedisUtils.getClient(configuration.getRedisLockConfig());
  }

  @Provides
  @Singleton
  DistributedBackend distributedBackend() {
    return DistributedBackend.REDIS;
  }

  @Provides
  @Singleton
  @Named("PSQLExecuteListener")
  ExecuteListener executeListener() {
    return HExecuteListener.getInstance();
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig(PipelineServiceConfiguration configuration) {
    return configuration.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "pms_delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "pms_delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "pms_delegateTaskProgressResponses")
        .build();
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return (Supplier<DelegateCallbackToken>) Suppliers.memoize(
        () -> getDelegateCallbackToken(delegateServiceGrpcClient));
  }

  private DelegateCallbackToken getDelegateCallbackToken(DelegateServiceGrpcClient delegateServiceClient) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("pms")
                                  .setConnection(configuration.getMongoConfig().getUri())
                                  .build())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return configuration.getDistributedLockImplementation() == null ? MONGO
                                                                    : configuration.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return configuration.getRedisLockConfig();
  }

  @Provides
  @Singleton
  @Named("templateRegistrationExecutorService")
  public ExecutorService templateRegistrationExecutionServiceThreadPool() {
    return ThreadPool.create(
        1, 1, 10, TimeUnit.SECONDS, new ThreadFactoryBuilder().setNameFormat("TemplateRegistrationService-%d").build());
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();

    PipelineServiceApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType =
        HarnessReflections.get().getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);
    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapperWithoutNamed() {
    return Jackson.newObjectMapper();
  }

  @Provides
  @Singleton
  public LogStreamingServiceConfiguration getLogStreamingServiceConfiguration() {
    return configuration.getLogStreamingServiceConfig();
  }

  @Provides
  @Singleton
  public PipelineServiceIteratorsConfig getIteratorsConfig() {
    return configuration.getIteratorsConfig() == null ? PipelineServiceIteratorsConfig.builder().build()
                                                      : configuration.getIteratorsConfig();
  }

  @Provides
  @Singleton
  @Named("shouldUseInstanceCache")
  public boolean shouldUseInstanceCache() {
    return configuration.isShouldUseInstanceCache();
  }

  @Provides
  @Singleton
  @Named("PipelineExecutorService")
  public ExecutorService pipelineExecutorService() {
    return ThreadPool.create(configuration.getPipelineExecutionPoolConfig().getCorePoolSize(),
        configuration.getPipelineExecutionPoolConfig().getMaxPoolSize(),
        configuration.getPipelineExecutionPoolConfig().getIdleTime(),
        configuration.getPipelineExecutionPoolConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("PipelineExecutorService-%d").build());
  }

  @Provides
  @Singleton
  @Named("PlanCreatorMergeExecutorService")
  public Executor planCreatorMergeExecutorService() {
    return ThreadPool.create(configuration.getPlanCreatorMergeServicePoolConfig().getCorePoolSize(),
        configuration.getPlanCreatorMergeServicePoolConfig().getMaxPoolSize(),
        configuration.getPlanCreatorMergeServicePoolConfig().getIdleTime(),
        configuration.getPlanCreatorMergeServicePoolConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("PipelineExecutorService-%d").build());
  }

  @Provides
  @Singleton
  @Named("JsonExpansionExecutorService")
  public Executor jsonExpansionExecutorService() {
    return ThreadPool.create(configuration.getJsonExpansionPoolConfig().getCorePoolSize(),
        configuration.getJsonExpansionPoolConfig().getMaxPoolSize(),
        configuration.getJsonExpansionPoolConfig().getIdleTime(),
        configuration.getJsonExpansionPoolConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("JsonExpansionExecutorService-%d").build());
  }

  @Provides
  @Singleton
  @Named("TelemetrySenderExecutor")
  public Executor telemetrySenderExecutor() {
    return ThreadPool.create(
        1, 2, 25, TimeUnit.SECONDS, new ThreadFactoryBuilder().setNameFormat("TelemetrySenderExecutor-%d").build());
  }

  @Provides
  @Singleton
  @Named("pmsEventsCache")
  public Cache<String, Integer> sdkEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("pmsEventsCache", String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("pmsSdkInstanceCache")
  public Cache<String, PmsSdkInstance> sdkInstanceCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    String cacheName = String.format("pmsSdkInstanceCache-%s", versionInfoManager.getVersionInfo().getBuildNo());
    return harnessCacheManager.getCache(cacheName, String.class, PmsSdkInstance.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 5)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("schemaDetailsCache")
  public Cache<SchemaCacheKey, YamlSchemaDetailsWrapperValue> schemaDetailsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("schemaDetailsCache", SchemaCacheKey.class, YamlSchemaDetailsWrapperValue.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("partialSchemaCache")
  public Cache<SchemaCacheKey, PartialSchemaDTOWrapperValue> partialSchemaCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("partialSchemaCache", SchemaCacheKey.class, PartialSchemaDTOWrapperValue.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("allowedParallelStages")
  public Integer getAllowedParallelStages() {
    return configuration.getAllowedParallelStages();
  }

  @Provides
  @Singleton
  @Named("planCreatorMergeServiceDependencyBatch")
  public Integer getPlanCreatorMergeServiceDependencyBatch() {
    return configuration.getPlanCreatorMergeServiceDependencyBatch();
  }

  @Provides
  @Singleton
  @Named("jsonExpansionRequestBatchSize")
  public Integer getjsonExpansionRequestBatchSize() {
    return configuration.getJsonExpansionBatchSize();
  }
}
