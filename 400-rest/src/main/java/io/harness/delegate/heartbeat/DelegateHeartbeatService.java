/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertFromBase64;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.delegate.utils.DelegateServiceConstants.HEARTBEAT_EXPIRY_TIME;
import static io.harness.delegate.utils.DelegateServiceConstants.STREAM_DELEGATE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_DESTROYED;

import io.harness.beans.DelegateHeartbeatParams;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateNotRegisteredException;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.DuplicateDelegateException;
import io.harness.exception.WingsException;
import io.harness.logging.Misc;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.observer.Subject;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateSetupService;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.service.impl.DelegateObserver;
import software.wings.service.intfc.AccountService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.atmosphere.cpr.BroadcasterFactory;

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
  @Inject private AccountService accountService;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private BroadcasterFactory broadcasterFactory;

  @Inject private DelegateSetupService delegateSetupService;

  @Inject @Getter private Subject<DelegateObserver> subject = new Subject<>();
  @Inject @Named("enableRedisForDelegateService") private boolean enableRedisForDelegateService;

  public Optional<T> precheck(@NotNull final Delegate existingDelegate, @NotNull final DelegateHeartbeatParams params) {
    if (isNotEmpty(existingDelegate.getDelegateConnectionId())) {
      UUID currentUUID = convertFromBase64(params.getDelegateConnectionId());
      UUID existingUUID = convertFromBase64(existingDelegate.getDelegateConnectionId());
      if (existingUUID.timestamp() < currentUUID.timestamp()) {
        if (DelegateType.SHELL_SCRIPT.equals(existingDelegate.getDelegateType())) {
          shellScriptDelegateLocationCheck(existingDelegate, params);
        }
        log.debug("Delegate restarted");
      }
    }
    return Optional.empty();
  }

  public abstract void finish(T response, DelegateHeartbeatParams params);

  public abstract T buildHeartbeatResponseOnSuccess(DelegateHeartbeatParams params, Delegate existingDelegate);
  public abstract T buildHeartbeatResponseOnFailure(DelegateHeartbeatParams params, WingsException e);

  public T processHeartbeatRequest(@NotNull final Delegate existingDelegate, @NotNull DelegateHeartbeatParams params) {
    logLastHeartbeatSkew(existingDelegate.getUuid(), params.getLastHeartBeat());
    delegateHeartbeatDao.updateDelegateWithHeartbeatAndConnectionInfo(existingDelegate.getAccountId(),
        existingDelegate.getUuid(), clock.millis(),
        Date.from(OffsetDateTime.now().plus(existingDelegate.ttlMillis(), ChronoUnit.MILLIS).toInstant()), params);
    delegateTaskService.touchExecutingTasks(existingDelegate.getAccountId(), existingDelegate.getUuid(),
        existingDelegate.getCurrentlyExecutingDelegateTasks());
    // FIXME: have a different way of updating TTL since one group can have multiple delegates and all of them will be
    // updating this very frequently
    if (existingDelegate.getDelegateGroupId() != null) {
      delegateSetupService.updateDelegateGroupValidity(
          existingDelegate.getAccountId(), existingDelegate.getDelegateGroupId());
    }
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
    final Delegate delegate = delegateCache.get(accountId, delegateId, !enableRedisForDelegateService);
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
      final T response =
          precheck(existingDelegate, params).orElseGet(() -> processHeartbeatRequest(existingDelegate, params));
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

  @VisibleForTesting
  protected boolean shellScriptDelegateLocationCheck(Delegate existingDelegate, DelegateHeartbeatParams params) {
    boolean notSameLocationForShellScriptDelegate = isNotEmpty(params.getLocation())
        && isNotEmpty(existingDelegate.getLocation()) && !params.getLocation().equals(existingDelegate.getLocation());
    if (notSameLocationForShellScriptDelegate) {
      log.error(
          "Newer delegate connection found for the delegate id! Will initiate self destruct sequence for the current delegate.");
      destroyTheCurrentDelegate(params.getAccountId(), params.getDelegateId(), params.getDelegateConnectionId(),
          existingDelegate.isPolllingModeEnabled());
      return true;
    } else {
      log.error("Two delegates with the same identity");
      return false;
    }
  }

  private void destroyTheCurrentDelegate(
      String accountId, String delegateId, String delegateConnectionId, boolean isPollingMode) {
    Delegate delegate = delegateCache.get(accountId, delegateId);
    delegateMetricsService.recordDelegateMetrics(delegate, DELEGATE_DESTROYED);
    if (isPollingMode) {
      log.warn("Sent self destruct command to delegate {}, with connectionId {}.", delegateId, delegateConnectionId);
      throw new DuplicateDelegateException(delegateId, delegateConnectionId);
    }
    broadcasterFactory.lookup(STREAM_DELEGATE + accountId, true)
        .broadcast(SELF_DESTRUCT + delegateId + "-" + delegateConnectionId);
    log.warn("Sent self destruct command to delegate {}, with connectionId {}.", delegateId, delegateConnectionId);
  }
}
