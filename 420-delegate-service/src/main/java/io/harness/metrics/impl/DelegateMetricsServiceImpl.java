/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.metrics.AutoMetricContext;
import io.harness.metrics.beans.DelegateAccountMetricContext;
import io.harness.metrics.beans.DelegateTaskMetricContext;
import io.harness.metrics.beans.DelegateTaskTypeMetricContext;
import io.harness.metrics.beans.PerpetualTaskMetricContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateMetricsServiceImpl implements DelegateMetricsService {
  public static final String DELEGATE_TASK_CREATION = "delegate_task_creation";
  public static final String DELEGATE_TASK_NO_ELIGIBLE_DELEGATES = "delegate_task_no_eligible_delegates";
  public static final String DELEGATE_TASK_RESPONSE = "delegate_response";
  public static final String DELEGATE_TASK_ACQUIRE = "delegate_task_acquire";
  public static final String DELEGATE_TASK_ACQUIRE_FAILED = "delegate_task_acquire_failed";
  public static final String DELEGATE_TASK_EXPIRED = "delegate_task_expired";
  public static final String DELEGATE_TASK_ACQUIRE_LIMIT_EXCEEDED = "delegate_task_acquire_limit_exceeded";
  public static final String DELEGATE_TASK_REBROADCAST = "delegate_task_rebroadcast";
  public static final String DELEGATE_TASK_VALIDATION = "delegate_task_validation";
  public static final String DELEGATE_TASK_NO_FIRST_WHITELISTED = "delegate_task_no_first_whitelisted";

  public static final String DELEGATE_REGISTRATION_FAILED = "delegate_registration_failed";
  public static final String DELEGATE_RESTARTED = "delegate_restarted";
  public static final String DELEGATE_DISCONNECTED = "delegate_disconnected";
  public static final String DELEGATE_DESTROYED = "destroy_delegate";

  public static final String PERPETUAL_TASK_CREATE = "perpetual_task_create";
  public static final String PERPETUAL_TASK_RESET = "perpetual_task_reset";
  public static final String PERPETUAL_TASK_DELETE = "perpetual_task_delete";
  public static final String PERPETUAL_TASK_PAUSE = "perpetual_task_pause";
  public static final String PERPETUAL_TASK_ASSIGNED = "perpetual_task_assigned";
  public static final String PERPETUAL_TASK_UNASSIGNED = "perpetual_task_unassigned";
  public static final String TASK_TYPE_SUFFIX = "_by_type";

  public static final String DELEGATE_JWT_CACHE_HIT = "delegate_auth_cache_hit";
  public static final String DELEGATE_JWT_CACHE_MISS = "delegate_auth_cache_miss";

  @Inject private MetricService metricService;
  @Inject private DelegateTaskMetricContextBuilder metricContextBuilder;

  @Override
  public void recordDelegateTaskMetrics(DelegateTask task, String metricName) {
    try (AutoMetricContext ignore = new DelegateTaskMetricContext(task.getAccountId())) {
      metricService.incCounter(metricName);
    }

    try (AutoMetricContext ignore = new DelegateTaskTypeMetricContext(task.getData().getTaskType())) {
      metricService.incCounter(metricName + TASK_TYPE_SUFFIX);
    }
  }

  @Override
  public void recordDelegateTaskMetrics(String accountId, String metricName) {
    try (DelegateTaskMetricContext ignore = new DelegateTaskMetricContext(accountId)) {
      metricService.incCounter(metricName);
    }
  }

  @Override
  public void recordDelegateTaskResponseMetrics(
      DelegateTask delegateTask, DelegateTaskResponse response, String metricName) {
    try (DelegateTaskMetricContext ignore = new DelegateTaskMetricContext(delegateTask.getAccountId())) {
      metricService.incCounter(metricName);
    }
  }

  @Override
  public void recordDelegateMetrics(Delegate delegate, String metricName) {
    if (delegate == null) {
      return;
    }
    try (AutoMetricContext ignore = metricContextBuilder.getContext(delegate, Delegate.class)) {
      metricService.incCounter(metricName);
    }
  }

  @Override
  public void recordPerpetualTaskMetrics(String accountId, String perpetualTaskType, String metricName) {
    try (PerpetualTaskMetricContext ignore = new PerpetualTaskMetricContext(accountId, perpetualTaskType)) {
      metricService.incCounter(metricName);
    }
  }

  @Override
  public void recordDelegateJWTCacheMetrics(String accountId, String metricName) {
    try (DelegateAccountMetricContext ignore = new DelegateAccountMetricContext(accountId)) {
      metricService.incCounter(metricName);
    }
  }
}
