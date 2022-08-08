/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.AuthorizationServiceHeader;
import io.harness.EntityType;
import io.harness.SCMGrpcClientModule;
import io.harness.ScmConnectionConfig;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.gitsync.common.GitSyncEntityOrderComparatorInMsvc;
import io.harness.gitsync.entityInfo.GitSdkEntityHandlerInterface;
import io.harness.gitsync.persistance.GitAwareRepository;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.redis.RedisConfig;
import io.harness.version.VersionInfoManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import org.redisson.api.RedissonClient;

@OwnedBy(DX)
public abstract class AbstractGitSyncSdkModule extends AbstractModule {
  public static final String GIT_SYNC_SDK = "GitSyncSdk";
  @Override
  protected void configure() {
    install(new SCMGrpcClientModule(getScmConnectionConfig()));
    install(GitSyncSdkModule.getInstance());
    if (getGitSyncSdkConfiguration().getEventsRedisConfig().getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.GIT_CONFIG_STREAM + GIT_SYNC_SDK))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      RedisConfig redisConfig = getGitSyncSdkConfiguration().getEventsRedisConfig();
      RedissonClient redissonClient = RedisUtils.getClient(redisConfig);
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.GIT_CONFIG_STREAM + GIT_SYNC_SDK))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.GIT_CONFIG_STREAM,
              getGitSyncSdkConfiguration().getServiceHeader().getServiceId() + GIT_SYNC_SDK, redissonClient,
              EventsFrameworkConstants.GIT_CONFIG_STREAM_PROCESSING_TIME,
              EventsFrameworkConstants.GIT_CONFIG_STREAM_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
    }
    final Set<GitSyncEntitiesConfiguration> gitSyncEntitiesConfiguration =
        getGitSyncSdkConfiguration().getGitSyncEntitiesConfiguration();
    gitSyncEntitiesConfiguration.forEach(gitSyncEntitiesConfig -> {
      final Class<? extends GitSyncableEntity> entityClass = gitSyncEntitiesConfig.getEntityClass();
      Class<? extends GitSdkEntityHandlerInterface<?, ?>> entityHelperClass =
          gitSyncEntitiesConfig.getEntityHelperClass();

      final MapBinder<String, GitSdkEntityHandlerInterface> gitPersistenceHelperServiceMapBinder =
          MapBinder.newMapBinder(binder(), String.class, GitSdkEntityHandlerInterface.class);
      gitPersistenceHelperServiceMapBinder.addBinding(entityClass.getCanonicalName()).to(entityHelperClass);

      final MapBinder<EntityType, GitSdkEntityHandlerInterface> gitEntityTypeMapBinder =
          MapBinder.newMapBinder(binder(), EntityType.class, GitSdkEntityHandlerInterface.class);
      gitEntityTypeMapBinder.addBinding(gitSyncEntitiesConfig.getEntityType()).to(entityHelperClass);
    });
    bind(GitSyncEntityOrderComparatorInMsvc.class)
        .to(getGitSyncSdkConfiguration().getGitSyncEntitySortComparator())
        .in(Singleton.class);
  }

  <B extends GitSyncableEntity, Y extends YamlDTO> AnnotatedBindingBuilder<GitAwareRepository<B, Y>> bindGitRepository(
      Class<B> beanClass, Class<Y> yamlClass) {
    ParameterizedType type = Types.newParameterizedType(GitAwareRepository.class, yamlClass, beanClass);
    TypeLiteral<GitAwareRepository<B, Y>> typeLiteral = (TypeLiteral<GitAwareRepository<B, Y>>) TypeLiteral.get(type);
    return bind(typeLiteral);
  }

  public abstract GitSyncSdkConfiguration getGitSyncSdkConfiguration();

  @Provides
  @Singleton
  public ScmConnectionConfig getScmConnectionConfig() {
    return getGitSyncSdkConfiguration().getScmConnectionConfig();
  }

  @Provides
  @Singleton
  @Named("GitSdkAuthorizationServiceHeader")
  public AuthorizationServiceHeader getGitSdkAuthorizationServiceHeader() {
    return getGitSyncSdkConfiguration().getServiceHeader();
  }

  @Provides
  @Singleton
  public GitSyncSdkConfiguration gitSyncSdkConfiguration() {
    return getGitSyncSdkConfiguration();
  }

  @Provides
  @Singleton
  @Named("GitSyncGrpcClientConfig")
  public GrpcClientConfig grpcClientConfig() {
    return getGitSyncSdkConfiguration().getGrpcClientConfig();
  }

  @Provides
  @Singleton
  @Named("GitSyncSortOrder")
  public Supplier<List<EntityType>> getSortOrder() {
    return getGitSyncSdkConfiguration().getGitSyncSortOrder();
  }

  @Provides
  @Singleton
  @Named("git-msvc")
  public AuthorizationServiceHeader getAuthorizationServiceHeader() {
    return getGitSyncSdkConfiguration().getServiceHeader();
  }

  @Provides
  @Singleton
  @Named("GitSyncEntityConfigurations")
  public Map<EntityType, GitSyncEntitiesConfiguration> getGitSyncEntityConfigurations() {
    if (isEmpty(gitSyncSdkConfiguration().getGitSyncEntitiesConfiguration())) {
      return new HashMap<>();
    }
    return gitSyncSdkConfiguration().getGitSyncEntitiesConfiguration().stream().collect(
        Collectors.toMap(GitSyncEntitiesConfiguration::getEntityType, Function.identity()));
  }

  @Provides
  @Singleton
  @Named("GitSyncObjectMapper")
  public ObjectMapper getGitSyncObjectMapper() {
    return getGitSyncSdkConfiguration().getObjectMapper();
  }

  @Provides
  @Singleton
  @Named("gitSyncEnabledCache")
  public Cache<String, Boolean> gitEnabledCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("gitEnabledCacheSdk", String.class, Boolean.class,
        AccessedExpiryPolicy.factoryOf(Duration.FIVE_MINUTES), versionInfoManager.getVersionInfo().getBuildNo());
  }
}
