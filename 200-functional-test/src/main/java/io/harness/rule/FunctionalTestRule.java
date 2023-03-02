/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static io.harness.cache.CacheBackend.NOOP;

import static org.mockito.Mockito.mock;

import io.harness.AccessControlClientConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.GraphQLModule;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.commandlibrary.client.CommandLibraryServiceHttpClient;
import io.harness.configuration.ConfigurationType;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.delegate.beans.StartupMode;
import io.harness.event.EventsModule;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.ff.FeatureFlagConfig;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.GrpcServiceConfigurationModule;
import io.harness.grpc.client.AbstractManagerGrpcClientModule;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.client.ManagerGrpcClientModule;
import io.harness.grpc.server.Connector;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.logstreaming.LogStreamingServiceConfig;
import io.harness.module.DelegateServiceModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoModule;
import io.harness.mongo.ObjectFactoryModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.observer.NoOpRemoteObserverInformerImpl;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.consumer.AbstractRemoteObserverModule;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.QueryFactory;
import io.harness.persistence.UserProvider;
import io.harness.queueservice.config.DelegateQueueServiceConfig;
import io.harness.redis.RedisConfig;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rest.RestResponse;
import io.harness.scm.ScmSecret;
import io.harness.security.AsymmetricDecryptor;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.serializer.morphia.EventServerMorphiaRegistrar;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.springdata.SpringPersistenceModule;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.testframework.framework.ManagerExecutor;
import io.harness.testframework.framework.Setup;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.timescaledb.TimeScaleDBConfig;

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
import software.wings.app.SearchModule;
import software.wings.app.SignupModule;
import software.wings.app.TemplateModule;
import software.wings.app.WingsModule;
import software.wings.app.YamlModule;
import software.wings.graphql.provider.QueryLanguageProvider;
import software.wings.scheduler.LdapSyncJobConfig;
import software.wings.search.framework.ElasticsearchConfig;
import software.wings.service.impl.EventEmitter;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoClients;
import dev.morphia.AdvancedDatastore;
import dev.morphia.Morphia;
import dev.morphia.converters.TypeConverter;
import graphql.GraphQL;
import io.dropwizard.Configuration;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.ws.rs.core.GenericType;
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

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class FunctionalTestRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  private ClosingFactory closingFactory;

  public FunctionalTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  private ExecutorService executorService = new CurrentThreadExecutor();
  @Getter private GraphQL graphQL;

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ManagerExecutor.ensureManager(AbstractFunctionalTest.class);

    RestResponse<MongoConfig> mongoConfigRestResponse =
        Setup.portal()
            .queryParam("configurationType", ConfigurationType.MONGO)
            .get("/health/configuration")
            .as(new GenericType<RestResponse<MongoConfig>>() {}.getType());

    String mongoUri =
        new AsymmetricDecryptor(new ScmSecret()).decryptText(mongoConfigRestResponse.getResource().getEncryptedUri());

    MongoConfig mongoConfig = MongoConfig.builder().build();
    MongoClientURI clientUri =
        new MongoClientURI(mongoUri, MongoClientOptions.builder(MongoModule.getDefaultMongoClientOptions(mongoConfig)));
    String dbName = clientUri.getDatabase();

    MongoClient mongoClient = new MongoClient(clientUri);
    closingFactory.addServer(mongoClient);

    com.mongodb.client.MongoClient newMongoClient = MongoClients.create(mongoUri);

    RestResponse<ElasticsearchConfig> elasticsearchConfigRestResponse =
        Setup.portal()
            .queryParam("configurationType", ConfigurationType.ELASTICSEARCH)
            .get("/health/configuration")
            .as(new GenericType<RestResponse<ElasticsearchConfig>>() {}.getType());

    String elasticsearchUri = new AsymmetricDecryptor(new ScmSecret())
                                  .decryptText(elasticsearchConfigRestResponse.getResource().getEncryptedUri());
    String elasticsearchIndexSuffix = elasticsearchConfigRestResponse.getResource().getIndexSuffix();
    ElasticsearchConfig elasticsearchConfig =
        ElasticsearchConfig.builder().uri(elasticsearchUri).indexSuffix(elasticsearchIndexSuffix).build();

    RestResponse<Boolean> searchEnabledRestResponse =
        Setup.portal()
            .queryParam("configurationType", ConfigurationType.SEARCH_ENABLED)
            .get("/health/configuration")
            .as(new GenericType<RestResponse<Boolean>>() {}.getType());

    boolean isSearchEnabled = searchEnabledRestResponse.getResource();

    Configuration configuration = getConfiguration(mongoUri, elasticsearchConfig, isSearchEnabled);

    io.harness.threading.ExecutorModule.getInstance().setExecutorService(executorService);

    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));

    modules.add(new KryoModule());
    modules.add(ObjectFactoryModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().addAll(ManagerRegistrars.kryoRegistrars).build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(ManagerRegistrars.morphiaRegistrars)
            .add(EventServerMorphiaRegistrar.class)
            .build();
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "!!!custom_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "!!!custom_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "!!!custom_delegateTaskProgressResponses")
            .build();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return Collections.EMPTY_LIST;
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
      @Override
      public void configure() {
        install(new SpringPersistenceModule());
      }

      @Provides
      @Named("locksDatabase")
      @Singleton
      String databaseNameProvider() {
        return dbName;
      }

      @Provides
      @Named("locksMongoClient")
      @Singleton
      public MongoClient locksMongoClient(ClosingFactory closingFactory) {
        return mongoClient;
      }

      @Provides
      @Named("primaryDatastore")
      @Singleton
      AdvancedDatastore datastore(Morphia morphia) {
        AdvancedDatastore datastore = (AdvancedDatastore) morphia.createDatastore(mongoClient, dbName);
        MongoConfig mongoConfig = MongoConfig.builder().build();
        datastore.setQueryFactory(
            new QueryFactory(mongoConfig.getTraceMode(), mongoConfig.getMaxOperationTimeInMillis()));
        return datastore;
      }

      @Provides
      @Named("primaryMongoClient")
      @Singleton
      MongoClient mongoClient() {
        return mongoClient;
      }

      @Provides
      @Named("primaryMongoClient")
      @Singleton
      com.mongodb.client.MongoClient newMongoClient() {
        return newMongoClient;
      }

      @Provides
      @Singleton
      @Nullable
      UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });

    CacheModule cacheModule = new CacheModule(CacheConfig.builder()
                                                  .cacheBackend(NOOP)
                                                  .disabledCaches(new HashSet<>())
                                                  .cacheNamespace("harness-cache")
                                                  .build());
    modules.add(0, cacheModule);
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
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(EventEmitter.class).toInstance(mock(EventEmitter.class));
        bind(BroadcasterFactory.class).toInstance(mock(BroadcasterFactory.class));
        bind(MetricRegistry.class);
        bind(CommandLibraryServiceHttpClient.class).toInstance(mock(CommandLibraryServiceHttpClient.class));
        bind(DelegateCache.class).to(DelegateCacheImpl.class).in(Singleton.class);
      }
    });
    modules.add(new ValidationModule(validatorFactory));
    modules.add(new DelegateServiceModule());
    modules.add(new WingsModule((MainConfiguration) configuration, StartupMode.MANAGER));
    modules.add(new TestTotpModule());
    modules.add(new IndexMigratorModule());
    modules.add(new YamlModule());
    modules.add(new ManagerExecutorModule());
    modules.add(new TemplateModule());
    modules.add(new EventsModule((MainConfiguration) configuration));
    modules.add(GraphQLModule.getInstance());
    modules.add(new SSOModule());
    modules.add(new SignupModule());
    modules.add(new SearchModule());
    modules.add(new GcpMarketplaceIntegrationModule());
    modules.add(new AuthModule());
    modules.add(new ManagerQueueModule());
    modules.add(new AbstractManagerGrpcClientModule() {
      @Override
      public ManagerGrpcClientModule.Config config() {
        return ManagerGrpcClientModule.Config.builder().target("localhost:9880").authority("localhost").build();
      }

      @Override
      public String application() {
        return "Manager";
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
    modules.add(new GrpcServiceConfigurationModule(((MainConfiguration) configuration).getGrpcServerConfig(),
        ((MainConfiguration) configuration).getPortal().getJwtNextGenManagerSecret()));
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

    return modules;
  }

  protected Configuration getConfiguration(
      String mongoUri, ElasticsearchConfig elasticsearchConfig, boolean isSearchEnabled) {
    MainConfiguration configuration = new MainConfiguration();
    configuration.getPortal().setCompanyName("COMPANY_NAME");
    configuration.getPortal().setUrl("PORTAL_URL");
    configuration.getPortal().setVerificationUrl("VERIFICATION_PATH");
    configuration.getPortal().setJwtNextGenManagerSecret(
        "IC04LYMBf1lDP5oeY4hupxd4HJhLmN6azUku3xEbeE3SUx5G3ZYzhbiwVtK4i7AmqyU9OZkwB4v8E9qM");
    GrpcServerConfig grpcServerConfig = new GrpcServerConfig();
    grpcServerConfig.setConnectors(Arrays.asList(
        Connector.builder().port(9880).secure(true).keyFilePath("key.pem").certFilePath("cert.pem").build()));
    configuration.setGrpcServerConfig(grpcServerConfig);

    configuration.setExecutorsConfig(
        ExecutorsConfig.builder().dataReconciliationExecutorConfig(ExecutorConfig.builder().build()).build());

    configuration.setGrpcDelegateServiceClientConfig(
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

    configuration.setMongoConnectionFactory(MongoConfig.builder().uri(mongoUri).build());
    configuration.setElasticsearchConfig(elasticsearchConfig);
    configuration.setSearchEnabled(isSearchEnabled);
    configuration.setSegmentConfig(
        SegmentConfig.builder().enabled(false).apiKey("dummy_api_key").url("dummy_url").build());
    configuration.setNgManagerServiceHttpClientConfig(
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build());
    configuration.getBackgroundSchedulerConfig().setAutoStart(System.getProperty("setupScheduler", "false"));
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
    configuration.setSegmentConfiguration(SegmentConfiguration.builder()
                                              .enabled(false)
                                              .apiKey("dummy_api_key")
                                              .url("dummy_url")
                                              .certValidationRequired(false)
                                              .build());
    configuration.setQueueServiceConfig(DelegateQueueServiceConfig.builder()
                                            .queueServiceConfig(ServiceHttpClientConfig.builder()
                                                                    .baseUrl("http://localhost:9091/")
                                                                    .readTimeOutSeconds(15)
                                                                    .connectTimeOutSeconds(15)
                                                                    .build())
                                            .topic("delegate-service")
                                            .build());
    return configuration;
  }

  @Override
  public void initialize(Injector injector, List<Module> modules) {
    System.setProperty("javax.cache.spi.CachingProvider", "com.hazelcast.cache.HazelcastCachingProvider");
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
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }
}
