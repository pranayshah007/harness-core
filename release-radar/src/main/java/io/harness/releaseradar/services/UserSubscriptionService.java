/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.releaseradar.beans.EventFilter;
import io.harness.releaseradar.entities.UserSubscription;
import io.harness.repositories.UserSubscriptionRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class UserSubscriptionService {
  @Inject private UserSubscriptionRepository repository;

  public List<String> jiraTicketsSubscribed() {
    return StreamSupport.stream(repository.findAll().spliterator(), false)
        .filter(userSubscription
            -> userSubscription.getFilter() != null && isNotEmpty(userSubscription.getFilter().getJiraId()))
        .map(UserSubscription::getFilter)
        .map(EventFilter::getJiraId)
        .collect(Collectors.toList());
  }

  public List<UserSubscription> getAllSubscriptions(EventFilter filter) {
    List<UserSubscription> subscriptions = new ArrayList<>();
    repository.findAll().forEach(userSubscription -> {
      if (isSubscribed(filter, userSubscription)) {
        subscriptions.add(userSubscription);
      }
    });
    return subscriptions;
  }

  public List<UserSubscription> getAllSubscriptions(Collection<String> jiraIds) {
    List<UserSubscription> subscriptions = new ArrayList<>();
    repository.findAll().forEach(userSubscription -> {
      if (isSubscribed(jiraIds, userSubscription)) {
        subscriptions.add(userSubscription);
      }
    });
    return subscriptions;
  }

  private static boolean isSubscribed(EventFilter filter, UserSubscription userSubscription) {
    EventFilter userFilter = userSubscription.getFilter();
    return (isEmpty(userFilter.getServiceName())
               || userFilter.getServiceName().equalsIgnoreCase(filter.getServiceName()))

        && (isEmpty(userFilter.getBuildVersion())
            || userFilter.getBuildVersion().equalsIgnoreCase(filter.getBuildVersion()))

        && (userFilter.getEnvironment() == null || filter.getEnvironment() == userFilter.getEnvironment())

        && (userFilter.getEventType() == null || filter.getEventType() == userFilter.getEventType())

        && (isEmpty(userFilter.getRelease()) || userFilter.getRelease().equalsIgnoreCase(filter.getRelease()))

        && (isEmpty(userFilter.getJiraId()) || userFilter.getJiraId().equalsIgnoreCase(filter.getJiraId()));
  }

  private static boolean isSubscribed(Collection<String> jiraIds, UserSubscription userSubscription) {
    EventFilter userFilter = userSubscription.getFilter();
    return isNotEmpty(userFilter.getJiraId()) && jiraIds.contains(userFilter.getJiraId());
  }
}
