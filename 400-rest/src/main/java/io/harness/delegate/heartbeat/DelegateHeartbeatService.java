/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.delegate.utils.DelegateServiceConstants.HEARTBEAT_EXPIRY_TIME;

import io.harness.beans.DelegateHeartbeatParams;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateNotRegisteredException;
import io.harness.exception.WingsException;
import io.harness.logging.Misc;
import io.harness.observer.Subject;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.service.impl.DelegateObserver;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Process delegate heart beat request.
 * @param <T> Heartbeat response type.
 * FIXME: use observer for heartbeat callbacks instead of inheritance.
 */
@Slf4j
public abstract class DelegateHeartbeatService<T extends Object> {
  @Inject protected Clock clock;
  @Inject private DelegateHeartbeatDao delegateHeartbeatDao;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private DelegateCache delegateCache;
  @Inject @Getter private Subject<DelegateObserver> subject = new Subject<>();

  public Optional<T> precheck(@NotNull final Delegate existingDelegate) {
    return Optional.empty();
  }

  public abstract void finish(T response, DelegateHeartbeatParams params);

  public abstract T buildHeartbeatResponseOnSuccess(DelegateHeartbeatParams params, Delegate existingDelegate);
  public abstract T buildHeartbeatResponseOnFailure(DelegateHeartbeatParams params, WingsException e);

  public T processHeartbeatRequest(@NotNull final Delegate existingDelegate, @NotNull DelegateHeartbeatParams params) {
    logLastHeartbeatSkew(existingDelegate.getUuid(), params.getLastHeartBeat());
    delegateHeartbeatDao.updateDelegateWithHeartbeatTime(existingDelegate.getAccountId(), existingDelegate.getUuid(),
        clock.millis(), Date.from(OffsetDateTime.now().plusDays(Delegate.TTL.toDays()).toInstant()));
    delegateTaskService.touchExecutingTasks(existingDelegate.getAccountId(), existingDelegate.getUuid(),
        existingDelegate.getCurrentlyExecutingDelegateTasks());
    return buildHeartbeatResponseOnSuccess(params, existingDelegate);
  }

  private void logLastHeartbeatSkew(@NotNull final String delegateId, final long lastHeartbeat) {
    final long now = clock.millis();
    long delegateHeartbeat = lastHeartbeat;
    long skew = Math.abs(now - delegateHeartbeat);
    if (skew > TimeUnit.MINUTES.toMillis(2L)) {
      log.debug("Delegate {} has clock skew of {}", delegateId, Misc.getDurationString(skew));
    }
  }

  private Delegate getExistingDelegateExceptionIfNullOrDeleted(
      @NotNull final String accountId, @NotNull final String delegateId) {
    final Delegate delegate = delegateCache.get(accountId, delegateId, true);
    if (Objects.isNull(delegate) || (delegate.getStatus() == DelegateInstanceStatus.DELETED)) {
      log.warn("Sending self destruct command from register delegate because the existing delegate "
          + (Objects.isNull(delegate) ? "is not found" : "has status deleted."));
      throw new DelegateNotRegisteredException(String.format("{uuid %s, account_id %s}", delegateId, accountId));
    }
    return delegate;
  }

  public T process(@NotNull DelegateHeartbeatParams params) {
    try {
      final Delegate existingDelegate =
          getExistingDelegateExceptionIfNullOrDeleted(params.getAccountId(), params.getDelegateId());
      long lastRecordedHeartBeat = existingDelegate.getLastHeartBeat();
      final T response = precheck(existingDelegate).orElseGet(() -> processHeartbeatRequest(existingDelegate, params));
      finish(response, params);
      boolean isDelegateReconnectingAfterLongPause =
          clock.millis() > (lastRecordedHeartBeat + HEARTBEAT_EXPIRY_TIME.toMillis());
      if (isDelegateReconnectingAfterLongPause) {
        subject.fireInform(DelegateObserver::onReconnected, existingDelegate);
      }
      return response;
    } catch (WingsException e) {
      log.error("Heartbeat failed for delegate {}.", params.getDelegateId(), e);
      // If the exception is not handled, it will fail the process early
      final T failureResponse = Optional.ofNullable(buildHeartbeatResponseOnFailure(params, e)).orElseThrow(() -> e);
      finish(failureResponse, params);
      return failureResponse;
    }
  }
}
