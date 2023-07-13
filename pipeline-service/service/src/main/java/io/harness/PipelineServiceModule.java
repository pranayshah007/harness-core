/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.BEARER;
import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
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
import io.harness.ci.CiServiceResourceClientModule;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.client.DelegateSelectionLogHttpClientModule;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.datastructures.DistributedBackend;
import io.harness.datastructures.EphemeralServiceModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.ff.FeatureFlagModule;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.server.PipelineServiceGrpcModule;
import io.harness.hsqs.client.HsqsServiceClientModule;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngsettings.client.remote.NGSettingsClientModule;
import io.harness.ngtriggers.outbox.TriggerOutboxEventHandler;
import io.harness.opaclient.OpaClientModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.plancreator.steps.pluginstep.ContainerStepV2PluginProvider;
import io.harness.pms.approval.ApprovalResourceService;
import io.harness.pms.approval.ApprovalResourceServiceImpl;
import io.harness.pms.approval.api.ApprovalsApiImpl;
import io.harness.pms.approval.custom.CustomApprovalHelperServiceImpl;
import io.harness.pms.approval.jira.JiraApprovalHelperServiceImpl;
import io.harness.pms.approval.notification.ApprovalNotificationHandlerImpl;
import io.harness.pms.approval.resources.ApprovalResource;
import io.harness.pms.approval.resources.ApprovalResourceImpl;
import io.harness.pms.approval.servicenow.ServiceNowApprovalHelperServiceImpl;
import io.harness.pms.barriers.resources.PMSBarrierResource;
import io.harness.pms.barriers.resources.PMSBarrierResourceImpl;
import io.harness.pms.barriers.service.PMSBarrierService;
import io.harness.pms.barriers.service.PMSBarrierServiceImpl;
import io.harness.pms.dashboard.PMSLandingDashboardResource;
import io.harness.pms.dashboard.PMSLandingDashboardResourceImpl;
import io.harness.pms.dashboard.PMSLandingDashboardService;
import io.harness.pms.dashboard.PMSLandingDashboardServiceImpl;
import io.harness.pms.dashboard.PipelineDashboardOverviewResource;
import io.harness.pms.dashboard.PipelineDashboardOverviewResourceImpl;
import io.harness.pms.dashboard.PipelineDashboardOverviewResourceV2;
import io.harness.pms.dashboard.PipelineDashboardOverviewResourceV2Impl;
import io.harness.pms.event.entitycrud.AccountEntityCrudStreamListener;
import io.harness.pms.event.entitycrud.PipelineEntityCRUDStreamListener;
import io.harness.pms.event.entitycrud.ProjectEntityCrudStreamListener;
import io.harness.pms.event.pollingevent.PollingEventStreamListener;
import io.harness.pms.event.triggerwebhookevent.TriggerExecutionEventStreamListener;
import io.harness.pms.expressions.PMSExpressionEvaluatorProvider;
import io.harness.pms.health.HealthResource;
import io.harness.pms.health.HealthResourceImpl;
import io.harness.pms.jira.JiraStepHelperServiceImpl;
import io.harness.pms.ngpipeline.inputs.api.InputsApiImpl;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsService;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsServiceImpl;
import io.harness.pms.ngpipeline.inputset.api.InputSetsApiImpl;
import io.harness.pms.ngpipeline.inputset.resources.InputSetResourcePMS;
import io.harness.pms.ngpipeline.inputset.resources.InputSetResourcePMSImpl;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetServiceImpl;
import io.harness.pms.opa.service.PMSOpaService;
import io.harness.pms.opa.service.PMSOpaServiceImpl;
import io.harness.pms.outbox.PMSOutboxEventHandler;
import io.harness.pms.outbox.PipelineOutboxEventHandler;
import io.harness.pms.pipeline.PipelineResource;
import io.harness.pms.pipeline.PipelineResourceImpl;
import io.harness.pms.pipeline.api.PipelinesApiImpl;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceServiceImpl;
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
import io.harness.pms.pipeline.service.yamlschema.pipelinestage.PipelineStageYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.pipelinestage.PipelineStageYamlSchemaServiceImpl;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationServiceImpl;
import io.harness.pms.pipeline.validation.service.PipelineValidationService;
import io.harness.pms.pipeline.validation.service.PipelineValidationServiceImpl;
import io.harness.pms.plan.creation.NodeTypeLookupService;
import io.harness.pms.plan.creation.NodeTypeLookupServiceImpl;
import io.harness.pms.plan.execution.PlanExecutionResource;
import io.harness.pms.plan.execution.PlanExecutionResourceImpl;
import io.harness.pms.plan.execution.mapper.PipelineExecutionFilterPropertiesMapper;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.execution.service.PMSExecutionServiceImpl;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryServiceImpl;
import io.harness.pms.plugin.ContainerStepV2PluginProviderImpl;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.pms.preflight.service.PreflightServiceImpl;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.rbac.validator.PipelineRbacServiceImpl;
import io.harness.pms.resourceconstraints.resources.PMSResourceConstraintResource;
import io.harness.pms.resourceconstraints.resources.PMSResourceConstraintResourceImpl;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintService;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintServiceImpl;
import io.harness.pms.schema.PmsYamlSchemaResource;
import io.harness.pms.schema.PmsYamlSchemaResourceImpl;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.servicenow.ServiceNowStepHelperServiceImpl;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.pms.template.service.PipelineRefreshServiceImpl;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionService;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookEventExecutionServiceImpl;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImpl;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImplV2;
import io.harness.pms.wait.WaitStepResource;
import io.harness.pms.wait.WaitStepResourceImpl;
import io.harness.polling.client.PollResourceClientModule;
import io.harness.project.ProjectClientModule;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGTriggerRegistrars;
import io.harness.serializer.OrchestrationStepsModuleRegistrars;
import io.harness.serializer.PipelineServiceModuleRegistrars;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.spec.server.pipeline.v1.ApprovalsApi;
import io.harness.spec.server.pipeline.v1.InputSetsApi;
import io.harness.spec.server.pipeline.v1.InputsApi;
import io.harness.spec.server.pipeline.v1.PipelinesApi;
import io.harness.ssca.client.SSCAServiceClientModuleV2;
import io.harness.steps.PodCleanUpModule;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.custom.CustomApprovalHelperService;
import io.harness.steps.approval.step.jira.JiraApprovalHelperService;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.jira.JiraStepHelperService;
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptHelperServiceImpl;
import io.harness.steps.wait.WaitStepService;
import io.harness.steps.wait.WaitStepServiceImpl;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.template.TemplateResourceClientModule;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;
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
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteListener;
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
      protected EventsFrameworkConfiguration eventsFrameworkConfiguration() {
        return configuration.getEventsFrameworkConfiguration();
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
            .orchestrationRestrictionConfiguration(configuration.getOrchestrationRestrictionConfiguration())
            .licenseClientServiceSecret(configuration.getNgManagerServiceSecret())
            .licenseClientConfig(configuration.getNgManagerServiceHttpClientConfig())
            .licenseClientId(PIPELINE_SERVICE.getServiceId())
            .expandedJsonLockConfig(configuration.getExpandedJsonLockConfig())
            .build()));
    install(OrchestrationStepsModule.getInstance(configuration.getOrchestrationStepConfig()));
    install(FeatureFlagModule.getInstance());
    install(OrchestrationVisualizationModule.getInstance(configuration.getEventsFrameworkConfiguration(),
        configuration.getOrchestrationVisualizationThreadPoolConfig()));
    install(PodCleanUpModule.getInstance(configuration.getPodCleanUpThreadPoolConfig()));
    install(PrimaryVersionManagerModule.getInstance());
    install(new DelegateServiceDriverGrpcClientModule(configuration.getManagerServiceSecret(),
        configuration.getManagerTarget(), configuration.getManagerAuthority(), true));
    install(new ConnectorResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new TemplateResourceClientModule(configuration.getTemplateServiceClientConfig(),
        configuration.getTemplateServiceSecret(), PIPELINE_SERVICE.toString()));
    install(
        new CiServiceResourceClientModule(configuration.getCiServiceClientConfig(), configuration.getCiServiceSecret(),
            PIPELINE_SERVICE.toString(), configuration.isContainerStepConfigureWithCi()));
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
    install(new NGSettingsClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new DelegateSelectionLogHttpClientModule(configuration.getManagerClientConfig(),
        configuration.getManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new PipelineServiceEventsFrameworkModule(configuration.getEventsFrameworkConfiguration(),
        configuration.getPipelineRedisEventsConfig(), configuration.getDebeziumConsumersConfigs(),
        configuration.getEventsFrameworkSnapshotConfiguration(),
        configuration.isShouldUseEventsFrameworkSnapshotDebezium()));
    install(new EntitySetupUsageClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new LogStreamingModule(configuration.getLogStreamingServiceConfig().getBaseUrl()));
    install(new OpaClientModule(
        configuration.getOpaClientConfig(), configuration.getPolicyManagerSecret(), PIPELINE_SERVICE.getServiceId()));
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
    install(new HsqsServiceClientModule(this.configuration.getQueueServiceClientConfig(), BEARER.getServiceId()));
    install(new SSCAServiceClientModuleV2(this.configuration.getSscaServiceConfig(), PIPELINE_SERVICE.getServiceId()));

    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(PMSOutboxEventHandler.class);
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(PipelineMetadataService.class).to(PipelineMetadataServiceImpl.class);

    bind(PMSPipelineService.class).to(PMSPipelineServiceImpl.class);
    bind(PipelineAsyncValidationService.class).to(PipelineAsyncValidationServiceImpl.class);
    bind(PmsExecutionSummaryService.class).to(PmsExecutionSummaryServiceImpl.class);
    bind(PipelineGovernanceService.class).to(PipelineGovernanceServiceImpl.class);
    bind(PipelineValidationService.class).to(PipelineValidationServiceImpl.class);

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
    bind(PipelineStageYamlSchemaService.class).to(PipelineStageYamlSchemaServiceImpl.class).in(Singleton.class);
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
    bind(TriggerWebhookEventExecutionService.class).to(TriggerWebhookEventExecutionServiceImpl.class);
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
    bind(PipelineResource.class).to(PipelineResourceImpl.class);
    bind(PipelinesApi.class).to(PipelinesApiImpl.class);
    bind(ApprovalsApi.class).to(ApprovalsApiImpl.class);
    bind(InputSetsApi.class).to(InputSetsApiImpl.class);
    bind(PipelineDashboardOverviewResource.class).to(PipelineDashboardOverviewResourceImpl.class);
    bind(PipelineDashboardOverviewResourceV2.class).to(PipelineDashboardOverviewResourceV2Impl.class);
    bind(PMSLandingDashboardResource.class).to(PMSLandingDashboardResourceImpl.class);
    bind(ApprovalResource.class).to(ApprovalResourceImpl.class);
    bind(PMSBarrierResource.class).to(PMSBarrierResourceImpl.class);
    bind(HealthResource.class).to(HealthResourceImpl.class);
    bind(CustomApprovalHelperService.class).to(CustomApprovalHelperServiceImpl.class);
    bind(JiraApprovalHelperService.class).to(JiraApprovalHelperServiceImpl.class);
    bind(JiraStepHelperService.class).to(JiraStepHelperServiceImpl.class);
    bind(PMSResourceConstraintService.class).to(PMSResourceConstraintServiceImpl.class);
    bind(PMSLandingDashboardService.class).to(PMSLandingDashboardServiceImpl.class);
    bind(InputSetResourcePMS.class).to(InputSetResourcePMSImpl.class);
    bind(InputsApi.class).to(InputsApiImpl.class);
    bind(PMSInputsService.class).to(PMSInputsServiceImpl.class);
    bind(PlanExecutionResource.class).to(PlanExecutionResourceImpl.class);
    bind(WaitStepResource.class).to(WaitStepResourceImpl.class);
    bind(WaitStepService.class).to(WaitStepServiceImpl.class);
    bind(PmsYamlSchemaResource.class).to(PmsYamlSchemaResourceImpl.class);
    bind(PMSResourceConstraintResource.class).to(PMSResourceConstraintResourceImpl.class);
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder()
                        .logStreamingServiceBaseUrl(configuration.getLogStreamingServiceConfig().getBaseUrl())
                        .build());

    bind(PipelineDashboardService.class).to(PipelineDashboardServiceImpl.class);
    bind(ServiceNowApprovalHelperService.class).to(ServiceNowApprovalHelperServiceImpl.class);
    bind(ServiceNowStepHelperService.class).to(ServiceNowStepHelperServiceImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(ContainerStepV2PluginProvider.class).to(ContainerStepV2PluginProviderImpl.class);
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

    // ng-license dependencies
    install(NgLicenseHttpClientModule.getInstance(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
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
        .annotatedWith(Names.named(ACCOUNT_ENTITY + ENTITY_CRUD))
        .to(AccountEntityCrudStreamListener.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.POLLING_EVENTS_STREAM))
        .to(PollingEventStreamListener.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM))
        .to(TriggerExecutionEventStreamListener.class);
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
  @Named("logStreamingClientThreadPool")
  public ThreadPoolExecutor logStreamingClientThreadPool() {
    ThreadPoolConfig threadPoolConfig = configuration != null && configuration.getLogStreamingServiceConfig() != null
            && configuration.getLogStreamingServiceConfig().getThreadPoolConfig() != null
        ? configuration.getLogStreamingServiceConfig().getThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(50).idleTime(30).timeUnit(TimeUnit.SECONDS).build();
    return ThreadPool.create(threadPoolConfig.getCorePoolSize(), threadPoolConfig.getMaxPoolSize(),
        threadPoolConfig.getIdleTime(), threadPoolConfig.getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("log-client-pool-%d").build());
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
    return RedissonClientFactory.getClient(configuration.getRedisLockConfig());
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
  @Named("DashboardExecutorService")
  public ExecutorService dashboardExecutorService() {
    return ThreadPool.create(configuration.getDashboardExecutorServiceConfig().getCorePoolSize(),
        configuration.getDashboardExecutorServiceConfig().getMaxPoolSize(),
        configuration.getDashboardExecutorServiceConfig().getIdleTime(),
        configuration.getDashboardExecutorServiceConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("DashboardExecutorService-%d").build());
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
  @Named("YamlSchemaExecutorService")
  public ExecutorService yamlSchemaExecutorService() {
    return ThreadPool.create(configuration.getYamlSchemaExecutorServiceConfig().getCorePoolSize(),
        configuration.getYamlSchemaExecutorServiceConfig().getMaxPoolSize(),
        configuration.getYamlSchemaExecutorServiceConfig().getIdleTime(),
        configuration.getYamlSchemaExecutorServiceConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("YamlSchemaService-%d").build());
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

  /**
   * To be used for async validations of Pipelines. Because pipeline fetch calls can be frequent, max pool size needs to
   * be high
   */
  @Provides
  @Singleton
  @Named("PipelineAsyncValidationExecutorService")
  public Executor pipelineAsyncValidationExecutorService() {
    return new ManagedExecutorService(
        ThreadPool.create(configuration.getPipelineAsyncValidationPoolConfig().getCorePoolSize(),
            configuration.getPipelineAsyncValidationPoolConfig().getMaxPoolSize(),
            configuration.getPipelineAsyncValidationPoolConfig().getIdleTime(),
            configuration.getPipelineAsyncValidationPoolConfig().getTimeUnit(),
            new ThreadFactoryBuilder().setNameFormat("PipelineAsyncValidationExecutorService-%d").build()));
  }

  @Provides
  @Singleton
  @Named("TriggerAuthenticationExecutorService")
  public ExecutorService triggerAuthenticationExecutorService() {
    return ThreadPool.create(configuration.getTriggerAuthenticationPoolConfig().getCorePoolSize(),
        configuration.getTriggerAuthenticationPoolConfig().getMaxPoolSize(),
        configuration.getTriggerAuthenticationPoolConfig().getIdleTime(),
        configuration.getTriggerAuthenticationPoolConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("TriggerAuthenticationExecutorService-%d").build());
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
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo(),
        true);
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
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 7)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("staticSchemaCache")
  public Cache<SchemaCacheKey, String> staticSchemaCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("staticSchemaCache", SchemaCacheKey.class, String.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 7)),
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

  @Provides
  @Singleton
  @Named("pipelineSetupUsageCreationExecutorService")
  public ExecutorService pipelineSetupUsageCreationExecutorService() {
    return new ManagedExecutorService(ThreadPool.create(configuration.getPipelineSetupUsageCreationPoolConfig(), 1,
        new ThreadFactoryBuilder().setNameFormat("PipelineSetupUsageCreationExecutorService-%d").build(),
        new ThreadPoolExecutor.AbortPolicy()));
  }
}
