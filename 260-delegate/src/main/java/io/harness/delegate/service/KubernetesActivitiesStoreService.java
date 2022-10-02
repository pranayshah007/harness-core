/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.cvng.beans.activity.KubernetesActivityDTO;
import io.harness.rest.RestResponse;
import io.harness.verificationclient.CVNextGenServiceClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.common.util.concurrent.TimeLimiter;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.inject.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class KubernetesActivitiesStoreService {
  @Inject private CVNextGenServiceClient cvNextGenServiceClient;
  @Inject private TimeLimiter timeLimiter;
  private Cache<String, List<KubernetesActivityDTO>> activitiesCache;

  @Inject
  public KubernetesActivitiesStoreService(CVNextGenServiceClient cvNextGenServiceClient,
      @Named("asyncExecutor") ExecutorService executorService, TimeLimiter timeLimiter) {
    this.cvNextGenServiceClient = cvNextGenServiceClient;
    this.timeLimiter = timeLimiter;
    this.activitiesCache = Caffeine.newBuilder()
                               .executor(executorService)
                               .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
                               .removalListener(this::dispatchKubernetesActivities)
                               .build();
  }

  public synchronized void save(String accountId, KubernetesActivityDTO activityDTO) {
    Optional.ofNullable(activitiesCache.get(accountId, s -> new ArrayList<>()))
        .ifPresent(activityDTOs -> activityDTOs.add(activityDTO));
  }

  private void dispatchKubernetesActivities(
      String accountId, List<KubernetesActivityDTO> activityDTOS, RemovalCause removalCause) {
    if (accountId == null || isEmpty(activityDTOS)) {
      log.error("Unexpected Cache eviction accountId={}, activityDTOS={}, removalCause={}", accountId, activityDTOS,
          removalCause);
      return;
    }
    activityDTOS.stream()
        .collect(groupingBy(KubernetesActivityDTO::getActivitySourceConfigId, toList()))
        .forEach((activitySourceConfigId, activities) -> {
          if (isEmpty(activities)) {
            return;
          }
          try {
            log.info("Dispatching {} activities for [{}] [{}]", activities.size(), accountId, activitySourceConfigId);
            RestResponse<Boolean> restResponse = HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(30),
                ()
                    -> execute(cvNextGenServiceClient.saveKubernetesActivities(
                        accountId, activitySourceConfigId, activities)));
            if (restResponse == null) {
              return;
            }
            log.info("Dispatched {} activities for [{}] [{}]",
                restResponse.getResource() != null ? activities.size() : 0, accountId, activitySourceConfigId);
          } catch (Exception e) {
            log.error(
                "Dispatch activities failed for {}. printing lost activities[{}]", accountId, activities.size(), e);
            activities.forEach(logObject -> log.error(logObject.toString()));
            log.error("Finished printing lost activities");
          }
        });
  }
}
