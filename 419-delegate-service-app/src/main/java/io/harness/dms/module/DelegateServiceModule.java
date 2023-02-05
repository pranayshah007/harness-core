package io.harness.dms.module;

import io.harness.cache.CacheModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.dms.configuration.DelegateServiceConfiguration;
import io.harness.govern.ProviderModule;
import io.harness.metrics.modules.MetricsModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.serializer.DelegateServiceRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.threading.ExecutorModule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import java.util.Map;
import java.util.Set;

public class DelegateServiceModule extends AbstractModule {
  private final DelegateServiceConfiguration config;

  public DelegateServiceModule(DelegateServiceConfiguration config) {
    this.config = config;
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    // this is needed because DelegateSyncTaskResponse and others need custom names.
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "delegateTaskProgressResponses")
        .build();
  }

  @Override
  protected void configure() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(DelegateServiceRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(DelegateServiceRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return config.getMongoConfig();
      }
    });
    install(new MetricsModule());
    install(ExecutorModule.getInstance());
    bind(DelegateServiceConfiguration.class).toInstance(config);
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    bind(HPersistence.class).to(MongoPersistence.class);

    install(new CacheModule(config.getCacheConfig()));
  }
}