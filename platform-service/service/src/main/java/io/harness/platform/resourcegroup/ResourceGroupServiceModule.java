/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.RESOUCE_GROUP_SERVICE;
import static io.harness.lock.DistributedLockImplementation.MONGO;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import io.harness.AccessControlClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.eventsframework.impl.redis.monitoring.publisher.RedisEventMetricPublisher;
import io.harness.govern.ProviderModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.platform.PlatformConfiguration;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.resourcegroup.ResourceGroupModule;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupValidator;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupValidatorImpl;
import io.harness.resourcegroup.v1.remote.dto.ZendeskConfig;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.morphia.ResourceGroupSerializer;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.token.TokenClientModule;
import io.harness.version.VersionModule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.redisson.api.RedissonClient;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(PL)
public class ResourceGroupServiceModule extends AbstractModule {
  private static final String RESOURCE_GROUP_CONSUMER_GROUP = "resource-group";
  PlatformConfiguration appConfig;

  public ResourceGroupServiceModule(PlatformConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  public void configure() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(ResourceGroupSerializer.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(ResourceGroupSerializer.morphiaRegistrars)
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
        return appConfig.getResoureGroupServiceConfig().getMongoConfig();
      }
    });
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    install(ExecutorModule.getInstance());
    bind(PlatformConfiguration.class).toInstance(appConfig);
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(ResourceGroupValidator.class).to(ResourceGroupValidatorImpl.class);
    install(VersionModule.getInstance());
    bind(QueueController.class).toInstance(new QueueController() {
      @Override
      public boolean isPrimary() {
        return true;
      }

      @Override
      public boolean isNotPrimary() {
        return false;
      }
    });
    install(new ValidationModule(getValidatorFactory()));
    install(new ResourceGroupPersistenceModule());
    install(PersistentLockModule.getInstance());
    install(TimeModule.getInstance());
    install(new ResourceGroupModule(appConfig.getResoureGroupServiceConfig()));
    install(new AuditClientModule(this.appConfig.getResoureGroupServiceConfig().getAuditClientConfig(),
        this.appConfig.getPlatformSecrets().getNgManagerServiceSecret(), RESOUCE_GROUP_SERVICE.getServiceId(),
        this.appConfig.getResoureGroupServiceConfig().isEnableAudit()));
    install(AccessControlClientModule.getInstance(
        this.appConfig.getAccessControlClientConfig(), RESOUCE_GROUP_SERVICE.getServiceId()));

    install(EnforcementClientModule.getInstance(appConfig.getNgManagerServiceConfig(),
        appConfig.getPlatformSecrets().getNgManagerServiceSecret(), RESOUCE_GROUP_SERVICE.getServiceId(),
        appConfig.getEnforcementClientConfiguration()));

    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, RESOUCE_GROUP_SERVICE.getServiceId(),
        appConfig.getResoureGroupServiceConfig().isExportMetricsToStackDriver()));

    install(new TokenClientModule(this.appConfig.getRbacServiceConfig(),
        this.appConfig.getPlatformSecrets().getNgManagerServiceSecret(), RESOUCE_GROUP_SERVICE.getServiceId()));
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return appConfig.getResoureGroupServiceConfig().getDistributedLockImplementation() == null
        ? MONGO
        : appConfig.getResoureGroupServiceConfig().getDistributedLockImplementation();
  }

  @Provides
  @Named("zendeskApiConfiguration")
  @Singleton
  ZendeskConfig getZendeskConfig() {
    return appConfig.getZendeskConfig();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisLockConfig() {
    return appConfig.getResoureGroupServiceConfig().getRedisLockConfig();
  }

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

  private ValidatorFactory getValidatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .parameterNameProvider(new ReflectionParameterNameProvider())
        .buildValidatorFactory();
  }

  @Provides
  @Named("eventsFrameworkRedissonClient")
  @Singleton
  public RedissonClient getRedissonClient() {
    RedisConfig redisConfig = appConfig.getResoureGroupServiceConfig().getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      return null;
    }
    return RedissonClientFactory.getClient(redisConfig);
  }

  @Provides
  @Named(EventsFrameworkConstants.ENTITY_CRUD)
  Producer getProducer(@Nullable @Named("eventsFrameworkRedissonClient") RedissonClient redissonClient) {
    RedisConfig redisConfig = appConfig.getResoureGroupServiceConfig().getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      return NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME);
    }
    return RedisProducer.of(EventsFrameworkConstants.ENTITY_CRUD, redissonClient,
        EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, RESOUCE_GROUP_SERVICE.getServiceId(),
        redisConfig.getEnvNamespace());
  }

  @Provides
  @Named(EventsFrameworkConstants.ENTITY_CRUD)
  Consumer getConsumer(@Nullable @Named("eventsFrameworkRedissonClient") RedissonClient redissonClient,
      RedisEventMetricPublisher redisEventMetricPublisher) {
    RedisConfig redisConfig = appConfig.getResoureGroupServiceConfig().getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      return NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME);
    }
    return RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, RESOURCE_GROUP_CONSUMER_GROUP, redissonClient,
        EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME, EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE,
        redisConfig.getEnvNamespace(), redisEventMetricPublisher);
  }
}
