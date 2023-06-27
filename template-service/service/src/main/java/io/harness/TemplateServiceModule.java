/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.authorization.AuthorizationServiceHeader.TEMPLATE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.ng.core.template.TemplateEntityConstants.ARTIFACT_SOURCE;
import static io.harness.ng.core.template.TemplateEntityConstants.CUSTOM_DEPLOYMENT;
import static io.harness.ng.core.template.TemplateEntityConstants.MONITORED_SERVICE;
import static io.harness.ng.core.template.TemplateEntityConstants.PIPELINE;
import static io.harness.ng.core.template.TemplateEntityConstants.SECRET_MANAGER;
import static io.harness.ng.core.template.TemplateEntityConstants.STAGE;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP_GROUP;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.customDeployment.CustomDeploymentClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.engine.GovernanceService;
import io.harness.engine.GovernanceServiceImpl;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.ff.FeatureFlagService;
import io.harness.ff.FeatureFlagServiceImpl;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngsettings.client.remote.NGSettingsClientModule;
import io.harness.opaclient.OpaClientModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.pipeline.yamlschema.PipelineYamlSchemaClientModule;
import io.harness.project.ProjectClientModule;
import io.harness.reconcile.NgManagerReconcileClientModule;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.TemplateServiceModuleRegistrars;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.ServiceResourceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.template.event.OrgEntityCrudStreamListener;
import io.harness.template.event.ProjectEntityCrudStreamListener;
import io.harness.template.events.TemplateOutboxEventHandler;
import io.harness.template.eventsframework.TemplateEventsFrameworkModule;
import io.harness.template.handler.CustomDeploymentYamlConversionHandler;
import io.harness.template.handler.PipelineTemplateYamlConversionHandler;
import io.harness.template.handler.SecretManagerTemplateYamlConversionHandler;
import io.harness.template.handler.StepGroupTemplateYamlConversionHandler;
import io.harness.template.handler.TemplateYamlConversionHandler;
import io.harness.template.handler.TemplateYamlConversionHandlerRegistry;
import io.harness.template.health.HealthResource;
import io.harness.template.health.HealthResourceImpl;
import io.harness.template.mappers.TemplateFilterPropertiesMapper;
import io.harness.template.resources.NGTemplateRefreshResource;
import io.harness.template.resources.NGTemplateRefreshResourceImpl;
import io.harness.template.resources.NGTemplateResource;
import io.harness.template.resources.NGTemplateResourceImpl;
import io.harness.template.resources.NGTemplateSchemaResource;
import io.harness.template.resources.NGTemplateSchemaResourceImpl;
import io.harness.template.services.NGTemplateSchemaService;
import io.harness.template.services.NGTemplateSchemaServiceImpl;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceImpl;
import io.harness.template.services.TemplateAsyncSetupUsageService;
import io.harness.template.services.TemplateAsyncSetupUsageServiceImpl;
import io.harness.template.services.TemplateGitXService;
import io.harness.template.services.TemplateGitXServiceImpl;
import io.harness.template.services.TemplateMergeService;
import io.harness.template.services.TemplateMergeServiceImpl;
import io.harness.template.services.TemplateRefreshService;
import io.harness.template.services.TemplateRefreshServiceImpl;
import io.harness.threading.ThreadPool;
import io.harness.time.TimeModule;
import io.harness.token.TokenClientModule;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.client.YamlSchemaClientModule;
import io.harness.yaml.schema.client.config.YamlSchemaClientConfig;
import io.harness.yaml.schema.client.config.YamlSchemaHttpClientConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(CDC)
public class TemplateServiceModule extends AbstractModule {
  private final TemplateServiceConfiguration templateServiceConfiguration;

  private static TemplateServiceModule instance;

  public TemplateServiceModule(TemplateServiceConfiguration templateServiceConfiguration) {
    this.templateServiceConfiguration = templateServiceConfiguration;
  }

  public static TemplateServiceModule getInstance(TemplateServiceConfiguration appConfig) {
    if (instance == null) {
      instance = new TemplateServiceModule(appConfig);
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
    install(ExceptionModule.getInstance());
    install(TemplateServiceGrpcClientModule.getInstance(templateServiceConfiguration));
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.SPRING).build();
      }
    });
    install(new TemplateServicePersistenceModule());
    install(PersistentLockModule.getInstance());
    install(DelegateServiceDriverModule.getInstance(true, false));
    install(PrimaryVersionManagerModule.getInstance());
    install(TimeModule.getInstance());
    install(FiltersModule.getInstance());
    install(new ValidationModule(getValidatorFactory()));
    install(new ProjectClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(new OrganizationClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(new EntitySetupUsageClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(new PipelineYamlSchemaClientModule(
        ServiceHttpClientConfig.builder()
            .baseUrl(templateServiceConfiguration.getPipelineServiceClientConfig().getBaseUrl())
            .build(),
        templateServiceConfiguration.getPipelineServiceSecret(), TEMPLATE_SERVICE.toString()));
    install(new DelegateServiceDriverGrpcClientModule(templateServiceConfiguration.getManagerServiceSecret(),
        templateServiceConfiguration.getManagerTarget(), templateServiceConfiguration.getManagerAuthority(), true));
    install(new AuditClientModule(this.templateServiceConfiguration.getAuditClientConfig(),
        this.templateServiceConfiguration.getManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId(),
        this.templateServiceConfiguration.isEnableAudit()));
    install(new CustomDeploymentClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(new NgManagerReconcileClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, TEMPLATE_SERVICE.getServiceId(), false));
    install(new TokenClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(AccessControlClientModule.getInstance(
        this.templateServiceConfiguration.getAccessControlClientConfiguration(), TEMPLATE_SERVICE.getServiceId()));
    install(new TemplateEventsFrameworkModule(this.templateServiceConfiguration.getEventsFrameworkConfiguration()));
    install(new AccountClientModule(templateServiceConfiguration.getManagerClientConfig(),
        templateServiceConfiguration.getManagerServiceSecret(), TEMPLATE_SERVICE.toString()));
    install(YamlSdkModule.getInstance());
    Map<String, YamlSchemaHttpClientConfig> yamlSchemaHttpClientConfigMap = new HashMap<>();
    yamlSchemaHttpClientConfigMap.put("cd",
        YamlSchemaHttpClientConfig.builder()
            .serviceHttpClientConfig(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig())
            .secret(this.templateServiceConfiguration.getNgManagerServiceSecret())
            .build());
    install(YamlSchemaClientModule.getInstance(
        YamlSchemaClientConfig.builder().yamlSchemaHttpClientMap(yamlSchemaHttpClientConfigMap).build(),
        TEMPLATE_SERVICE.getServiceId()));
    install(new OpaClientModule(templateServiceConfiguration.getOpaClientConfig(),
        templateServiceConfiguration.getPolicyManagerSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return templateServiceConfiguration.getSegmentConfiguration();
      }
    });

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("progressUpdateServiceExecutor"))
        .toInstance(new ManagedScheduledExecutorService("ProgressUpdateServiceExecutor-Thread"));
    bind(OutboxEventHandler.class).to(TemplateOutboxEventHandler.class);
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(NGTemplateService.class).to(NGTemplateServiceImpl.class);
    bind(GovernanceService.class).to(GovernanceServiceImpl.class);
    bind(NGTemplateSchemaService.class).to(NGTemplateSchemaServiceImpl.class);
    bind(TemplateRefreshService.class).to(TemplateRefreshServiceImpl.class);
    bind(NGTemplateResource.class).to(NGTemplateResourceImpl.class);
    bind(NGTemplateRefreshResource.class).to(NGTemplateRefreshResourceImpl.class);
    bind(NGTemplateSchemaResource.class).to(NGTemplateSchemaResourceImpl.class);
    bind(HealthResource.class).to(HealthResourceImpl.class);
    bind(TemplateMergeService.class).to(TemplateMergeServiceImpl.class).in(Singleton.class);
    bind(TemplateGitXService.class).to(TemplateGitXServiceImpl.class).in(Singleton.class);
    bind(PmsFeatureFlagService.class).to(PmsFeatureFlagHelper.class);
    bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
    bind(TemplateAsyncSetupUsageService.class).to(TemplateAsyncSetupUsageServiceImpl.class);
    install(new NGSettingsClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));
    install(EnforcementClientModule.getInstance(templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId(),
        templateServiceConfiguration.getEnforcementClientConfiguration()));

    install(new ServiceResourceClientModule(this.templateServiceConfiguration.getNgManagerServiceHttpClientConfig(),
        this.templateServiceConfiguration.getNgManagerServiceSecret(), TEMPLATE_SERVICE.getServiceId()));

    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.TEMPLATE.toString()).to(TemplateFilterPropertiesMapper.class);
    registerEventsFrameworkMessageListeners();
  }

  @Provides
  @Singleton
  public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(TemplateServiceModuleRegistrars.kryoRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(TemplateServiceModuleRegistrars.morphiaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends TypeConverter>> morphiaConverters() {
    return ImmutableSet.<Class<? extends TypeConverter>>builder()
        .addAll(TemplateServiceModuleRegistrars.morphiaConverters)
        .build();
  }

  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder()
        .addAll(TemplateServiceModuleRegistrars.yamlSchemaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  List<Class<? extends Converter<?, ?>>> springConverters() {
    return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
        .addAll(TemplateServiceModuleRegistrars.springConverters)
        .build();
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig() {
    return templateServiceConfiguration.getMongoConfig();
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return templateServiceConfiguration.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return RedisConfig.builder().build();
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

  private void registerEventsFrameworkMessageListeners() {
    bind(MessageListener.class)
        .annotatedWith(Names.named(PROJECT_ENTITY + ENTITY_CRUD))
        .to(ProjectEntityCrudStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(ORGANIZATION_ENTITY + ENTITY_CRUD))
        .to(OrgEntityCrudStreamListener.class);
  }

  @Provides
  @Singleton
  TemplateYamlConversionHandlerRegistry getTemplateYamlConversionHandlerRegistry(Injector injector) {
    TemplateYamlConversionHandlerRegistry templateYamlConversionHandlerRegistry =
        new TemplateYamlConversionHandlerRegistry();
    templateYamlConversionHandlerRegistry.register(STEP, injector.getInstance(TemplateYamlConversionHandler.class));
    templateYamlConversionHandlerRegistry.register(STAGE, injector.getInstance(TemplateYamlConversionHandler.class));
    templateYamlConversionHandlerRegistry.register(
        CUSTOM_DEPLOYMENT, injector.getInstance(CustomDeploymentYamlConversionHandler.class));
    templateYamlConversionHandlerRegistry.register(
        PIPELINE, injector.getInstance(PipelineTemplateYamlConversionHandler.class));
    templateYamlConversionHandlerRegistry.register(
        MONITORED_SERVICE, injector.getInstance(TemplateYamlConversionHandler.class));
    templateYamlConversionHandlerRegistry.register(
        SECRET_MANAGER, injector.getInstance(SecretManagerTemplateYamlConversionHandler.class));
    templateYamlConversionHandlerRegistry.register(
        ARTIFACT_SOURCE, injector.getInstance(TemplateYamlConversionHandler.class));
    templateYamlConversionHandlerRegistry.register(
        STEP_GROUP, injector.getInstance(StepGroupTemplateYamlConversionHandler.class));
    return templateYamlConversionHandlerRegistry;
  }

  @Provides
  @Singleton
  @Named("allowedParallelStages")
  public Integer getAllowedParallelStages() {
    return templateServiceConfiguration.getAllowedParallelStages();
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    TemplateServiceApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }
  private DelegateCallbackToken getDelegateCallbackToken(DelegateServiceGrpcClient delegateServiceClient) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("pms")
                                  .setConnection(templateServiceConfiguration.getMongoConfig().getUri())
                                  .build())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Singleton
  @Named("TemplateAsyncSetupUsageExecutorService")
  public Executor templateAsyncSetupUsageExecutorService() {
    return new ManagedExecutorService(
        ThreadPool.create(templateServiceConfiguration.getTemplateAsyncSetupUsagePoolConfig().getCorePoolSize(),
            templateServiceConfiguration.getTemplateAsyncSetupUsagePoolConfig().getMaxPoolSize(),
            templateServiceConfiguration.getTemplateAsyncSetupUsagePoolConfig().getIdleTime(),
            templateServiceConfiguration.getTemplateAsyncSetupUsagePoolConfig().getTimeUnit(),
            new ThreadFactoryBuilder().setNameFormat("TemplateAsyncSetupUsageExecutorService-%d").build()));
  }

  private ValidatorFactory getValidatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .parameterNameProvider(new ReflectionParameterNameProvider())
        .buildValidatorFactory();
  }
}
