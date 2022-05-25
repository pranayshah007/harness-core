/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.AuthorizationServiceHeader.MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.GLOBAL_DISABLE_HEALTH_CHECK;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.lock.mongo.MongoPersistentLocker.LOCKS_STORE;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;
import static io.harness.persistence.HPersistence.ANALYTICS_STORE_NAME;
import static io.harness.time.DurationUtils.durationTillDayTime;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.common.VerificationConstants.CV_24X7_METRIC_LABELS;
import static software.wings.common.VerificationConstants.CV_META_DATA;
import static software.wings.common.VerificationConstants.VERIFICATION_DEPLOYMENTS;
import static software.wings.common.VerificationConstants.VERIFICATION_METRIC_LABELS;

import static com.google.common.collect.ImmutableMap.of;
import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.not;
import static com.google.inject.name.Names.named;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.GraphQLModule;
import io.harness.artifact.ArtifactCollectionPTaskServiceClient;
import io.harness.cache.CacheModule;
import io.harness.capability.CapabilityModule;
import io.harness.ccm.CEPerpetualTaskHandler;
import io.harness.ccm.KubernetesClusterHandler;
import io.harness.ccm.cluster.ClusterRecordHandler;
import io.harness.ccm.cluster.ClusterRecordObserver;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.ClusterRecordServiceImpl;
import io.harness.ccm.license.CeLicenseExpiryHandler;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.commandlibrary.client.CommandLibraryServiceClientModule;
import io.harness.config.DatadogConfig;
import io.harness.config.PublisherConfiguration;
import io.harness.config.WorkersConfiguration;
import io.harness.configuration.DeployMode;
import io.harness.cvng.core.services.api.VerificationServiceSecretManager;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.delay.DelayEventListener;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.delegate.beans.StartupMode;
import io.harness.delegate.event.handler.DelegateProfileEventHandler;
import io.harness.delegate.eventstream.EntityCRUDConsumer;
import io.harness.delegate.resources.DelegateTaskResource;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.telemetry.DelegateTelemetryPublisher;
import io.harness.dms.DmsModule;
import io.harness.event.EventsModule;
import io.harness.event.listener.EventListener;
import io.harness.event.reconciliation.service.DeploymentReconExecutorService;
import io.harness.event.reconciliation.service.DeploymentReconTask;
import io.harness.event.usagemetrics.EventsModuleHelper;
import io.harness.eventframework.dms.DmsEventConsumerService;
import io.harness.eventframework.dms.DmsObserverEventProducer;
import io.harness.eventframework.manager.ManagerEventConsumerService;
import io.harness.eventframework.manager.ManagerObserverEventProducer;
import io.harness.exception.ConstraintViolationExceptionMapper;
import io.harness.exception.WingsException;
import io.harness.execution.export.background.ExportExecutionsRequestCleanupHandler;
import io.harness.execution.export.background.ExportExecutionsRequestHandler;
import io.harness.ff.FeatureFlagConfig;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.ProviderModule;
import io.harness.grpc.GrpcServiceConfigurationModule;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.insights.DelegateInsightsSummaryJob;
import io.harness.iterator.DelegateTaskExpiryCheckIterator;
import io.harness.iterator.FailDelegateTaskIterator;
import io.harness.lock.AcquiredLock;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLocker;
import io.harness.maintenance.MaintenanceController;
import io.harness.manifest.ManifestCollectionPTaskServiceClient;
import io.harness.marketplace.gcp.GcpMarketplaceSubscriberService;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.migrations.MigrationModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.QuartzCleaner;
import io.harness.mongo.QueryFactory;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.tracing.TraceMode;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.CorrelationFilter;
import io.harness.observer.NoOpRemoteObserverInformerImpl;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.consumer.AbstractRemoteObserverModule;
import io.harness.outbox.OutboxEventPollService;
import io.harness.perpetualtask.AwsAmiInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.AwsCodeDeployInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.CustomDeploymentInstanceSyncClient;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskServiceClientRegistry;
import io.harness.perpetualtask.PerpetualTaskServiceImpl;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.connector.ConnectorHeartbeatPerpetualTaskClient;
import io.harness.perpetualtask.ecs.EcsPerpetualTaskServiceClient;
import io.harness.perpetualtask.example.SamplePerpetualTaskServiceClient;
import io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.instancesync.AwsSshPerpetualTaskServiceClient;
import io.harness.perpetualtask.instancesync.AzureVMSSInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.instancesync.AzureWebAppInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.instancesync.ContainerInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.instancesync.PcfInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.instancesync.PdcPerpetualTaskServiceClient;
import io.harness.perpetualtask.instancesync.SpotinstAmiInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.internal.PerpetualTaskRecordHandler;
import io.harness.perpetualtask.k8s.watch.K8sWatchPerpetualTaskServiceClient;
import io.harness.persistence.HPersistence;
import io.harness.persistence.Store;
import io.harness.persistence.UserProvider;
import io.harness.queue.QueueListener;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.queue.TimerScheduledExecutorService;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.request.RequestContextFilter;
import io.harness.scheduler.PersistentScheduler;
import io.harness.secret.ConfigSecretUtils;
import io.harness.secrets.SecretMigrationEventListener;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.serializer.CurrentGenRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.DelegateServiceModule;
import io.harness.service.impl.DelegateInsightsServiceImpl;
import io.harness.service.impl.DelegateNgTokenServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.service.impl.DelegateTaskServiceImpl;
import io.harness.service.impl.DelegateTokenServiceImpl;
import io.harness.service.intfc.DelegateProfileObserver;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.service.intfc.DelegateTokenService;
import io.harness.service.intfc.PerpetualTaskStateObserver;
import io.harness.springdata.SpringPersistenceModule;
import io.harness.state.inspection.StateInspectionListener;
import io.harness.state.inspection.StateInspectionServiceImpl;
import io.harness.stream.AtmosphereBroadcaster;
import io.harness.stream.GuiceObjectFactory;
import io.harness.stream.StreamModule;
import io.harness.threading.ExecutorModule;
import io.harness.threading.Schedulable;
import io.harness.threading.ThreadPool;
import io.harness.timeout.TimeoutEngine;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.tracing.AbstractPersistenceTracerModule;
import io.harness.tracing.MongoRedisTracer;
import io.harness.validation.SuppressValidation;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.NotifyResponseCleaner;
import io.harness.waiter.OrchestrationNotifyEventListener;
import io.harness.waiter.ProgressUpdateService;
import io.harness.workers.background.critical.iterator.ArtifactCollectionHandler;
import io.harness.workers.background.critical.iterator.EventDeliveryHandler;
import io.harness.workers.background.critical.iterator.ResourceConstraintBackupHandler;
import io.harness.workers.background.critical.iterator.WorkflowExecutionMonitorHandler;
import io.harness.workers.background.iterator.ArtifactCleanupHandler;
import io.harness.workers.background.iterator.InstanceSyncHandler;
import io.harness.workers.background.iterator.SettingAttributeValidateConnectivityHandler;

import software.wings.app.MainConfiguration.AssetsConfigurationMixin;
import software.wings.beans.Activity;
import software.wings.beans.Log;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.alert.AlertReconciliationHandler;
import software.wings.collect.ArtifactCollectEventListener;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.exception.GenericExceptionMapper;
import software.wings.exception.JsonProcessingExceptionMapper;
import software.wings.exception.WingsExceptionMapper;
import software.wings.filter.AuditRequestFilter;
import software.wings.filter.AuditResponseFilter;
import software.wings.jersey.JsonViews;
import software.wings.jersey.KryoFeature;
import software.wings.licensing.LicenseService;
import software.wings.notification.EmailNotificationListener;
import software.wings.prune.PruneEntityListener;
import software.wings.resources.AppResource;
import software.wings.resources.SearchResource;
import software.wings.resources.graphql.GraphQLResource;
import software.wings.scheduler.AccessRequestHandler;
import software.wings.scheduler.AccountPasswordExpirationJob;
import software.wings.scheduler.DeletedEntityHandler;
import software.wings.scheduler.InstancesPurgeJob;
import software.wings.scheduler.LdapGroupScheduledHandler;
import software.wings.scheduler.ManagerVersionsCleanUpJob;
import software.wings.scheduler.ResourceLookupSyncHandler;
import software.wings.scheduler.UsageMetricsHandler;
import software.wings.scheduler.VaultSecretManagerRenewalHandler;
import software.wings.scheduler.YamlChangeSetPruneJob;
import software.wings.scheduler.account.DeleteAccountHandler;
import software.wings.scheduler.account.LicenseCheckHandler;
import software.wings.scheduler.approval.ApprovalPollingHandler;
import software.wings.scheduler.audit.EntityAuditRecordHandler;
import software.wings.scheduler.events.segment.SegmentGroupEventJob;
import software.wings.scheduler.marketplace.gcp.GCPBillingHandler;
import software.wings.scheduler.persistance.PersistentLockCleanup;
import software.wings.search.framework.ElasticsearchSyncService;
import software.wings.security.AuthResponseFilter;
import software.wings.security.AuthRuleFilter;
import software.wings.security.AuthenticationFilter;
import software.wings.security.LoginRateLimitFilter;
import software.wings.security.ThreadLocalUserProvider;
import software.wings.security.authentication.totp.TotpModule;
import software.wings.security.encryption.migration.EncryptedDataLocalToGcpKmsMigrationHandler;
import software.wings.security.encryption.migration.SettingAttributesSecretsMigrationHandler;
import software.wings.service.impl.AccountServiceImpl;
import software.wings.service.impl.AppManifestCloudProviderPTaskManager;
import software.wings.service.impl.ApplicationManifestServiceImpl;
import software.wings.service.impl.ArtifactStreamServiceImpl;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.AuditServiceImpl;
import software.wings.service.impl.BarrierServiceImpl;
import software.wings.service.impl.CloudProviderObserver;
import software.wings.service.impl.DelegateDisconnectedDetector;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateProfileServiceImpl;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.DelegateTaskStatusObserver;
import software.wings.service.impl.ExecutionEventListener;
import software.wings.service.impl.InfrastructureMappingServiceImpl;
import software.wings.service.impl.SettingAttributeObserver;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.impl.applicationmanifest.AppManifestSettingAttributePTaskManager;
import software.wings.service.impl.applicationmanifest.ManifestPerpetualTaskManger;
import software.wings.service.impl.artifact.ArtifactStreamPTaskManager;
import software.wings.service.impl.artifact.ArtifactStreamPTaskMigrationJob;
import software.wings.service.impl.artifact.ArtifactStreamSettingAttributePTaskManager;
import software.wings.service.impl.compliance.DeploymentFreezeActivationHandler;
import software.wings.service.impl.compliance.DeploymentFreezeDeactivationHandler;
import software.wings.service.impl.event.DeploymentTimeSeriesEventListener;
import software.wings.service.impl.infrastructuredefinition.InfrastructureDefinitionServiceImpl;
import software.wings.service.impl.instance.DeploymentEventListener;
import software.wings.service.impl.instance.InstanceEventListener;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskMigrationJob;
import software.wings.service.impl.trigger.ScheduledTriggerHandler;
import software.wings.service.impl.workflow.WorkflowServiceImpl;
import software.wings.service.impl.yaml.YamlPushServiceImpl;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureDefinitionServiceObserver;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureMappingServiceObserver;
import software.wings.service.intfc.MigrationService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.account.AccountCrudObserver;
import software.wings.service.intfc.applicationmanifest.ApplicationManifestServiceObserver;
import software.wings.service.intfc.artifact.ArtifactStreamServiceObserver;
import software.wings.service.intfc.entitycrud.EntityCrudOperationObserver;
import software.wings.service.intfc.manipulation.SettingsServiceManipulationObserver;
import software.wings.service.intfc.perpetualtask.PerpetualTaskCrudObserver;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateMachineExecutor;
import software.wings.sm.StateStatusUpdate;
import software.wings.yaml.gitSync.GitChangeSetRunnable;
import software.wings.yaml.gitSync.GitSyncEntitiesExpiryHandler;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.bundles.assets.AssetsConfiguration;
import io.dropwizard.bundles.assets.ConfiguredAssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletRegistration;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.MetaBroadcaster;
import org.coursera.metrics.datadog.DatadogReporter;
import org.coursera.metrics.datadog.transport.HttpTransport;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;
import ru.vyarus.guice.validator.aop.ValidationMethodInterceptor;

/**
 * The main application - entry point for the entire Wings Application.
 */
@Slf4j
@OwnedBy(PL)
public class WingsApplication extends Application<MainConfiguration> {
  private static final SecureRandom random = new SecureRandom();

  private final MetricRegistry metricRegistry = new MetricRegistry();
  private HarnessMetricRegistry harnessMetricRegistry;
  private StartupMode startupMode;

  public WingsApplication(StartupMode startupMode) {
    this.startupMode = startupMode;
  }
  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new WingsApplication(StartupMode.MANAGER).run(args);
  }

  @Override
  public String getName() {
    return "Wings Application";
  }

  @Override
  public void initialize(Bootstrap<MainConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());

    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new ConfiguredAssetsBundle("/static", "/", "index.html"));
    bootstrap.addBundle(new SwaggerBundle<MainConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(MainConfiguration mainConfiguration) {
        return mainConfiguration.getSwaggerBundleConfiguration();
      }
    });
    bootstrap.addBundle(new FileAssetsBundle("/.well-known"));
    configureObjectMapper(bootstrap.getObjectMapper());
    bootstrap.setMetricRegistry(metricRegistry);

    log.info("bootstrapping done.");
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.addMixIn(AssetsConfiguration.class, AssetsConfigurationMixin.class);
    final AnnotationAwareJsonSubtypeResolver subtypeResolver =
        AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver());
    mapper.setSubtypeResolver(subtypeResolver);
    mapper.setConfig(mapper.getSerializationConfig().withView(JsonViews.Public.class));
    mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
      // defining a different serialVersionUID then base. We don't care about serializing it.
      private static final long serialVersionUID = 7777451630128399020L;
      @Override
      public List<NamedType> findSubtypes(Annotated a) {
        final List<NamedType> subtypesFromSuper = super.findSubtypes(a);
        if (isNotEmpty(subtypesFromSuper)) {
          return subtypesFromSuper;
        }
        return emptyIfNull(subtypeResolver.findSubtypes(a));
      }
    });
  }

  @Override
  public void run(final MainConfiguration configuration, Environment environment) throws Exception {
    log.info("Starting app ...");
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    ConfigSecretUtils.resolveSecrets(configuration.getSecretsConfiguration(), configuration);

    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        configuration.getCommonPoolConfig().getCorePoolSize(), configuration.getCommonPoolConfig().getMaxPoolSize(),
        configuration.getCommonPoolConfig().getIdleTime(), configuration.getCommonPoolConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    addModules(configuration, modules);
    Injector injector = Guice.createInjector(modules);

    initializeManagerSvc(injector, environment, configuration);
    log.info("Starting app done");
    log.info("Manager is running on JRE: {}", System.getProperty("java.version"));
  }

  private void registerRemoteObserverModule(MainConfiguration configuration, List<Module> modules) {
    if (shouldEnableRemoteObservers(configuration)) {
      modules.add(new AbstractRemoteObserverModule() {
        @Override
        public boolean noOpProducer() {
          return false;
        }

        @Override
        public Set<RemoteObserver> observers() {
          Set<RemoteObserver> remoteObservers = new HashSet<>();
          if (isManager()) {
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(YamlPushServiceImpl.class)
                                    .observerClass(EntityCrudOperationObserver.class)
                                    .observer(AuditServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(AuditServiceHelper.class)
                                    .observerClass(EntityCrudOperationObserver.class)
                                    .observer(AuditServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(SettingsServiceImpl.class)
                                    .observerClass(CloudProviderObserver.class)
                                    .observer(ClusterRecordHandler.class)
                                    .observer(AppManifestCloudProviderPTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(SettingsServiceImpl.class)
                                    .observerClass(SettingAttributeObserver.class)
                                    .observer(ArtifactStreamSettingAttributePTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(SettingsServiceImpl.class)
                                    .observerClass(SettingAttributeObserver.class)
                                    .observer(AppManifestSettingAttributePTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(InfrastructureDefinitionServiceImpl.class)
                                    .observerClass(InfrastructureDefinitionServiceObserver.class)
                                    .observer(ClusterRecordHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(InfrastructureMappingServiceImpl.class)
                                    .observerClass(InfrastructureMappingServiceObserver.class)
                                    .observer(ClusterRecordHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ClusterRecordServiceImpl.class)
                                    .observerClass(ClusterRecordObserver.class)
                                    .observer(CEPerpetualTaskHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ArtifactStreamServiceImpl.class)
                                    .observerClass(ArtifactStreamServiceObserver.class)
                                    .observer(ArtifactStreamPTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ArtifactStreamServiceImpl.class)
                                    .observerClass(ArtifactStreamServiceObserver.class)
                                    .observer(ArtifactStreamPTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(AccountServiceImpl.class)
                                    .observerClass(AccountCrudObserver.class)
                                    .observer(DelegateProfileServiceImpl.class)
                                    .observer(CEPerpetualTaskHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ApplicationManifestServiceImpl.class)
                                    .observerClass(ApplicationManifestServiceObserver.class)
                                    .observer(ManifestPerpetualTaskManger.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(SettingsServiceImpl.class)
                                    .observerClass(SettingsServiceManipulationObserver.class)
                                    .observer(WorkflowServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(StateMachineExecutor.class)
                                    .observerClass(StateStatusUpdate.class)
                                    .observer(WorkflowExecutionServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(StateInspectionServiceImpl.class)
                                    .observerClass(StateInspectionListener.class)
                                    .observer(StateMachineExecutor.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateServiceImpl.class)
                                    .observerClass(DelegateObserver.class)
                                    .observer(KubernetesClusterHandler.class)
                                    .build());
          }

          if (isDms()) {
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateServiceImpl.class)
                                    .observerClass(DelegateTaskStatusObserver.class)
                                    .observer(DelegateInsightsServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateTaskServiceImpl.class)
                                    .observerClass(DelegateTaskStatusObserver.class)
                                    .observer(DelegateInsightsServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateServiceImpl.class)
                                    .observerClass(DelegateProfileObserver.class)
                                    .observer(DelegateProfileEventHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateProfileServiceImpl.class)
                                    .observerClass(DelegateProfileObserver.class)
                                    .observer(DelegateProfileEventHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(PerpetualTaskServiceImpl.class)
                                    .observerClass(PerpetualTaskCrudObserver.class)
                                    .observer(PerpetualTaskRecordHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(PerpetualTaskServiceImpl.class)
                                    .observerClass(PerpetualTaskStateObserver.class)
                                    .observer(DelegateInsightsServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(PerpetualTaskServiceImpl.class)
                                    .observerClass(PerpetualTaskStateObserver.class)
                                    .observer(DelegateInsightsServiceImpl.class)
                                    .build());
            remoteObservers.add(
                RemoteObserver.builder()
                    .subjectCLass(AccountServiceImpl.class)
                    .observerClass(AccountCrudObserver.class)
                    .observers(Arrays.asList(DelegateTokenServiceImpl.class, DelegateNgTokenServiceImpl.class))
                    .build());
          }
          return remoteObservers;
        }

        @Override
        public Class<? extends RemoteObserverInformer> getRemoteObserverImpl() {
          if (isManager()) {
            return ManagerObserverEventProducer.class;
          }
          return DmsObserverEventProducer.class;
        }
      });
    } else {
      modules.add(new AbstractRemoteObserverModule() {
        @Override
        public boolean noOpProducer() {
          return true;
        }

        @Override
        public Set<RemoteObserver> observers() {
          return Collections.emptySet();
        }

        @Override
        public Class<? extends RemoteObserverInformer> getRemoteObserverImpl() {
          return NoOpRemoteObserverInformerImpl.class;
        }
      });
    }
  }

  public void initializeManagerSvc(Injector injector, Environment environment, MainConfiguration configuration) {
    // Access all caches before coming out of maintenance
    injector.getInstance(new Key<Map<String, Cache<?, ?>>>() {});

    boolean shouldEnableDelegateMgmt = shouldEnableDelegateMgmt(configuration);
    if (shouldEnableDelegateMgmt) {
      registerAtmosphereStreams(environment, injector);
    }

    initializeFeatureFlags(configuration, injector);

    if (isManager()) {
      registerHealthChecksManager(environment, injector);
    }
    if (shouldEnableDelegateMgmt) {
      registerHealthChecksDelegateService(environment, injector);
    }

    registerStores(configuration, injector);
    registerDataStores(injector);
    if (configuration.getMongoConnectionFactory().getTraceMode() == TraceMode.ENABLED) {
      registerQueryTracer(injector);
    }

    registerResources(configuration, environment, injector);

    // Managed beans
    registerManagedBeansCommon(configuration, environment, injector);
    if (isManager()) {
      registerManagedBeansManager(configuration, environment, injector);
    }

    registerWaitEnginePublishers(injector);
    if (isManager()) {
      registerQueueListeners(configuration, injector);
    }

    // Schedule jobs
    ScheduledExecutorService delegateExecutor =
        injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("delegatePool")));
    if (isManager()) {
      scheduleJobsManager(injector, configuration, delegateExecutor);
    }
    if (shouldEnableDelegateMgmt) {
      scheduleJobsDelegateService(injector, configuration, delegateExecutor);
    }

    registerEventConsumers(injector);

    registerObservers(configuration, injector, environment);

    if (shouldEnableDelegateMgmt) {
      registerInprocPerpetualTaskServiceClients(injector);
    }

    if (isManager()) {
      registerCronJobs(injector);
    }

    // common for both manager and dms
    registerCorsFilter(configuration, environment);
    registerAuditResponseFilter(environment, injector);
    registerJerseyProviders(environment, injector);
    registerCharsetResponseFilter(environment, injector);
    // Authentication/Authorization filters
    registerAuthFilters(configuration, environment, injector);
    registerCorrelationFilter(environment, injector);
    registerRequestContextFilter(environment);
    // Register collection iterators
    if (configuration.isEnableIterators()) {
      if (isManager()) {
        registerIteratorsManager(configuration.getIteratorsConfig(), injector);
      }
      if (shouldEnableDelegateMgmt) {
        registerIteratorsDelegateService(configuration.getIteratorsConfig(), injector);
      }
    }

    environment.lifecycle().addServerLifecycleListener(server -> {
      for (Connector connector : server.getConnectors()) {
        if (connector instanceof ServerConnector) {
          ServerConnector serverConnector = (ServerConnector) connector;
          if (serverConnector.getName().equalsIgnoreCase("application")) {
            configuration.setSslEnabled(
                serverConnector.getDefaultConnectionFactory().getProtocol().equalsIgnoreCase("ssl"));
            configuration.setApplicationPort(serverConnector.getLocalPort());
            return;
          }
        }
      }
    });

    if (isManager()) {
      harnessMetricRegistry = injector.getInstance(HarnessMetricRegistry.class);
      initMetrics();
      initializeServiceSecretKeys(injector);
      runMigrations(injector);
    }

    String deployMode = configuration.getDeployMode().name();

    if (DeployMode.isOnPrem(deployMode)) {
      LicenseService licenseService = injector.getInstance(LicenseService.class);
      String encryptedLicenseInfoBase64String = System.getenv(LicenseService.LICENSE_INFO);
      log.info("Encrypted license info read from environment {}", encryptedLicenseInfoBase64String);
      if (isEmpty(encryptedLicenseInfoBase64String)) {
        log.error("No license info is provided");
      } else {
        try {
          log.info("Updating license info read from environment {}", encryptedLicenseInfoBase64String);
          licenseService.updateAccountLicenseForOnPrem(encryptedLicenseInfoBase64String);
          log.info("Updated license info read from environment {}", encryptedLicenseInfoBase64String);
        } catch (WingsException ex) {
          log.error("Error while updating license info", ex);
        }
      }
    }

    injector.getInstance(EventsModuleHelper.class).initialize();
    registerDatadogPublisherIfEnabled(configuration);

    initializeGrpcServer(injector);

    initMetrics(injector);

    log.info("Leaving startup maintenance mode");
    MaintenanceController.resetForceMaintenance();
  }

  private void initializeGrpcServer(Injector injector) {
    log.info("Initializing gRPC server...");
    ServiceManager serviceManager = injector.getInstance(ServiceManager.class).startAsync();
    serviceManager.awaitHealthy();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceManager.stopAsync().awaitStopped()));
  }

  private void registerQueryTracer(Injector injector) {
    AdvancedDatastore datastore = injector.getInstance(Key.get(AdvancedDatastore.class, named("primaryDatastore")));
    MongoRedisTracer tracer = injector.getInstance(MongoRedisTracer.class);
    ((QueryFactory) datastore.getQueryFactory()).getTracerSubject().register(tracer);
  }

  public boolean isManager() {
    return startupMode.equals(StartupMode.MANAGER);
  }

  public boolean shouldEnableDelegateMgmt(final MainConfiguration configuration) {
    return isDms() || !configuration.isDisableDelegateMgmtInManager();
  }

  public boolean isDms() {
    return startupMode.equals(StartupMode.DELEGATE_SERVICE);
  }

  private boolean shouldEnableRemoteObservers(final MainConfiguration configuration) {
    return isDms() || configuration.isDisableDelegateMgmtInManager();
  }

  public void addModules(final MainConfiguration configuration, List<Module> modules) {
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(CurrentGenRegistrars.kryoRegistrars)
            .build();
      }
      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CurrentGenRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(CurrentGenRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(CurrentGenRegistrars.springConverters)
            .build();
      }
    });

    modules.add(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new ThreadLocalUserProvider();
      }
    });

    modules.add(new SpringPersistenceModule());

    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    CacheModule cacheModule = new CacheModule(configuration.getCacheConfig());
    modules.add(cacheModule);
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      AtmosphereBroadcaster atmosphereBroadcaster() {
        return configuration.getAtmosphereBroadcaster();
      }
    });
    modules.add(StreamModule.getInstance());

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetricRegistry.class).toInstance(metricRegistry);
      }
    });

    modules.add(MetricsInstrumentationModule.builder()
                    .withMetricRegistry(metricRegistry)
                    .withMatcher(not(new AbstractMatcher<TypeLiteral<?>>() {
                      @Override
                      public boolean matches(TypeLiteral<?> typeLiteral) {
                        return typeLiteral.getRawType().isAnnotationPresent(Path.class);
                      }
                    }))
                    .build());
    modules.add(new ValidationModule(validatorFactory) {
      @Override
      protected void configureAop(ValidationMethodInterceptor interceptor) {
        bindInterceptor(not(annotatedWith(SuppressValidation.class)),
            annotatedWith(ValidateOnExecution.class).and(not(annotatedWith(SuppressValidation.class))), interceptor);
        bindInterceptor(annotatedWith(ValidateOnExecution.class).and(not(annotatedWith(SuppressValidation.class))),
            not(annotatedWith(SuppressValidation.class)), interceptor);
      }
    });
    modules.add(new DelegateServiceModule(configuration.getDelegateMtlsSubdomain()));
    modules.add(new CapabilityModule());
    modules.add(MigrationModule.getInstance());
    registerRemoteObserverModule(configuration, modules);
    modules.add(new WingsModule(configuration, startupMode));
    modules.add(new TotpModule());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "delegateTaskProgressResponses")
            .build();
      }
    });

    modules.add(new IndexMigratorModule());
    modules.add(new YamlModule());
    modules.add(new ManagerQueueModule());

    modules.add(new ManagerExecutorModule());
    modules.add(new TemplateModule());
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(new EventsModule(configuration));
    if (configuration.isGraphQLEnabled()) {
      modules.add(GraphQLModule.getInstance());
    }
    modules.add(new SSOModule());
    modules.add(new SignupModule());
    modules.add(new AuthModule());
    modules.add(new GcpMarketplaceIntegrationModule());
    if (configuration.isSearchEnabled()) {
      modules.add(new SearchModule());
    }
    modules.add(new ProviderModule() {
      @Provides
      public GrpcServerConfig getGrpcServerConfig() {
        return configuration.getGrpcServerConfig();
      }
    });
    modules.add(new GrpcServiceConfigurationModule(
        configuration.getGrpcServerConfig(), configuration.getPortal().getJwtNextGenManagerSecret()));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      WorkersConfiguration workersConfig() {
        return configuration.getWorkers();
      }

      @Provides
      @Singleton
      PublisherConfiguration publisherConfiguration() {
        return configuration.getPublisherConfiguration();
      }
    });

    modules.add(new CommandLibraryServiceClientModule(configuration.getCommandLibraryServiceConfig()));
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return configuration.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return configuration.getCfMigrationConfig();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return configuration.getFeatureFlagConfig();
      }
    });

    modules.add(new AbstractPersistenceTracerModule() {
      @Override
      protected RedisConfig redisConfigProvider() {
        return configuration.getEventsFrameworkConfiguration().getRedisConfig();
      }

      @Override
      protected String serviceIdProvider() {
        return MANAGER.getServiceId();
      }
    });
    modules.add(DmsModule.getInstance(shouldEnableDelegateMgmt(configuration)));
  }

  private void registerEventConsumers(final Injector injector) {
    final ExecutorService entityCRUDConsumerExecutor =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ENTITY_CRUD).build());
    entityCRUDConsumerExecutor.execute(injector.getInstance(EntityCRUDConsumer.class));
  }

  private void registerAtmosphereStreams(Environment environment, Injector injector) {
    AtmosphereServlet atmosphereServlet = injector.getInstance(AtmosphereServlet.class);
    atmosphereServlet.framework().objectFactory(new GuiceObjectFactory(injector));
    injector.getInstance(BroadcasterFactory.class);
    injector.getInstance(MetaBroadcaster.class);
    ServletRegistration.Dynamic dynamic = environment.servlets().addServlet("StreamServlet", atmosphereServlet);
    dynamic.setAsyncSupported(true);
    dynamic.setLoadOnStartup(0);
    dynamic.addMapping("/stream/*");
  }

  private void registerInprocPerpetualTaskServiceClients(Injector injector) {
    // will move to dms in the future, keep it in manager for now
    PerpetualTaskServiceClientRegistry clientRegistry =
        injector.getInstance(Key.get(PerpetualTaskServiceClientRegistry.class));

    clientRegistry.registerClient(
        PerpetualTaskType.K8S_WATCH, injector.getInstance(K8sWatchPerpetualTaskServiceClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.ECS_CLUSTER, injector.getInstance(EcsPerpetualTaskServiceClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.SAMPLE, injector.getInstance(SamplePerpetualTaskServiceClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.ARTIFACT_COLLECTION, injector.getInstance(ArtifactCollectionPTaskServiceClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.PCF_INSTANCE_SYNC, injector.getInstance(PcfInstanceSyncPerpetualTaskClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.AWS_SSH_INSTANCE_SYNC, injector.getInstance(AwsSshPerpetualTaskServiceClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.PDC_INSTANCE_SYNC, injector.getInstance(PdcPerpetualTaskServiceClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.AWS_AMI_INSTANCE_SYNC, injector.getInstance(AwsAmiInstanceSyncPerpetualTaskClient.class));
    clientRegistry.registerClient(PerpetualTaskType.AWS_CODE_DEPLOY_INSTANCE_SYNC,
        injector.getInstance(AwsCodeDeployInstanceSyncPerpetualTaskClient.class));
    clientRegistry.registerClient(PerpetualTaskType.SPOT_INST_AMI_INSTANCE_SYNC,
        injector.getInstance(SpotinstAmiInstanceSyncPerpetualTaskClient.class));
    clientRegistry.registerClient(PerpetualTaskType.AZURE_VMSS_INSTANCE_SYNC,
        injector.getInstance(AzureVMSSInstanceSyncPerpetualTaskClient.class));
    clientRegistry.registerClient(PerpetualTaskType.CONTAINER_INSTANCE_SYNC,
        injector.getInstance(ContainerInstanceSyncPerpetualTaskClient.class));
    clientRegistry.registerClient(PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC,
        injector.getInstance(AwsLambdaInstanceSyncPerpetualTaskClient.class));
    clientRegistry.registerClient(PerpetualTaskType.CUSTOM_DEPLOYMENT_INSTANCE_SYNC,
        injector.getInstance(CustomDeploymentInstanceSyncClient.class));
    clientRegistry.registerClient(
        PerpetualTaskType.MANIFEST_COLLECTION, injector.getInstance(ManifestCollectionPTaskServiceClient.class));
    try {
      clientRegistry.registerClient(PerpetualTaskType.CONNECTOR_TEST_CONNECTION,
          injector.getInstance(ConnectorHeartbeatPerpetualTaskClient.class));
    } catch (Exception ex) {
      log.info("Could not create the connector task client", ex);
    }
    clientRegistry.registerClient(PerpetualTaskType.AZURE_WEB_APP_INSTANCE_SYNC,
        injector.getInstance(AzureWebAppInstanceSyncPerpetualTaskClient.class));
  }

  private void registerDatadogPublisherIfEnabled(MainConfiguration configuration) {
    DatadogConfig datadogConfig = configuration.getDatadogConfig();
    if (datadogConfig != null && datadogConfig.isEnabled()) {
      try {
        log.info("Registering datadog javaagent");
        HttpTransport httpTransport = new HttpTransport.Builder().withApiKey(datadogConfig.getApiKey()).build();
        DatadogReporter reporter = DatadogReporter.forRegistry(harnessMetricRegistry.getThreadPoolMetricRegistry())
                                       .withTransport(httpTransport)
                                       .build();

        reporter.start(60, TimeUnit.SECONDS);
        log.info("Registered datadog javaagent");
      } catch (Exception t) {
        log.error("Error while initializing datadog", t);
      }
    }
  }

  private void initMetrics() {
    harnessMetricRegistry.registerCounterMetric(
        VERIFICATION_DEPLOYMENTS, VERIFICATION_METRIC_LABELS.toArray(new String[0]), " ");
    harnessMetricRegistry.registerGaugeMetric(CV_META_DATA, CV_24X7_METRIC_LABELS, " ");
  }

  private void initializeFeatureFlags(MainConfiguration mainConfiguration, Injector injector) {
    injector.getInstance(FeatureFlagService.class)
        .initializeFeatureFlags(mainConfiguration.getDeployMode(), mainConfiguration.getFeatureNames());

    // Required to Publish Feature Flag Events to Events Framework
    if (DeployMode.isOnPrem(mainConfiguration.getDeployMode().name())) {
      injector.getInstance(AccountService.class).updateFeatureFlagsForOnPremAccount();
    }
  }

  private void registerHealthChecksManager(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("WingsApp", healthService);

    if (!injector.getInstance(FeatureFlagService.class).isGlobalEnabled(GLOBAL_DISABLE_HEALTH_CHECK)) {
      healthService.registerMonitor(injector.getInstance(HPersistence.class));
      healthService.registerMonitor((HealthMonitor) injector.getInstance(PersistentLocker.class));
      TimeScaleDBService timeScaleDBService = injector.getInstance(TimeScaleDBService.class);
      if (timeScaleDBService.getTimeScaleDBConfig().isHealthCheckNeeded()) {
        healthService.registerMonitor(injector.getInstance(TimeScaleDBService.class));
      }
    }
  }

  private void registerHealthChecksDelegateService(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("DelegateMgmtService", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
    healthService.registerMonitor((HealthMonitor) injector.getInstance(PersistentLocker.class));
  }

  private void registerStores(MainConfiguration configuration, Injector injector) {
    final HPersistence persistence = injector.getInstance(HPersistence.class);
    if (configuration.getDistributedLockImplementation() == DistributedLockImplementation.MONGO
        && isNotEmpty(configuration.getMongoConnectionFactory().getLocksUri())
        && !configuration.getMongoConnectionFactory().getLocksUri().equals(
            configuration.getMongoConnectionFactory().getUri())) {
      persistence.register(LOCKS_STORE, configuration.getMongoConnectionFactory().getLocksUri());
    }
    if (isNotEmpty(configuration.getEventsMongo().getUri())
        && !configuration.getEventsMongo().getUri().equals(configuration.getMongoConnectionFactory().getUri())) {
      persistence.register(Store.builder().name("events").build(), configuration.getEventsMongo().getUri());
    }
  }

  private void registerDataStores(Injector injector) {
    AdvancedDatastore analyticsDataStore =
        injector.getInstance(Key.get(AdvancedDatastore.class, Names.named("analyticsDatabase")));
    final HPersistence persistence = injector.getInstance(HPersistence.class);
    persistence.registerDatastore(ANALYTICS_STORE_NAME, analyticsDataStore);
  }

  private void registerAuditResponseFilter(Environment environment, Injector injector) {
    environment.servlets()
        .addFilter("AuditResponseFilter", injector.getInstance(AuditResponseFilter.class))
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
    environment.jersey().register(injector.getInstance(AuditRequestFilter.class));
  }

  private void registerCorsFilter(MainConfiguration configuration, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = configuration.getPortal().getUrl();
    if (!configuration.getPortal().getAllowedOrigins().isEmpty()) {
      allowedOrigins = configuration.getPortal().getAllowedOrigins();
    }
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerResources(MainConfiguration configuration, Environment environment, Injector injector) {
    Set<Class<?>> resourceClasses =
        HarnessReflections.get()
            .getTypesAnnotatedWith(Path.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(klazz.getPackage().getName(), AppResource.class.getPackage().getName(),
                    DelegateTaskResource.class.getPackage().getName()))
            .collect(Collectors.toSet());

    if (!configuration.isGraphQLEnabled()) {
      resourceClasses.remove(GraphQLResource.class);
    }
    if (!configuration.isSearchEnabled()) {
      resourceClasses.remove(SearchResource.class);
    }
    for (Class<?> resource : resourceClasses) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    if (configuration.isDisableResourceValidation()) {
      environment.jersey().property(
          ServerProperties.RESOURCE_VALIDATION_DISABLE, configuration.isDisableResourceValidation());
    }
  }

  private void registerManagedBeansCommon(MainConfiguration configuration, Environment environment, Injector injector) {
    environment.lifecycle().manage((Managed) injector.getInstance(WingsPersistence.class));
    environment.lifecycle().manage((Managed) injector.getInstance(PersistentLocker.class));
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
    environment.lifecycle().manage(injector.getInstance(TimerScheduledExecutorService.class));
    environment.lifecycle().manage(injector.getInstance(NotifierScheduledExecutorService.class));
    environment.lifecycle().manage((Managed) injector.getInstance(ExecutorService.class));
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));
  }

  private void registerManagedBeansManager(
      MainConfiguration configuration, Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(ConfigurationController.class));
    environment.lifecycle().manage(injector.getInstance(GcpMarketplaceSubscriberService.class));
    // Perpetual task
    environment.lifecycle().manage(injector.getInstance(ArtifactStreamPTaskMigrationJob.class));
    environment.lifecycle().manage(injector.getInstance(InstanceSyncPerpetualTaskMigrationJob.class));

    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));

    if (configuration.isSearchEnabled()) {
      environment.lifecycle().manage(injector.getInstance(ElasticsearchSyncService.class));
    }
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final QueuePublisher<NotifyEvent> publisher =
        injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(GENERAL, payload -> publisher.send(asList(GENERAL), payload));
    notifyQueuePublisherRegister.register(ORCHESTRATION, payload -> publisher.send(asList(ORCHESTRATION), payload));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerQueueListeners(MainConfiguration configuration, Injector injector) {
    log.info("Initializing queue listeners...");

    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    EventListener genericEventListener =
        injector.getInstance(Key.get(EventListener.class, Names.named("GenericEventListener")));
    queueListenerController.register((QueueListener) genericEventListener, 1);

    queueListenerController.register(injector.getInstance(ArtifactCollectEventListener.class), 1);
    queueListenerController.register(injector.getInstance(DelayEventListener.class), 1);
    queueListenerController.register(injector.getInstance(DeploymentEventListener.class),
        configuration.getEventListenersCountConfig().getDeploymentEventListenerCount());
    queueListenerController.register(injector.getInstance(InstanceEventListener.class),
        configuration.getEventListenersCountConfig().getInstanceEventListenerCount());
    queueListenerController.register(injector.getInstance(DeploymentTimeSeriesEventListener.class),
        configuration.getEventListenersCountConfig().getDeploymentTimeSeriesEventListenerCount());
    queueListenerController.register(injector.getInstance(EmailNotificationListener.class), 1);
    queueListenerController.register(injector.getInstance(ExecutionEventListener.class),
        configuration.getEventListenersCountConfig().getExecutionEventListenerCount());
    queueListenerController.register(injector.getInstance(SecretMigrationEventListener.class), 1);
    queueListenerController.register(injector.getInstance(GeneralNotifyEventListener.class),
        configuration.getEventListenersCountConfig().getGeneralNotifyEventListenerCount());
    queueListenerController.register(injector.getInstance(OrchestrationNotifyEventListener.class),
        configuration.getEventListenersCountConfig().getOrchestrationNotifyEventListenerCount());
    queueListenerController.register(injector.getInstance(PruneEntityListener.class), 1);
  }

  private void scheduleJobsManager(
      Injector injector, MainConfiguration configuration, ScheduledExecutorService delegateExecutor) {
    log.info("Initializing scheduled jobs...");
    injector.getInstance(NotifierScheduledExecutorService.class)
        .scheduleWithFixedDelay(
            injector.getInstance(NotifyResponseCleaner.class), random.nextInt(300), 300L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("gitChangeSet")))
        .scheduleWithFixedDelay(
            injector.getInstance(GitChangeSetRunnable.class), random.nextInt(4), 4L, TimeUnit.SECONDS);

    injector.getInstance(DeploymentReconExecutorService.class)
        .scheduleWithFixedDelay(
            injector.getInstance(DeploymentReconTask.class), random.nextInt(60), 15 * 60L, TimeUnit.SECONDS);
    ImmutableList<Class<? extends AccountDataRetentionEntity>> classes =
        ImmutableList.<Class<? extends AccountDataRetentionEntity>>builder()
            .add(WorkflowExecution.class)
            .add(StateExecutionInstance.class)
            .add(Activity.class)
            .add(Log.class)
            .build();
    ScheduledExecutorService taskPollExecutor =
        injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")));
    if (configuration.getDistributedLockImplementation() == DistributedLockImplementation.MONGO) {
      taskPollExecutor.scheduleWithFixedDelay(
          injector.getInstance(PersistentLockCleanup.class), random.nextInt(60), 60L, TimeUnit.MINUTES);
    }

    taskPollExecutor.scheduleWithFixedDelay(
        new Schedulable("Failed ensure data retention",
            () -> { injector.getInstance(AccountService.class).ensureDataRetention(classes); }),
        durationTillDayTime(System.currentTimeMillis(), ofHours(10)).toMillis(), ofHours(24).toMillis(),
        TimeUnit.MILLISECONDS);

    taskPollExecutor.scheduleAtFixedRate(new Schedulable("Failed ensure data retention", () -> {
      Map<String, Long> dataRetentionMap = injector.getInstance(AccountService.class).obtainAccountDataRetentionMap();
      injector.getInstance(DataStoreService.class).purgeDataRetentionOlderRecords(dataRetentionMap);
    }), 0, 60L, TimeUnit.MINUTES);

    // Cleanup delegate versions from global account
    taskPollExecutor.scheduleWithFixedDelay(
        new Schedulable("Failed cleaning up manager versions.", injector.getInstance(ManagerVersionsCleanUpJob.class)),
        0L, 5L, TimeUnit.MINUTES);
  }

  private void scheduleJobsDelegateService(
      Injector injector, MainConfiguration configuration, ScheduledExecutorService delegateExecutor) {
    log.info("Initializing delegate service scheduled jobs ...");
    // delegate task broadcasting schedule job
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("delegateTaskNotifier")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateQueueTask.class), random.nextInt(5), 5L, TimeUnit.SECONDS);
    delegateExecutor.scheduleWithFixedDelay(new Schedulable("Failed while monitoring task progress updates",
                                                injector.getInstance(ProgressUpdateService.class)),
        0L, 5L, TimeUnit.SECONDS);

    delegateExecutor.scheduleWithFixedDelay(new Schedulable("Failed while detecting disconnected delegates",
                                                injector.getInstance(DelegateDisconnectedDetector.class)),
        0L, 60L, TimeUnit.SECONDS);

    delegateExecutor.scheduleWithFixedDelay(new Schedulable("Failed while monitoring sync task responses",
                                                injector.getInstance(DelegateSyncServiceImpl.class)),
        0L, 2L, TimeUnit.SECONDS);

    delegateExecutor.scheduleWithFixedDelay(new Schedulable("Failed while calculating delegate insights summaries",
                                                injector.getInstance(DelegateInsightsSummaryJob.class)),
        0L, 10L, TimeUnit.MINUTES);

    delegateExecutor.scheduleWithFixedDelay(
        new Schedulable("Failed while broadcasting perpetual tasks",
            () -> injector.getInstance(PerpetualTaskServiceImpl.class).broadcastToDelegate()),
        0L, 10L, TimeUnit.SECONDS);
  }

  public void registerObservers(MainConfiguration configuration, Injector injector, Environment environment) {
    // this can't be onboarded to remote observer pattern as things were not kryoserializable.
    AuditServiceImpl auditService = (AuditServiceImpl) injector.getInstance(Key.get(AuditService.class));
    AuditServiceHelper auditServiceHelper = injector.getInstance(Key.get(AuditServiceHelper.class));
    auditServiceHelper.getEntityCrudSubject().register(auditService);
    if (shouldEnableRemoteObservers(configuration)) {
      if (isDms()) {
        environment.lifecycle().manage(injector.getInstance(DmsEventConsumerService.class));
      }
      if (isManager()) {
        environment.lifecycle().manage(injector.getInstance(ManagerEventConsumerService.class));
      }
    } else {
      // Register Audit observer
      DelegateServiceImpl delegateServiceImpl =
          (DelegateServiceImpl) injector.getInstance(Key.get(DelegateService.class));

      if (isManager()) {
        registerManagerObservers(injector, delegateServiceImpl);
      }

      if (shouldEnableDelegateMgmt(configuration)) {
        registerDelegateServiceObservers(injector, delegateServiceImpl);
      }
    }
  }

  /**
   * All the observers that belong to Delegate service app
   * @param injector
   * @param delegateServiceImpl
   */
  private void registerDelegateServiceObservers(Injector injector, DelegateServiceImpl delegateServiceImpl) {
    delegateServiceImpl.getDelegateTaskStatusObserverSubject().register(
        injector.getInstance(Key.get(DelegateInsightsServiceImpl.class)));

    DelegateTaskServiceImpl delegateTaskService =
        (DelegateTaskServiceImpl) injector.getInstance(Key.get(DelegateTaskService.class));
    delegateTaskService.getDelegateTaskStatusObserverSubject().register(
        injector.getInstance(Key.get(DelegateInsightsServiceImpl.class)));

    DelegateProfileServiceImpl delegateProfileService =
        (DelegateProfileServiceImpl) injector.getInstance(Key.get(DelegateProfileService.class));
    DelegateProfileEventHandler delegateProfileEventHandler =
        injector.getInstance(Key.get(DelegateProfileEventHandler.class));
    delegateServiceImpl.getDelegateProfileSubject().register(delegateProfileEventHandler);
    delegateProfileService.getDelegateProfileSubject().register(delegateProfileEventHandler);

    // Eventually will be moved to dms
    PerpetualTaskServiceImpl perpetualTaskService =
        (PerpetualTaskServiceImpl) injector.getInstance(Key.get(PerpetualTaskService.class));
    perpetualTaskService.getPerpetualTaskCrudSubject().register(
        injector.getInstance(Key.get(PerpetualTaskRecordHandler.class)));
    perpetualTaskService.getPerpetualTaskStateObserverSubject().register(
        injector.getInstance(Key.get(DelegateInsightsServiceImpl.class)));
    delegateServiceImpl.getSubject().register(perpetualTaskService);

    ClusterRecordHandler clusterRecordHandler = injector.getInstance(Key.get(ClusterRecordHandler.class));
    SettingsServiceImpl settingsService = (SettingsServiceImpl) injector.getInstance(Key.get(SettingsService.class));
    settingsService.getSubject().register(clusterRecordHandler);

    KubernetesClusterHandler kubernetesClusterHandler = injector.getInstance(Key.get(KubernetesClusterHandler.class));
    delegateServiceImpl.getSubject().register(kubernetesClusterHandler);

    CEPerpetualTaskHandler cePerpetualTaskHandler = injector.getInstance(Key.get(CEPerpetualTaskHandler.class));
    ClusterRecordServiceImpl clusterRecordService =
        (ClusterRecordServiceImpl) injector.getInstance(Key.get(ClusterRecordService.class));
    clusterRecordService.getSubject().register(cePerpetualTaskHandler);
  }

  /**
   * All the observers that belong to manager
   * @param injector
   * @param delegateServiceImpl
   */
  private void registerManagerObservers(Injector injector, DelegateServiceImpl delegateServiceImpl) {
    YamlPushServiceImpl yamlPushService = (YamlPushServiceImpl) injector.getInstance(Key.get(YamlPushService.class));
    AuditServiceImpl auditService = (AuditServiceImpl) injector.getInstance(Key.get(AuditService.class));
    yamlPushService.getEntityCrudSubject().register(auditService);

    ClusterRecordHandler clusterRecordHandler = injector.getInstance(Key.get(ClusterRecordHandler.class));
    AppManifestCloudProviderPTaskManager appManifestCloudProviderPTaskManager =
        injector.getInstance(Key.get(AppManifestCloudProviderPTaskManager.class));
    SettingsServiceImpl settingsService = (SettingsServiceImpl) injector.getInstance(Key.get(SettingsService.class));
    settingsService.getSubject().register(clusterRecordHandler);
    settingsService.getSubject().register(appManifestCloudProviderPTaskManager);
    settingsService.getArtifactStreamSubject().register(
        injector.getInstance(Key.get(ArtifactStreamSettingAttributePTaskManager.class)));
    settingsService.getAppManifestSubject().register(
        injector.getInstance(Key.get(AppManifestSettingAttributePTaskManager.class)));

    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
        (InfrastructureDefinitionServiceImpl) injector.getInstance(Key.get(InfrastructureDefinitionService.class));
    infrastructureDefinitionService.getSubject().register(clusterRecordHandler);

    InfrastructureMappingServiceImpl infrastructureMappingService =
        (InfrastructureMappingServiceImpl) injector.getInstance(Key.get(InfrastructureMappingService.class));
    infrastructureMappingService.getSubject().register(clusterRecordHandler);

    CEPerpetualTaskHandler cePerpetualTaskHandler = injector.getInstance(Key.get(CEPerpetualTaskHandler.class));
    ClusterRecordServiceImpl clusterRecordService =
        (ClusterRecordServiceImpl) injector.getInstance(Key.get(ClusterRecordService.class));
    clusterRecordService.getSubject().register(cePerpetualTaskHandler);

    ArtifactStreamServiceImpl artifactStreamService =
        (ArtifactStreamServiceImpl) injector.getInstance(Key.get(ArtifactStreamService.class));
    artifactStreamService.getSubject().register(injector.getInstance(Key.get(ArtifactStreamPTaskManager.class)));

    AccountServiceImpl accountService = (AccountServiceImpl) injector.getInstance(Key.get(AccountService.class));
    accountService.getAccountCrudSubject().register(
        (DelegateProfileServiceImpl) injector.getInstance(Key.get(DelegateProfileService.class)));
    accountService.getAccountCrudSubject().register(injector.getInstance(Key.get(CEPerpetualTaskHandler.class)));
    accountService.getAccountCrudSubject().register(
        (DelegateTokenServiceImpl) injector.getInstance(Key.get(DelegateTokenService.class)));

    accountService.getAccountCrudSubject().register(
        (DelegateNgTokenServiceImpl) injector.getInstance(Key.get(DelegateNgTokenService.class)));

    ApplicationManifestServiceImpl applicationManifestService =
        (ApplicationManifestServiceImpl) injector.getInstance(Key.get(ApplicationManifestService.class));
    applicationManifestService.getSubject().register(injector.getInstance(Key.get(ManifestPerpetualTaskManger.class)));

    ObserversHelper.registerSharedObservers(injector);
  }

  public static void registerIteratorsDelegateService(IteratorsConfig iteratorsConfig, Injector injector) {
    injector.getInstance(PerpetualTaskRecordHandler.class)
        .registerIterators(iteratorsConfig.getPerpetualTaskAssignmentIteratorConfig().getThreadPoolSize(),
            iteratorsConfig.getPerpetualTaskRebalanceIteratorConfig().getThreadPoolSize());
    injector.getInstance(DelegateTaskExpiryCheckIterator.class)
        .registerIterators(iteratorsConfig.getDelegateTaskExpiryCheckIteratorConfig().getThreadPoolSize());
    injector.getInstance(FailDelegateTaskIterator.class)
        .registerIterators(iteratorsConfig.getFailDelegateTaskIteratorConfig().getThreadPoolSize());
    injector.getInstance(DelegateTelemetryPublisher.class).registerIterators();
  }

  public static void registerIteratorsManager(IteratorsConfig iteratorsConfig, Injector injector) {
    final ScheduledThreadPoolExecutor artifactCollectionExecutor =
        new ScheduledThreadPoolExecutor(iteratorsConfig.getArtifactCollectionIteratorConfig().getThreadPoolSize(),
            new ThreadFactoryBuilder().setNameFormat("Iterator-ArtifactCollection").build());
    final ScheduledThreadPoolExecutor eventDeliveryExecutor =
        new ScheduledThreadPoolExecutor(iteratorsConfig.getEventDeliveryIteratorConfig().getThreadPoolSize(),
            new ThreadFactoryBuilder().setNameFormat("Iterator-Event-Delivery").build());

    injector.getInstance(AlertReconciliationHandler.class).registerIterators();
    injector.getInstance(ArtifactCollectionHandler.class).registerIterators(artifactCollectionExecutor);
    injector.getInstance(ArtifactCleanupHandler.class).registerIterators(artifactCollectionExecutor);
    injector.getInstance(EventDeliveryHandler.class).registerIterators(eventDeliveryExecutor);
    injector.getInstance(InstanceSyncHandler.class)
        .registerIterators(iteratorsConfig.getInstanceSyncIteratorConfig().getThreadPoolSize());
    injector.getInstance(LicenseCheckHandler.class).registerIterators();
    injector.getInstance(ApprovalPollingHandler.class).registerIterators();
    injector.getInstance(GCPBillingHandler.class).registerIterators();
    injector.getInstance(SegmentGroupEventJob.class).registerIterators();
    injector.getInstance(BarrierServiceImpl.class).registerIterators();
    injector.getInstance(EntityAuditRecordHandler.class).registerIterators();
    injector.getInstance(UsageMetricsHandler.class).registerIterators();
    injector.getInstance(ResourceConstraintBackupHandler.class)
        .registerIterators(iteratorsConfig.getResourceConstraintBackupIteratorConfig().getThreadPoolSize());
    injector.getInstance(WorkflowExecutionMonitorHandler.class)
        .registerIterators(iteratorsConfig.getWorkflowExecutionMonitorIteratorConfig().getThreadPoolSize());
    injector.getInstance(SettingAttributeValidateConnectivityHandler.class)
        .registerIterators(iteratorsConfig.getSettingAttributeValidateConnectivityIteratorConfig().getThreadPoolSize());
    injector.getInstance(VaultSecretManagerRenewalHandler.class)
        .registerIterators(iteratorsConfig.getVaultSecretManagerRenewalIteratorConfig().getThreadPoolSize());
    injector.getInstance(SettingAttributesSecretsMigrationHandler.class).registerIterators();
    injector.getInstance(GitSyncEntitiesExpiryHandler.class).registerIterators();
    injector.getInstance(ExportExecutionsRequestHandler.class).registerIterators();
    injector.getInstance(ExportExecutionsRequestCleanupHandler.class).registerIterators();
    injector.getInstance(DeploymentFreezeActivationHandler.class).registerIterators();
    injector.getInstance(DeploymentFreezeDeactivationHandler.class).registerIterators();
    injector.getInstance(CeLicenseExpiryHandler.class).registerIterators();
    injector.getInstance(DeleteAccountHandler.class).registerIterators();
    injector.getInstance(DeletedEntityHandler.class).registerIterators();
    injector.getInstance(ResourceLookupSyncHandler.class).registerIterators();
    injector.getInstance(AccessRequestHandler.class).registerIterators();
    injector.getInstance(ScheduledTriggerHandler.class).registerIterators();
    injector.getInstance(LdapGroupScheduledHandler.class).registerIterators();
    injector.getInstance(EncryptedDataLocalToGcpKmsMigrationHandler.class).registerIterators();
    injector.getInstance(TimeoutEngine.class)
        .registerIterators(
            IteratorConfig.builder().enabled(true).targetIntervalInSeconds(10).threadPoolCount(5).build());
  }

  private void registerCronJobs(Injector injector) {
    log.info("Register cron jobs...");
    final PersistentScheduler jobScheduler =
        injector.getInstance(Key.get(PersistentScheduler.class, Names.named("BackgroundJobScheduler")));

    PersistentLocker persistentLocker = injector.getInstance(Key.get(PersistentLocker.class));

    try (AcquiredLock acquiredLock = persistentLocker.waitToAcquireLock(
             WingsApplication.class, "Initialization", ofSeconds(5), ofSeconds(10))) {
      // If we do not get the lock, that's not critical - that's most likely because other managers took it
      // and they will initialize the jobs.
      if (acquiredLock != null) {
        YamlChangeSetPruneJob.add(jobScheduler);
        InstancesPurgeJob.add(jobScheduler);
        AccountPasswordExpirationJob.add(jobScheduler);
      }
    }

    WingsPersistence wingsPersistence = injector.getInstance(Key.get(WingsPersistence.class));

    new Thread(() -> {
      AdvancedDatastore datastore = wingsPersistence.getDatastore(HPersistence.DEFAULT_STORE);
      QuartzCleaner.cleanup(datastore, "quartz");
      QuartzCleaner.cleanup(datastore, "quartz_verification");
    }).start();
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(KryoFeature.class));
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(JsonProcessingExceptionMapper.class);
    environment.jersey().register(ConstraintViolationExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapper.class);
    environment.jersey().register(GenericExceptionMapper.class);
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerAuthFilters(MainConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isEnableAuth()) {
      environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
      environment.jersey().register(injector.getInstance(LoginRateLimitFilter.class));
      environment.jersey().register(injector.getInstance(AuthRuleFilter.class));
      environment.jersey().register(injector.getInstance(AuthResponseFilter.class));
      environment.jersey().register(injector.getInstance(AuthenticationFilter.class));
    }
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void initializeServiceSecretKeys(Injector injector) {
    injector.getInstance(VerificationServiceSecretManager.class).initializeServiceSecretKeys();
  }

  private void runMigrations(Injector injector) {
    injector.getInstance(MigrationService.class).runMigrations();
  }

  private void initMetrics(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }
}
