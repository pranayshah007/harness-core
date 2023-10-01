/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_PUBLISH_HEARTBEAT_METRICS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.HEARTBEAT_CONNECTED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.HEARTBEAT_DELETE_EVENT;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.HEARTBEAT_EVENT;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.HEARTBEAT_RESTART_EVENT;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.HEARTBEAT_UNREGISTER_EVENT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Account;
import software.wings.dl.GenericDbCache;
import software.wings.service.intfc.AccountService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class DelegateHeartBeatMetricsHelper {
  @Inject private AccountService accountService;
  @Inject private HPersistence persistence;
  @Inject private GenericDbCache dbCache;
  @Inject private DelegateMetricsService delegateMetricsService;

  private final Cache<String, DelegateRing> delegateRingCache =
      Caffeine.newBuilder().maximumSize(10).expireAfterWrite(30, TimeUnit.MINUTES).build();
  private final Cache<String, Long> lastHeartBeatTimeCache =
      Caffeine.newBuilder().maximumSize(5000L).expireAfterWrite(10, TimeUnit.MINUTES).build();
  private final Long HEARTBEAT_RECORD_FREQUENCY = 10L;

  private List<String> getAccountDetails(String accountId) {
    List<String> accountInfo = new ArrayList<>();
    Account account = dbCache.get(Account.class, accountId);
    if (account != null) {
      accountInfo.add(account.getAccountName());
      accountInfo.add(account.getCompanyName());
      accountInfo.add(account.getRingName());
      return accountInfo;
    }
    Account accountFromDB = accountService.get(accountId);
    dbCache.put(Account.class, accountId, accountFromDB);
    accountInfo.add(accountFromDB.getAccountName());
    accountInfo.add(accountFromDB.getCompanyName());
    accountInfo.add(accountFromDB.getRingName());
    return accountInfo;
  }

  private DelegateRing getDelegateRing(String ringName) {
    DelegateRing delegateRing = delegateRingCache.getIfPresent(ringName);
    if (delegateRing != null) {
      return delegateRing;
    }
    DelegateRing ringFromDB =
        persistence.createQuery(DelegateRing.class).filter(DelegateRingKeys.ringName, ringName).get();
    delegateRingCache.put(ringName, ringFromDB);
    return ringFromDB;
  }

  public void addDelegateHeartBeatMetric(long time, String accountId, String orgId, String projectId,
      String delegateName, String delegateId, String delegateVersion, String delegateConnectionStatus,
      String delegateEventType, boolean isNg, boolean isImmutable, long lastHB, String metricName) {
    if (!accountService.isFeatureFlagEnabled(String.valueOf(PL_PUBLISH_HEARTBEAT_METRICS), accountId)) {
      return;
    }
    if (null == delegateId) {
      return;
    }
    if (Objects.equals(delegateConnectionStatus, HEARTBEAT_CONNECTED)
        && Objects.equals(delegateEventType, HEARTBEAT_EVENT)) {
      Long lastRecordedTime = lastHeartBeatTimeCache.getIfPresent(delegateId);
      if (null != lastRecordedTime
          && (TimeUnit.MILLISECONDS.toMinutes(time - lastRecordedTime) < HEARTBEAT_RECORD_FREQUENCY)) {
        return;
      }
    }
    try {
      List<String> accountInfo = getAccountDetails(accountId);
      DelegateRing delegateRing = (accountInfo.get(2) != null) ? getDelegateRing(accountInfo.get(2)) : null;
      delegateMetricsService.recordDelegateHeartBeatMetricsPerAccount(time, accountId, accountInfo.get(0),
          accountInfo.get(1), delegateRing, orgId, projectId, delegateName, delegateId, delegateVersion,
          delegateConnectionStatus, delegateEventType, isNg, isImmutable, lastHB, metricName);
      if (Objects.equals(delegateEventType, HEARTBEAT_DELETE_EVENT)
          || Objects.equals(delegateEventType, HEARTBEAT_UNREGISTER_EVENT)
          || Objects.equals(delegateEventType, HEARTBEAT_RESTART_EVENT)) {
        lastHeartBeatTimeCache.invalidate(delegateId);
      } else {
        lastHeartBeatTimeCache.put(delegateId, time);
      }
    } catch (Exception ex) {
      log.error("Exception occurred while recording heartBeat metric", ex);
    }
  }
}
