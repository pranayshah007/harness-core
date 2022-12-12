package io.harness.queueservice.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.Delegate;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.infc.DelegateResourceCriteria;
import io.harness.service.intfc.DelegateCache;

import software.wings.beans.TaskType;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class OrderByTotalNumberOfTaskAssignedCriteria implements DelegateResourceCriteria {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;

  PriorityQueue<Map.Entry<String, Integer>> orderedDelegatePq =
      new PriorityQueue<>(Comparator.comparingInt(Map.Entry::getValue));

  @Override
  public List<Delegate> getFilteredEligibleDelegateList(
      List<Delegate> delegateList, TaskType taskType, String accountId) {
    List<String> orderedList = listOfDelegatesSortedByNumberOfTaskAssignedFromLocalCache(accountId);
    return orderedList.stream()
        .map(delegateId -> getDelegateFromCache(delegateId, accountId))
        .collect(Collectors.toList());
  }

  public Delegate getDelegateFromCache(String delegateId, String accountId) {
    return delegateCache.get(accountId, delegateId, false);
  }

  public List<DelegateTask> getNumberOfTaskAssignedCache(String accountId) {
    return delegateCache.getCurrentlyAssignedTask(accountId);
  }

  private List<String> listOfDelegatesSortedByNumberOfTaskAssignedFromLocalCache(String accountId) {
    Map<String, Integer> numberOfTaskAssigned = new HashMap<>();
    getNumberOfTaskAssignedCache(accountId).forEach(delegateTask -> {
      numberOfTaskAssigned.putIfAbsent(
          delegateTask.getDelegateId(), numberOfTaskAssigned.getOrDefault(delegateTask.getDelegateId(), 0) + 1);
    });
    orderedDelegatePq.addAll(numberOfTaskAssigned.entrySet());
    return orderedDelegatePq.stream().map(Map.Entry::getKey).collect(Collectors.toList());
  }

  private Map<String, Integer> listOfDelegatesSortedByNumberOfTaskAssignedFromRedisCache(String accountId) {
    // TBD
    return null;
  }
}
