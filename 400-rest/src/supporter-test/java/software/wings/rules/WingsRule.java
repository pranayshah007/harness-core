/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.rules;

import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.maintenance.MaintenanceController.forceMaintenance;
import static io.harness.manage.GlobalContextManager.upsertGlobalContextRecord;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.utils.WingsTestConstants.PORTAL_URL;
import static software.wings.utils.WingsTestConstants.VERIFICATION_PATH;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;

import io.harness.AccessControlClientConfiguration;
import io.harness.NoopStatement;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.commandlibrary.client.CommandLibraryServiceHttpClient;
import io.harness.config.PublisherConfiguration;
import io.harness.delegate.authenticator.DelegateTokenAuthenticatorImpl;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.StartupMode;
import io.harness.event.EventsModule;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.ff.FeatureFlagConfig;
import io.harness.globalcontex.AuditGlobalContextData;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.client.AbstractManagerGrpcClientModule;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.client.ManagerGrpcClientModule;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfig;
import io.harness.manage.GlobalContextManager;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;
import io.harness.module.DelegateServiceModule;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.observer.NoOpRemoteObserverInformerImpl;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.consumer.AbstractRemoteObserverModule;
import io.harness.queue.QueueListener;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.queueservice.config.DelegateQueueServiceConfig;
import io.harness.redis.RedisConfig;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.serializer.kryo.TestManagerKryoRegistrar;
import io.harness.serializer.morphia.ManagerTestMorphiaRegistrar;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.NotifyResponseCleaner;
import io.harness.waiter.OrchestrationNotifyEventListener;

import software.wings.DataStorageMode;
import software.wings.WingsTestModule;
import software.wings.app.AuthModule;
import software.wings.app.ExecutorConfig;
import software.wings.app.ExecutorsConfig;
import software.wings.app.GcpMarketplaceIntegrationModule;
import software.wings.app.GeneralNotifyEventListener;
import software.wings.app.IndexMigratorModule;
import software.wings.app.JobsFrequencyConfig;
import software.wings.app.MainConfiguration;
import software.wings.app.ManagerExecutorModule;
import software.wings.app.ManagerQueueModule;
import software.wings.app.ObserversHelper;
import software.wings.app.SSOModule;
import software.wings.app.SearchModule;
import software.wings.app.SignupModule;
import software.wings.app.TemplateModule;
import software.wings.app.WingsModule;
import software.wings.app.YamlModule;
import software.wings.integration.IntegrationTestBase;
import software.wings.scheduler.LdapSyncJobConfig;
import software.wings.security.authentication.MarketPlaceConfig;
import software.wings.security.authentication.totp.SimpleTotpModule;
import software.wings.security.authentication.totp.TotpConfig;
import software.wings.security.authentication.totp.TotpLimit;
import software.wings.service.impl.EventEmitter;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.mongodb.MongoClient;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.Configuration;
import io.dropwizard.lifecycle.Managed;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.atmosphere.cpr.BroadcasterFactory;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(HarnessTeam.PL)
@Deprecated
public class WingsRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  protected ClosingFactory closingFactory = new ClosingFactory();

  protected Configuration configuration;
  protected Injector injector;
  private int port;
  private ExecutorService executorService = new CurrentThreadExecutor();

  private static final String JWT_PASSWORD_SECRET = "123456789";

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    if (isIntegrationTest(target)) {
      return new NoopStatement();
    }
    if (isIgnorePropertySetAndNotAlwaysRun(frameworkMethod)) {
      return new NoopStatement();
    }
    Statement wingsStatement = new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try (GlobalContextGuard ignore = GlobalContextManager.ensureGlobalContextGuard()) {
          upsertGlobalContextRecord(AuditGlobalContextData.builder().auditId("testing").build());
          List<Annotation> annotations = Lists.newArrayList(asList(frameworkMethod.getAnnotations()));
          annotations.addAll(asList(target.getClass().getAnnotations()));
          before(annotations, isIntegrationTest(target),
              target.getClass().getSimpleName() + "." + frameworkMethod.getName());
          injector.injectMembers(target);
          try {
            statement.evaluate();
          } finally {
            after(annotations);
          }
        }
      }
    };

    return wingsStatement;
  }

  private boolean isIgnorePropertySetAndNotAlwaysRun(FrameworkMethod frameworkMethod) {
    return frameworkMethod.getAnnotation(HarnessAlwaysRun.class) == null
        && "true".equalsIgnoreCase(System.getenv("IGNORE_400_TESTS")) && ownedByCdTeam(frameworkMethod);
  }

  private boolean ownedByCdTeam(FrameworkMethod frameworkMethod) {
    Set<HarnessTeam> cdTeams = ImmutableSet.of(HarnessTeam.CDC, HarnessTeam.CDP, HarnessTeam.PIPELINE);
    OwnedBy annotation = frameworkMethod.getDeclaringClass().getAnnotation(OwnedBy.class);
    if (annotation == null) {
      log.info("No owned by test present for test {}", frameworkMethod.getName());
      return false;
    }
    HarnessTeam value = annotation.value();
    return cdTeams.contains(value);
  }

  protected boolean isIntegrationTest(Object target) {
    return target instanceof IntegrationTestBase;
  }

  /**
   * Before.
   *
   * @param annotations                   the annotations
   * @param doesExtendBaseIntegrationTest the does extend base integration test
   * @param testName                      the test name  @throws Throwable the throwable
   * @throws Throwable the throwable
   */
  protected void before(List<Annotation> annotations, boolean doesExtendBaseIntegrationTest, String testName)
      throws Throwable {
    setProperty("javax.cache.spi.CachingProvider", "com.hazelcast.cache.HazelcastCachingProvider");
    initializeLogging();
    forceMaintenance(false);
    MongoClient mongoClient;
    String dbName = getProperty("dbName", "harness");

    configuration = getConfiguration(annotations, dbName);

    List<Module> modules = modules(annotations);
    modules.add(KryoModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return getKryoRegistrars();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return getMorphiaRegistrars();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(ManagerRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(ManagerRegistrars.springConverters)
            .build();
      }
    });
    addQueueModules(modules);
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return CfClientConfig.builder().build();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return FeatureFlagConfig.builder().build();
      }
    });

    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(0, cacheModule);
    long start = currentTimeMillis();
    injector = Guice.createInjector(modules);
    long diff = currentTimeMillis() - start;
    log.info("Creating guice injector took: {}ms", diff);
    registerListeners(annotations.stream().filter(Listeners.class ::isInstance).findFirst());
    registerScheduledJobs(injector);
    registerObservers();

    for (Module module : modules) {
      if (module instanceof ServersModule) {
        for (Closeable server : ((ServersModule) module).servers(injector)) {
          closingFactory.addServer(server);
        }
      }
    }
  }

  protected Set<Class<? extends KryoRegistrar>> getKryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(ManagerRegistrars.kryoRegistrars)
        .add(TestManagerKryoRegistrar.class)
        .build();
  }

  protected Set<Class<? extends MorphiaRegistrar>> getMorphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(ManagerRegistrars.morphiaRegistrars)
        .add(ManagerTestMorphiaRegistrar.class)
        .build();
  }

  protected void registerObservers() {
    ObserversHelper.registerSharedObservers(injector);
  }

  protected void addQueueModules(List<Module> modules) {
    modules.add(new ManagerQueueModule());
  }

  protected Configuration getConfiguration(List<Annotation> annotations, String dbName) {
    MainConfiguration configuration = new MainConfiguration();
    configuration.getPortal().setCompanyName("COMPANY_NAME");
    configuration.getPortal().setUrl(PORTAL_URL);
    configuration.getPortal().setVerificationUrl(VERIFICATION_PATH);
    configuration.getPortal().setJwtExternalServiceSecret("JWT_EXTERNAL_SERVICE_SECRET");
    configuration.getPortal().setJwtPasswordSecret(JWT_PASSWORD_SECRET);
    configuration.getPortal().setJwtNextGenManagerSecret("dummy_key");
    configuration.getPortal().setOptionalDelegateTaskRejectAtLimit(10000);
    configuration.getPortal().setImportantDelegateTaskRejectAtLimit(50000);
    configuration.getPortal().setCriticalDelegateTaskRejectAtLimit(100000);
    configuration.setApiUrl("http:localhost:8080");
    configuration.setMongoConnectionFactory(
        MongoConfig.builder().uri(getProperty("mongoUri", "mongodb://localhost:27017/" + dbName)).build());
    configuration.getBackgroundSchedulerConfig().setAutoStart(getProperty("setupScheduler", "false"));
    configuration.getServiceSchedulerConfig().setAutoStart(getProperty("setupScheduler", "false"));

    configuration.setExecutorsConfig(
        ExecutorsConfig.builder().dataReconciliationExecutorConfig(ExecutorConfig.builder().build()).build());

    configuration.setGrpcDelegateServiceClientConfig(
        GrpcClientConfig.builder().target("localhost:9880").authority("localhost").build());
    configuration.setGrpcDMSClientConfig(
        GrpcClientConfig.builder().target("localhost:15011").authority("localhost").build());
    configuration.setDmsSecret("dummy_key");

    configuration.setGrpcClientConfig(
        GrpcClientConfig.builder().target("localhost:9880").authority("localhost").build());

    configuration.setLogStreamingServiceConfig(
        LogStreamingServiceConfig.builder().baseUrl("http://localhost:8079").serviceToken("token").build());

    configuration.setQueueServiceConfig(
        DelegateQueueServiceConfig.builder()
            .topic("delegate-service")
            .enableQueueAndDequeue(false)
            .queueServiceClientConfig(QueueServiceClientConfig.builder()
                                          .httpClientConfig(ServiceHttpClientConfig.builder()
                                                                .baseUrl("http://localhost:9091/")
                                                                .readTimeOutSeconds(15)
                                                                .connectTimeOutSeconds(15)
                                                                .build())
                                          .build())
            .build());
    configuration.setAccessControlClientConfiguration(
        AccessControlClientConfiguration.builder()
            .enableAccessControl(false)
            .accessControlServiceSecret("token")
            .accessControlServiceConfig(ServiceHttpClientConfig.builder()
                                            .baseUrl("http://localhost:9006/api/")
                                            .readTimeOutSeconds(15)
                                            .connectTimeOutSeconds(15)
                                            .build())
            .build());
    MarketPlaceConfig marketPlaceConfig =
        MarketPlaceConfig.builder().azureMarketplaceAccessKey("qwertyu").azureMarketplaceSecretKey("qwertyu").build();
    configuration.setMarketPlaceConfig(marketPlaceConfig);

    JobsFrequencyConfig jobsFrequencyConfig = JobsFrequencyConfig.builder().build();
    configuration.setJobsFrequencyConfig(jobsFrequencyConfig);

    if (annotations.stream().anyMatch(SetupScheduler.class ::isInstance)) {
      configuration.getBackgroundSchedulerConfig().setAutoStart("true");
      configuration.getServiceSchedulerConfig().setAutoStart("true");
      configuration.getBackgroundSchedulerConfig().setJobStoreClass(
          org.quartz.simpl.RAMJobStore.class.getCanonicalName());
      configuration.getServiceSchedulerConfig().setJobStoreClass(org.quartz.simpl.RAMJobStore.class.getCanonicalName());
    }

    MarketoConfig marketoConfig =
        MarketoConfig.builder().clientId("client_id").clientSecret("client_secret_id").enabled(false).build();
    configuration.setMarketoConfig(marketoConfig);

    SegmentConfig segmentConfig = SegmentConfig.builder().enabled(false).url("dummy_url").apiKey("dummy_key").build();
    configuration.setSegmentConfig(segmentConfig);

    ServiceHttpClientConfig ngManagerServiceHttpClientConfig =
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build();
    configuration.setNgManagerServiceHttpClientConfig(ngManagerServiceHttpClientConfig);

    configuration.setDistributedLockImplementation(DistributedLockImplementation.NOOP);
    configuration.setEventsFrameworkConfiguration(
        EventsFrameworkConfiguration.builder()
            .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
            .build());

    configuration.setFileStorageMode(DataStorageMode.MONGO);
    configuration.setClusterName("");

    configuration.setCfClientConfig(CfClientConfig.builder().build());
    configuration.setTimeScaleDBConfig(TimeScaleDBConfig.builder().build());
    configuration.setCfMigrationConfig(CfMigrationConfig.builder()
                                           .account("testAccount")
                                           .enabled(false)
                                           .environment("testEnv")
                                           .org("testOrg")
                                           .project("testProject")
                                           .build());
    configuration.setLdapSyncJobConfig(
        LdapSyncJobConfig.builder().defaultCronExpression("0 0 23 ? * SAT *").poolSize(3).syncInterval(15).build());

    configuration.setDelegateServiceRedisConfig(RedisConfig.builder().redisUrl("rediss://").build());
    configuration.setTotpConfig(
        TotpConfig.builder()
            .secOpsEmail("secops.fake.email@mailnator.com")
            .incorrectAttemptsUntilSecOpsNotified(50)
            .limit(TotpLimit.builder().count(10).duration(3).durationUnit(TimeUnit.MINUTES).build())
            .build());
    SegmentConfiguration segmentConfiguration = SegmentConfiguration.builder()
                                                    .enabled(false)
                                                    .url("dummy_url")
                                                    .apiKey("dummy_key")
                                                    .certValidationRequired(false)
                                                    .build();
    configuration.setSegmentConfiguration(segmentConfiguration);
    return configuration;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(executorService);

    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(mongoTypeModule(annotations));

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(EventEmitter.class).toInstance(mock(EventEmitter.class));
        bind(BroadcasterFactory.class).toInstance(mock(BroadcasterFactory.class));
        bind(MetricRegistry.class);
        bind(CommandLibraryServiceHttpClient.class).toInstance(mock(CommandLibraryServiceHttpClient.class));
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(DelegateTokenAuthenticator.class).to(DelegateTokenAuthenticatorImpl.class).in(Singleton.class);
        bind(DelegateCache.class).to(DelegateCacheImpl.class).in(Singleton.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      PublisherConfiguration publisherConfiguration() {
        return PublisherConfiguration.allOn();
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Named("delegate")
      @Singleton
      public RLocalCachedMap<String, Delegate> getDelegateCache(DelegateRedissonCacheManager cacheManager) {
        return mock(RLocalCachedMap.class);
      }

      @Provides
      @Named("delegate_group")
      @Singleton
      public RLocalCachedMap<String, DelegateGroup> getDelegateGroupCache(DelegateRedissonCacheManager cacheManager) {
        return mock(RLocalCachedMap.class);
      }

      @Provides
      @Named("delegates_from_group")
      @Singleton
      public RLocalCachedMap<String, List<Delegate>> getDelegatesFromGroupCache(
          DelegateRedissonCacheManager cacheManager) {
        return mock(RLocalCachedMap.class);
      }

      @Provides
      @Named("aborted_task_list")
      @Singleton
      public RLocalCachedMap<String, Set<String>> getAbortedTaskListCache(DelegateRedissonCacheManager cacheManager) {
        return mock(RLocalCachedMap.class);
      }

      @Provides
      @Singleton
      @Named("enableRedisForDelegateService")
      boolean isEnableRedisForDelegateService() {
        return false;
      }

      @Provides
      @Singleton
      @Named("redissonClient")
      RedissonClient redissonClient() {
        return mock(RedissonClient.class);
      }
    });
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
    modules.add(new ValidationModule(validatorFactory));
    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceTestModule());
    modules.add(new DelegateServiceModule());
    modules.add(new WingsModule((MainConfiguration) configuration, StartupMode.MANAGER));
    modules.add(new SimpleTotpModule());
    modules.add(new IndexMigratorModule());
    modules.add(new YamlModule());
    modules.add(new ManagerExecutorModule());
    modules.add(new WingsTestModule());
    modules.add(new TemplateModule());
    modules.add(new EventsModule((MainConfiguration) configuration));
    modules.add(new SSOModule());
    modules.add(new AuthModule());
    modules.add(new SignupModule());
    modules.add(new GcpMarketplaceIntegrationModule());
    modules.add(new AbstractManagerGrpcClientModule() {
      @Override
      public ManagerGrpcClientModule.Config config() {
        return ManagerGrpcClientModule.Config.builder()
            .target(((MainConfiguration) configuration).getGrpcClientConfig().getTarget())
            .authority(((MainConfiguration) configuration).getGrpcClientConfig().getAuthority())
            .build();
      }

      @Override
      public String application() {
        return "Manager";
      }
    });
    modules.add(new SearchModule());
    return modules;
  }

  private void registerListeners(java.util.Optional<Annotation> listenerOptional) {
    if (listenerOptional.isPresent()) {
      for (Class<? extends QueueListener> queueListenerClass : ((Listeners) listenerOptional.get()).value()) {
        if (queueListenerClass.equals(GeneralNotifyEventListener.class)) {
          final QueuePublisher<NotifyEvent> publisher =
              injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
          final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
              injector.getInstance(NotifyQueuePublisherRegister.class);
          notifyQueuePublisherRegister.register(GENERAL, payload -> publisher.send(asList(GENERAL), payload));
        } else if (queueListenerClass.equals(OrchestrationNotifyEventListener.class)) {
          final QueuePublisher<NotifyEvent> publisher =
              injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
          final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
              injector.getInstance(NotifyQueuePublisherRegister.class);
          notifyQueuePublisherRegister.register(
              ORCHESTRATION, payload -> publisher.send(asList(ORCHESTRATION), payload));
        }
        injector.getInstance(QueueListenerController.class).register(injector.getInstance(queueListenerClass), 2);
      }
    }
  }

  /**
   * After.
   *
   * @param annotations the annotations
   */
  protected void after(List<Annotation> annotations) {
    try {
      log.info("Stopping executorService...");
      executorService.shutdownNow();
      log.info("Stopped executorService...");
    } catch (Exception ex) {
      log.error("", ex);
    }

    try {
      log.info("Stopping notifier...");
      ((Managed) injector.getInstance(NotifierScheduledExecutorService.class)).stop();
      log.info("Stopped notifier...");
    } catch (Exception ex) {
      log.error("", ex);
    }

    try {
      log.info("Stopping queue listener controller...");
      injector.getInstance(QueueListenerController.class).stop();
      log.info("Stopped queue listener controller...");
    } catch (Exception ex) {
      log.error("", ex);
    }

    log.info("Stopping servers...");
    closingFactory.stopServers();
  }

  protected void registerScheduledJobs(Injector injector) {
    log.info("Initializing scheduledJobs...");
    injector.getInstance(NotifierScheduledExecutorService.class)
        .scheduleWithFixedDelay(injector.getInstance(NotifyResponseCleaner.class), 0L, 1000L, TimeUnit.MILLISECONDS);
  }
}
