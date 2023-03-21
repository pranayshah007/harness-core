/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static org.mockito.Mockito.mock;

import io.harness.AccessControlClientConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.GraphQLModule;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.commandlibrary.client.CommandLibraryServiceHttpClient;
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
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.logstreaming.LogStreamingServiceConfig;
import io.harness.module.DelegateServiceModule;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.observer.NoOpRemoteObserverInformerImpl;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.consumer.AbstractRemoteObserverModule;
import io.harness.queueservice.config.DelegateQueueServiceConfig;
import io.harness.redis.RedisConfig;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.serializer.kryo.TestManagerKryoRegistrar;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.version.VersionModule;

import software.wings.DataStorageMode;
import software.wings.app.AuthModule;
import software.wings.app.ExecutorConfig;
import software.wings.app.ExecutorsConfig;
import software.wings.app.GcpMarketplaceIntegrationModule;
import software.wings.app.IndexMigratorModule;
import software.wings.app.MainConfiguration;
import software.wings.app.ManagerExecutorModule;
import software.wings.app.ManagerQueueModule;
import software.wings.app.SSOModule;
import software.wings.app.SignupModule;
import software.wings.app.TemplateModule;
import software.wings.app.WingsModule;
import software.wings.app.YamlModule;
import software.wings.graphql.provider.QueryLanguageProvider;
import software.wings.scheduler.LdapSyncJobConfig;
import software.wings.security.authentication.totp.SimpleTotpModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import graphql.GraphQL;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.Getter;
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

@OwnedBy(HarnessTeam.DX)
@Slf4j
public class GraphQLRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;
  @Getter private GraphQL graphQL;

  public GraphQLRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public void initialize(Injector injector, List<Module> modules) {
    for (Module module : modules) {
      if (module instanceof ServersModule) {
        for (Closeable server : ((ServersModule) module).servers(injector)) {
          closingFactory.addServer(server);
        }
      }
    }
    final QueryLanguageProvider<GraphQL> instance =
        injector.getInstance(Key.get(new TypeLiteral<QueryLanguageProvider<GraphQL>>() {}));
    graphQL = instance.getPrivateGraphQL();
    initializeLogging();
  }

  protected MainConfiguration getConfiguration(String dbName) {
    MainConfiguration configuration = new MainConfiguration();
    configuration.getPortal().setCompanyName("COMPANY_NAME");
    configuration.getPortal().setUrl("PORTAL_URL");
    configuration.setApiUrl("API_URL");
    configuration.getPortal().setVerificationUrl("VERIFICATION_PATH");
    configuration.getPortal().setJwtExternalServiceSecret("JWT_EXTERNAL_SERVICE_SECRET");
    configuration.getPortal().setJwtNextGenManagerSecret("dummy_key");
    configuration.setNgManagerServiceHttpClientConfig(
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build());
    configuration.setMongoConnectionFactory(
        MongoConfig.builder().uri(System.getProperty("mongoUri", "mongodb://localhost:27017/" + dbName)).build());
    configuration.getBackgroundSchedulerConfig().setAutoStart(System.getProperty("setupScheduler", "false"));
    configuration.getServiceSchedulerConfig().setAutoStart(System.getProperty("setupScheduler", "false"));

    configuration.setExecutorsConfig(
        ExecutorsConfig.builder().dataReconciliationExecutorConfig(ExecutorConfig.builder().build()).build());

    configuration.setGrpcDelegateServiceClientConfig(
        GrpcClientConfig.builder().target("localhost:9880").authority("localhost").build());
    configuration.setGrpcClientConfig(
        GrpcClientConfig.builder().target("localhost:9880").authority("localhost").build());

    configuration.setGrpcDMSClientConfig(
        GrpcClientConfig.builder().target("localhost:15011").authority("localhost").build());
    configuration.setDmsSecret("dummy_key");

    configuration.setLogStreamingServiceConfig(
        LogStreamingServiceConfig.builder().baseUrl("http://localhost:8079").serviceToken("token").build());

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

    MarketoConfig marketoConfig =
        MarketoConfig.builder().clientId("client_id").clientSecret("client_secret_id").enabled(false).build();
    configuration.setMarketoConfig(marketoConfig);

    SegmentConfig segmentConfig = SegmentConfig.builder().enabled(false).url("dummy_url").apiKey("dummy_key").build();
    configuration.setSegmentConfig(segmentConfig);
    configuration.setEventsFrameworkConfiguration(
        EventsFrameworkConfiguration.builder()
            .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
            .build());
    configuration.setFileStorageMode(DataStorageMode.MONGO);
    configuration.setClusterName("");
    configuration.setTimeScaleDBConfig(TimeScaleDBConfig.builder().build());
    configuration.setCfClientConfig(CfClientConfig.builder().build());
    configuration.setCfMigrationConfig(CfMigrationConfig.builder()
                                           .account("testAccount")
                                           .enabled(false)
                                           .environment("testEnv")
                                           .org("testOrg")
                                           .project("testProject")
                                           .build());
    configuration.setLdapSyncJobConfig(
        LdapSyncJobConfig.builder().defaultCronExpression("0 0 23 ? * SAT *").poolSize(3).syncInterval(15).build());
    SegmentConfiguration segmentConfiguration = SegmentConfiguration.builder()
                                                    .enabled(false)
                                                    .url("dummy_url")
                                                    .apiKey("dummy_key")
                                                    .certValidationRequired(false)
                                                    .build();
    configuration.setSegmentConfiguration(segmentConfiguration);

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

    return configuration;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(mongoTypeModule(annotations));

    modules.add(VersionModule.getInstance());
    modules.add(TimeModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceTestModule());
    modules.add(KryoModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(ManagerRegistrars.kryoRegistrars)
            .add(TestManagerKryoRegistrar.class)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(ManagerRegistrars.morphiaRegistrars)
            .build();
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

    MainConfiguration configuration = getConfiguration("graphQL");

    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(0, cacheModule);

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(BroadcasterFactory.class).toInstance(mock(BroadcasterFactory.class));
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

    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    modules.add(new ValidationModule(validatorFactory));
    modules.add(new DelegateServiceModule());
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
    modules.add(new WingsModule(configuration, StartupMode.MANAGER));
    modules.add(new SimpleTotpModule());
    modules.add(new IndexMigratorModule());
    modules.add(new YamlModule());
    modules.add(new ManagerQueueModule());
    modules.add(new ManagerExecutorModule());
    modules.add(new TemplateModule());
    modules.add(new EventsModule(configuration));
    modules.add(GraphQLModule.getInstance());
    modules.add(new AuthModule());
    modules.add(new SSOModule());
    modules.add(new SignupModule());
    modules.add(new GcpMarketplaceIntegrationModule());
    return modules;
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }
}
