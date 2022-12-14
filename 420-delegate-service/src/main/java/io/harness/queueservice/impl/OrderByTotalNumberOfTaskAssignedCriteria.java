package io.harness.queueservice.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.infc.DelegateResourceCriteria;
import io.harness.service.intfc.DelegateCache;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class OrderByTotalNumberOfTaskAssignedCriteria implements DelegateResourceCriteria {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;

  @Inject
  public OrderByTotalNumberOfTaskAssignedCriteria(HPersistence persistence, DelegateCache delegateCache) {
    this.persistence = persistence;
    this.delegateCache = delegateCache;
  }

  @Override
  public List<Delegate> getFilteredEligibleDelegateList(
      List<Delegate> delegateList, TaskType taskType, String accountId) {
    return listOfDelegatesSortedByNumberOfTaskAssignedFromLocalCache(accountId);
  }

  private List<Delegate> listOfDelegatesSortedByNumberOfTaskAssignedFromLocalCache(String accountId) {
    Map<String, Integer> numberOfTaskAssigned = new HashMap<>();
    getNumberOfTaskAssignedCache(accountId).forEach(delegateTask -> {
      if (delegateTask.getDelegateId() != null) {
        numberOfTaskAssigned.put(
            delegateTask.getDelegateId(), numberOfTaskAssigned.getOrDefault(delegateTask.getDelegateId(), 0) + 1);
      }
    });
    PriorityQueue<Map.Entry<String, Integer>> orderedDelegatePq =
        new PriorityQueue<>(Map.Entry.comparingByValue(Comparator.naturalOrder()));
    orderedDelegatePq.addAll(numberOfTaskAssigned.entrySet());
    return Stream.generate(orderedDelegatePq::poll)
        .filter(Objects::nonNull)
        .map(entry -> updateDelegateWithNumberTaskAssigned(entry, accountId))
        .limit(orderedDelegatePq.size())
        .collect(Collectors.toList());
  }

  private Delegate updateDelegateWithNumberTaskAssigned(Map.Entry<String, Integer> entry, String accountId) {
    Delegate delegate = getDelegateFromCache(entry.getKey(), accountId);
    if (delegate == null) {
      return null;
    }
    delegate.setNumberOfTaskAssigned(entry.getValue());
    return delegate;
  }

  public Delegate getDelegateFromCache(String delegateId, String accountId) {
    return delegateCache.get(accountId, delegateId, false);
  }

  public List<DelegateTask> getNumberOfTaskAssignedCache(String accountId) {
    return delegateCache.getCurrentlyAssignedTask(accountId);
  }

  private Map<String, Integer> listOfDelegatesSortedByNumberOfTaskAssignedFromRedisCache(String accountId) {
    // TBD
    return null;
  }
}
