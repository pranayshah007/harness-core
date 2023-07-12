package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateActivity;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.utils.DelegateLogContextHelper;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DMSAssignDelegateService;
import io.harness.service.intfc.DelegateCache;

import software.wings.delegatetasks.validation.core.DelegateConnectionResult;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Singleton
@Slf4j
public class DMSAssignDelegateServiceImpl implements DMSAssignDelegateService {
  @Inject private HPersistence persistence;

  @Inject private DelegateCache delegateCache;

  public static final long WHITELIST_TTL = TimeUnit.HOURS.toMillis(6);
  public static final long BLACKLIST_TTL = TimeUnit.MINUTES.toMillis(5);

  public static final long MAX_DELEGATE_LAST_HEARTBEAT = (5 * 60 * 1000L) + (15 * 1000L); // 5 minutes 15 seconds

  private LoadingCache<ImmutablePair<String, String>, Optional<DelegateConnectionResult>>
      delegateConnectionResultCache =
          CacheBuilder.newBuilder()
              .maximumSize(10000)
              .expireAfterWrite(2, TimeUnit.MINUTES)
              .build(new CacheLoader<ImmutablePair<String, String>, Optional<DelegateConnectionResult>>() {
                @Override
                public Optional<DelegateConnectionResult> load(ImmutablePair<String, String> key) {
                  return Optional.ofNullable(
                      persistence.createQuery(DelegateConnectionResult.class)
                          .filter(DelegateConnectionResult.DelegateConnectionResultKeys.delegateId, key.getLeft())
                          .filter(DelegateConnectionResult.DelegateConnectionResultKeys.criteria, key.getRight())
                          .get());
                }
              });

  private LoadingCache<String, List<Delegate>> accountDelegatesCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(MAX_DELEGATE_LAST_HEARTBEAT / 3, TimeUnit.MILLISECONDS)
          .build(new CacheLoader<String, List<Delegate>>() {
            @Override
            public List<Delegate> load(String accountId) {
              return persistence.createQuery(Delegate.class)
                  .filter(Delegate.DelegateKeys.accountId, accountId)
                  .field(Delegate.DelegateKeys.status)
                  .notEqual(DelegateInstanceStatus.DELETED)
                  .project(Delegate.DelegateKeys.uuid, true)
                  .project(Delegate.DelegateKeys.lastHeartBeat, true)
                  .project(Delegate.DelegateKeys.status, true)
                  .project(Delegate.DelegateKeys.delegateGroupName, true)
                  .project(Delegate.DelegateKeys.delegateGroupId, true)
                  .project(Delegate.DelegateKeys.owner, true)
                  .project(Delegate.DelegateKeys.ng, true)
                  .asList();
            }
          });

  @Override
  public boolean shouldValidate(DelegateTask task, String delegateId) {
    try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(task)) {
      for (String criteria : fetchCriteria(task)) {
        if (isNotBlank(criteria)) {
          Optional<DelegateConnectionResult> result =
              delegateConnectionResultCache.get(ImmutablePair.of(delegateId, criteria));
          if (shouldValidateCriteria(result, currentTimeMillis())
              || (!retrieveActiveDelegates(task.getAccountId(), null).contains(delegateId)
                  && isEmpty(connectedWhitelistedDelegates(task)))) {
            return true;
          }
        } else {
          log.error("We should not have blank criteria");
          return true;
        }
      }
    } catch (Exception e) {
      log.error("Error checking whether delegate should validate task", e);
    }
    return false;
  }

  protected List<String> fetchCriteria(DelegateTask task) {
    if (isEmpty(task.getExecutionCapabilities())) {
      return emptyList();
    }

    return task.getExecutionCapabilities()
        .stream()
        .filter(e -> e.evaluationMode() == ExecutionCapability.EvaluationMode.AGENT)
        .map(ExecutionCapability::fetchCapabilityBasis)
        .collect(toList());
  }

  public static boolean shouldValidateCriteria(Optional<DelegateConnectionResult> result, long now) {
    if (!result.isPresent()) {
      return true;
    }

    long delay = now - result.get().getLastUpdatedAt();
    if (result.get().isValidated() && delay > WHITELIST_TTL) {
      return true;
    }

    if (!result.get().isValidated() && delay > BLACKLIST_TTL) {
      return true;
    }

    return false;
  }

  @Override
  public List<String> retrieveActiveDelegates(String accountId, DelegateTask task) {
    try {
      List<Delegate> accountDelegates = accountDelegatesCache.get(accountId);

      if (accountDelegates.isEmpty()) {
        /* Cache invalidation was added here in order to cover the edge case, when there are no delegates in db for
         * the given account, so that the cache has an opportunity to refresh on a next invocation, instead of waiting
         * for the whole cache validity period to pass and returning empty list.
         * */
        accountDelegatesCache.invalidate(accountId);
      }

      if (task != null) {
        boolean isTaskNg =
            !isEmpty(task.getSetupAbstractions()) && Boolean.parseBoolean(task.getSetupAbstractions().get(NG));
        accountDelegates = accountDelegates.stream().filter(delegate -> delegate.isNg() == isTaskNg).collect(toList());
      }

      return identifyActiveDelegateIds(accountDelegates, accountId);
    } catch (ExecutionException ex) {
      log.error("Unexpected error occurred while fetching delegates from cache.", ex);
      return emptyList();
    }
  }

  @Override
  public List<String> connectedWhitelistedDelegates(DelegateTask task) {
    List<String> delegateIds = new ArrayList<>();

    try {
      List<String> connectedEligibleDelegates =
          retrieveActiveDelegates(task.getAccountId(), task)
              .stream()
              .filter(delegateId -> task.getEligibleToExecuteDelegateIds().contains(delegateId))
              .collect(toList());

      List<String> criteria = fetchCriteria(task);
      if (isEmpty(criteria)) {
        return connectedEligibleDelegates;
      }

      for (String delegateId : connectedEligibleDelegates) {
        boolean matching = true;
        for (String criterion : criteria) {
          Optional<DelegateConnectionResult> result =
              delegateConnectionResultCache.get(ImmutablePair.of(delegateId, criterion));
          if (!result.isPresent() || !result.get().isValidated()) {
            matching = false;
            break;
          }
        }
        if (matching) {
          delegateIds.add(delegateId);
        }
      }
    } catch (Exception e) {
      log.error("Error checking for whitelisted delegates", e);
    }
    return delegateIds;
  }

  private List<String> identifyActiveDelegateIds(List<Delegate> accountDelegates, String accountId) {
    long oldestAcceptableHeartBeat = currentTimeMillis() - MAX_DELEGATE_LAST_HEARTBEAT;

    Map<DelegateActivity, List<Delegate>> delegatesMap =
        accountDelegates.stream().collect(Collectors.groupingBy(delegate -> {
          if (DelegateInstanceStatus.ENABLED == delegate.getStatus()) {
            if (delegate.getLastHeartBeat() > oldestAcceptableHeartBeat) {
              return DelegateActivity.ACTIVE;
            } else {
              return DelegateActivity.DISCONNECTED;
            }
          } else if (DelegateInstanceStatus.WAITING_FOR_APPROVAL == delegate.getStatus()) {
            return DelegateActivity.WAITING_FOR_APPROVAL;
          }
          return DelegateActivity.OTHER;
        }, Collectors.toList()));

    return delegatesMap.get(DelegateActivity.ACTIVE) == null
        ? emptyList()
        : delegatesMap.get(DelegateActivity.ACTIVE).stream().map(Delegate::getUuid).collect(Collectors.toList());
  }

  @Override
  public boolean isWhitelisted(DelegateTask task, String delegateId) {
    try {
      boolean matching = true;
      for (String criteria : fetchCriteria(task)) {
        if (isNotBlank(criteria)) {
          Optional<DelegateConnectionResult> result =
              delegateConnectionResultCache.get(ImmutablePair.of(delegateId, criteria));
          if (!result.isPresent() || result.get().getLastUpdatedAt() < currentTimeMillis() - WHITELIST_TTL
              || !result.get().isValidated()) {
            matching = false;
            Delegate delegate = delegateCache.get(task.getAccountId(), delegateId, false);
            if (delegate == null) {
              break;
            }
            String delegateName =
                isNotEmpty(delegate.getDelegateName()) ? delegate.getDelegateName() : delegate.getUuid();
            String noMatchError = String.format("No matching criteria %s found in delegate %s", criteria, delegateName);
            addToTaskActivityLog(task, noMatchError);
            break;
          }
        }
      }
      return matching;
    } catch (Exception e) {
      log.error("Error checking whether delegate is whitelisted for task {}", task.getUuid(), e);
    }
    return false;
  }

  private void addToTaskActivityLog(DelegateTask task, String message) {
    if (task.getTaskActivityLogs() == null) {
      task.setTaskActivityLogs(Lists.newArrayList());
    }
    task.getTaskActivityLogs().add(message);
  }
}
