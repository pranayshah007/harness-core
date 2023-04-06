/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.utils.DelegateServiceConstants.HEARTBEAT_EXPIRY_TIME_FIVE_MINS;
import static io.harness.serializer.DelegateServiceCacheRegistrar.ABORTED_TASK_LIST_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATES_FROM_GROUP_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_GROUP_CACHE;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileKeys;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.persistence.HPersistence;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.service.intfc.DelegateCache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.redisson.api.RLocalCachedMap;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateCacheImpl implements DelegateCache {
  private static final int MAX_DELEGATE_META_INFO_ENTRIES = 10000;

  @Inject private HPersistence persistence;
  @Inject private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @Inject @Named(DELEGATE_CACHE) RLocalCachedMap<String, Delegate> delegateRedisCache;
  @Inject @Named(DELEGATE_GROUP_CACHE) RLocalCachedMap<String, DelegateGroup> delegateGroupRedisCache;
  @Inject @Named(DELEGATES_FROM_GROUP_CACHE) RLocalCachedMap<String, List<Delegate>> delegatesFromGroupRedisCache;
  @Inject @Named(ABORTED_TASK_LIST_CACHE) RLocalCachedMap<String, Set<String>> abortedTaskListCache;

  @Inject @Named("enableRedisForDelegateService") private boolean enableRedisForDelegateService;

  @Inject DelegateRedissonCacheManager delegateRedissonCacheManager;

  private LoadingCache<String, Optional<Delegate>> delegateCache =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_DELEGATE_META_INFO_ENTRIES)
          .expireAfterWrite(1, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Optional<Delegate>>() {
            @Override
            public Optional<Delegate> load(String delegateId) {
              return Optional.ofNullable(
                  persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegateId).get());
            }
          });

  private LoadingCache<ImmutablePair<String, String>, DelegateGroup> delegateGroupCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(30, TimeUnit.SECONDS)
          .build(new CacheLoader<ImmutablePair<String, String>, DelegateGroup>() {
            @Override
            public DelegateGroup load(ImmutablePair<String, String> delegateGroupKey) {
              return persistence.createQuery(DelegateGroup.class)
                  .filter(DelegateGroupKeys.accountId, delegateGroupKey.getLeft())
                  .filter(DelegateGroupKeys.uuid, delegateGroupKey.getRight())
                  .get();
            }
          });

  private LoadingCache<ImmutablePair<String, String>, DelegateProfile> delegateProfilesCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(30, TimeUnit.SECONDS)
          .build(new CacheLoader<ImmutablePair<String, String>, DelegateProfile>() {
            @Override
            public DelegateProfile load(ImmutablePair<String, String> delegateProfileKey) {
              return persistence.createQuery(DelegateProfile.class)
                  .filter(DelegateProfileKeys.accountId, delegateProfileKey.getLeft())
                  .filter(DelegateProfileKeys.uuid, delegateProfileKey.getRight())
                  .get();
            }
          });

  private LoadingCache<ImmutablePair<String, String>, List<Delegate>> delegatesFromGroupCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(new CacheLoader<ImmutablePair<String, String>, List<Delegate>>() {
            @Override
            public List<Delegate> load(ImmutablePair<String, String> delegateGroupKey) {
              return persistence.createQuery(Delegate.class)
                  .filter(DelegateKeys.accountId, delegateGroupKey.getLeft())
                  .filter(DelegateKeys.ng, true)
                  .filter(DelegateKeys.delegateGroupId, delegateGroupKey.getRight())
                  .asList();
            }
          });

  private LoadingCache<String, Set<String>> activeDelegateSupportedTaskTypesCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(10, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Set<String>>() {
            @Override
            public Set<String> load(@NotNull String accountId) {
              return getIntersectionOfSupportedTaskTypes(accountId);
            }
          });

  private LoadingCache<String, Long> optionalDelegateTasksCountCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(1, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Long>() {
            @Override
            public Long load(@NotNull String accountId) {
              return populateDelegateTaskCount(accountId, DelegateTaskRank.OPTIONAL);
            }
          });

  private LoadingCache<String, Long> importantDelegateTasksCountCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(1, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Long>() {
            @Override
            public Long load(@NotNull String accountId) {
              return populateDelegateTaskCount(accountId, DelegateTaskRank.IMPORTANT);
            }
          });

  @Override
  public Delegate get(String accountId, String delegateId, boolean forceRefresh) {
    try {
      if (enableRedisForDelegateService) {
        return getDelegateFromRedisCache(delegateId, forceRefresh);
      }

      if (forceRefresh) {
        delegateCache.refresh(delegateId);
      }
      Delegate delegate = delegateCache.get(delegateId).orElse(null);
      if (delegate != null && (delegate.getAccountId() == null || !delegate.getAccountId().equals(accountId))) {
        // TODO: this is serious, we should not return the delegate if the account is not the expected one
        //       just to be on the safe side, make sure that all such scenarios are first fixed
        log.error("Delegate account id mismatch", new Exception(""));
      }
      return delegate;
    } catch (ExecutionException e) {
      log.error("Execution exception", e);
    } catch (UncheckedExecutionException e) {
      log.error("Delegate not found exception", e);
    }
    return null;
  }

  // only for task assignment logic we should fetch from cache, since we process very heavy number of tasks per minute.
  @Override
  public DelegateGroup getDelegateGroup(String accountId, String delegateGroupId) {
    if (isBlank(accountId) || isBlank(delegateGroupId)) {
      return null;
    }

    try {
      if (enableRedisForDelegateService) {
        return getDelegateGroupRedisCache(accountId, delegateGroupId);
      }
      return delegateGroupCache.get(ImmutablePair.of(accountId, delegateGroupId));
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      return null;
    }
  }

  @Override
  public DelegateProfile getDelegateProfile(String accountId, String delegateProfileId) {
    if (StringUtils.isBlank(delegateProfileId)) {
      return null;
    }

    try {
      return delegateProfilesCache.get(ImmutablePair.of(accountId, delegateProfileId));
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      return null;
    }
  }

  @Override
  public void invalidateDelegateProfileCache(String accountId, String delegateProfileId) {
    delegateProfilesCache.invalidate(ImmutablePair.of(accountId, delegateProfileId));
  }

  @Override
  public List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId) {
    if (isBlank(accountId) || isBlank(delegateGroupId)) {
      return null;
    }
    try {
      if (enableRedisForDelegateService) {
        return getDelegatesForGroupRedisCache(accountId, delegateGroupId);
      }
      return delegatesFromGroupCache.get(ImmutablePair.of(accountId, delegateGroupId));
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      log.warn("Unable to getDelegates from cache based on group id");
      return null;
    }
  }

  @Override
  public Set<String> getDelegateSupportedTaskTypes(@NotNull String accountId) {
    try {
      return activeDelegateSupportedTaskTypesCache.get(accountId);
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      log.warn("Unable to get supported task types from cache based on account id");
      return null;
    }
  }

  @Override
  public long getTasksCount(@NotNull String accountId, @NotNull DelegateTaskRank rank) {
    try {
      if (rank == DelegateTaskRank.OPTIONAL) {
        return optionalDelegateTasksCountCache.get(accountId);
      } else if (rank == DelegateTaskRank.IMPORTANT) {
        return importantDelegateTasksCountCache.get(accountId);
      }
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      log.warn("Unable to get count of optional delegate tasks from cache based on accountId.");
      return 0;
    }
    throw new InvalidArgumentsException("Unsupported delegate task rank " + rank);
  }

  @Override
  public Map<String, Long> getTasksCountPerAccount(@NotNull DelegateTaskRank rank) {
    if (rank == DelegateTaskRank.OPTIONAL) {
      return optionalDelegateTasksCountCache.asMap();
    } else if (rank == DelegateTaskRank.IMPORTANT) {
      return importantDelegateTasksCountCache.asMap();
    }
    throw new InvalidArgumentsException("Unsupported delegate task rank " + rank);
  }

  @Override
  public Set<String> getAbortedTaskList(String accountId) {
    if (!enableRedisForDelegateService) {
      log.info("enableRedisForDelegateService flag is false");
      return Collections.emptySet();
    }
    return abortedTaskListCache.get(accountId) != null ? abortedTaskListCache.get(accountId) : Collections.emptySet();
  }

  @Override
  public void addToAbortedTaskList(String accountId, Set<String> abortedTaskList) {
    if (!enableRedisForDelegateService) {
      log.info("enableRedisForDelegateService flag is false");
      return;
    }
    abortedTaskListCache.putIfAbsent(accountId, abortedTaskList);
  }

  @Override
  public void removeFromAbortedTaskList(String accountId, String delegateTaskId) {
    if (!enableRedisForDelegateService) {
      log.info("enableRedisForDelegateService flag is false");
      return;
    }
    if (abortedTaskListCache.get(accountId) != null) {
      abortedTaskListCache.get(accountId).remove(delegateTaskId);
    }
  }

  private Set<String> getIntersectionOfSupportedTaskTypes(@NotNull String accountId) {
    List<Delegate> delegateList = getActiveDelegates(accountId);
    Set<String> supportedTaskTypes = new HashSet<>();
    if (isNotEmpty(delegateList)) {
      supportedTaskTypes = new HashSet<>(delegateList.get(0).getSupportedTaskTypes());
    }
    for (Delegate delegate : delegateList) {
      supportedTaskTypes = Sets.intersection(supportedTaskTypes, new HashSet<>(delegate.getSupportedTaskTypes()));
    }
    return supportedTaskTypes;
  }

  private List<Delegate> getActiveDelegates(String accountId) {
    return persistence.createQuery(Delegate.class)
        .filter(DelegateKeys.accountId, accountId)
        .field(DelegateKeys.lastHeartBeat)
        .greaterThan(System.currentTimeMillis() - HEARTBEAT_EXPIRY_TIME_FIVE_MINS.toMillis())
        .project(DelegateKeys.uuid, true)
        .project(DelegateKeys.accountId, true)
        .project(DelegateKeys.supportedTaskTypes, true)
        .asList();
  }

  private Long populateDelegateTaskCount(String accountId, DelegateTaskRank rank) {
    long count = getDelegateTaskCount(accountId, rank, false);

    if (delegateTaskMigrationHelper.isDelegateTaskMigrationEnabled()) {
      count += getDelegateTaskCount(accountId, rank, true);
    }
    return count;
  }

  private long getDelegateTaskCount(String accountId, DelegateTaskRank rank, boolean isDelegateTaskMigrationEnabled) {
    return persistence.createQuery(DelegateTask.class, isDelegateTaskMigrationEnabled)
        .filter(DelegateTaskKeys.accountId, accountId)
        .filter(DelegateTaskKeys.rank, rank)
        .count();
  }

  private Delegate getDelegateFromRedisCache(String delegateId, boolean forceRefresh) {
    if (delegateRedisCache.get(delegateId) == null || forceRefresh) {
      Delegate delegate = persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegateId).get();
      if (delegate == null) {
        log.warn("Unable to find delegate {} in DB.", delegateId);
        return null;
      }
      delegateRedisCache.put(delegateId, delegate);
    }
    return delegateRedisCache.get(delegateId);
  }

  private DelegateGroup getDelegateGroupRedisCache(String accountId, String delegateGroupId) {
    if (delegateGroupRedisCache.get(delegateGroupId) == null) {
      DelegateGroup delegateGroup = persistence.createQuery(DelegateGroup.class)
                                        .filter(DelegateGroupKeys.accountId, accountId)
                                        .filter(DelegateGroupKeys.uuid, delegateGroupId)
                                        .get();
      delegateGroupRedisCache.put(delegateGroupId, delegateGroup);
    }
    return delegateGroupRedisCache.get(delegateGroupId);
  }

  private List<Delegate> getDelegatesForGroupRedisCache(String accountId, String delegateGroupId) {
    if (delegatesFromGroupRedisCache.get(delegateGroupId) == null) {
      List<Delegate> delegateList = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, accountId)
                                        .filter(DelegateKeys.ng, true)
                                        .filter(DelegateKeys.delegateGroupId, delegateGroupId)
                                        .asList();
      delegatesFromGroupRedisCache.put(delegateGroupId, delegateList);
    }
    return delegatesFromGroupRedisCache.get(delegateGroupId);
  }
}
