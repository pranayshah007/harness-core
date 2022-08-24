/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common;

import static io.harness.data.structure.UUIDGenerator.generateTimeBasedUuid;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASKS_CURRENTLY_EXECUTING;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASKS_IN_QUEUE;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.REVOKED_TOKEN;
import static io.harness.network.Localhost.getLocalHostAddress;
import static io.harness.network.Localhost.getLocalHostName;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.logging.DelegateStackdriverLogAppender;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.grpc.util.RestartableServiceManager;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.version.VersionInfoManager;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.atmosphere.wasync.Socket;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
public abstract class AbstractDelegateAgentServiceImpl implements DelegateAgentService {
  protected static final String HOST_NAME = getLocalHostName();
  protected static final String DELEGATE_TYPE = System.getenv("DELEGATE_TYPE");
  protected static final String DELEGATE_NAME =
      isNotBlank(System.getenv("DELEGATE_NAME")) ? System.getenv("DELEGATE_NAME") : "";
  protected static final String DELEGATE_GROUP_NAME = System.getenv("DELEGATE_GROUP_NAME");
  protected static final boolean DELEGATE_NG =
      isNotBlank(System.getenv("DELEGATE_SESSION_IDENTIFIER")) || Boolean.parseBoolean(System.getenv("NEXT_GEN"));
  private static final long HEARTBEAT_TIMEOUT = TimeUnit.MINUTES.toMillis(15);
  protected static final String DELEGATE_ORG_IDENTIFIER = System.getenv("DELEGATE_ORG_IDENTIFIER");
  protected static final String DELEGATE_PROJECT_IDENTIFIER = System.getenv("DELEGATE_PROJECT_IDENTIFIER");
  protected static final String DELEGATE_CONNECTION_ID = generateTimeBasedUuid();
  protected static final String DELEGATE_INSTANCE_ID = generateUuid();
  protected static final boolean BLOCK_SHELL_TASK = Boolean.parseBoolean(System.getenv("BLOCK_SHELL_TASK"));
  protected static final String DELEGATE_GROUP_ID = System.getenv("DELEGATE_GROUP_ID");
  protected static final String DELEGATE_TAGS = System.getenv("DELEGATE_TAGS");

  private static final String DUPLICATE_DELEGATE_ERROR_MESSAGE =
      "Duplicate delegate with same delegateId:%s and connectionId:%s exists";

  private static volatile String delegateId;

  @Inject @Named("taskExecutor") @Getter(AccessLevel.PROTECTED) private ThreadPoolExecutor taskExecutor;
  @Inject @Named("taskPollExecutor") @Getter(AccessLevel.PROTECTED) private ScheduledExecutorService taskPollExecutor;

  @Inject @Getter(AccessLevel.PROTECTED) private DelegateConfiguration delegateConfiguration;
  @Inject @Getter(AccessLevel.PROTECTED) private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject @Getter(AccessLevel.PROTECTED) private RestartableServiceManager restartableServiceManager;
  @Inject @Getter(AccessLevel.PROTECTED) private VersionInfoManager versionInfoManager;
  @Inject @Getter(AccessLevel.PROTECTED) private HarnessMetricRegistry metricRegistry;
  @Inject @Getter(AccessLevel.PROTECTED) private Clock clock;

  protected final AtomicBoolean sentFirstHeartbeat = new AtomicBoolean(false);
  private final AtomicLong lastHeartbeatSentAt = new AtomicLong(System.currentTimeMillis());
  protected final AtomicBoolean closingSocket = new AtomicBoolean(false);
  protected final AtomicBoolean reconnectingSocket = new AtomicBoolean(false);
  private final AtomicLong frozenAt = new AtomicLong(-1);

  @Getter(AccessLevel.PROTECTED) private final AtomicBoolean frozen = new AtomicBoolean(false);
  @Getter(AccessLevel.PROTECTED) private final AtomicBoolean acquireTasks = new AtomicBoolean(true);
  @Getter(AccessLevel.PROTECTED) private final AtomicBoolean selfDestruct = new AtomicBoolean(false);

  @Override
  public void pause() {
    if (!delegateConfiguration.isPollForTasks()) {
      finalizeSocket();
    }
  }

  public void freeze() {
    log.warn("Delegate with id: {} was put in freeze mode.", delegateId);
    frozenAt.set(System.currentTimeMillis());
    frozen.set(true);
  }

  @Override
  public boolean isHeartbeatHealthy() {
    return sentFirstHeartbeat.get() && ((clock.millis() - lastHeartbeatSentAt.get()) <= HEARTBEAT_TIMEOUT);
  }

  @Override
  public boolean isSocketHealthy() {
    return getSocket().status() == Socket.STATUS.OPEN || getSocket().status() == Socket.STATUS.REOPENED;
  }

  @Override
  public void shutdown(boolean shouldUnregister) throws InterruptedException {
    shutdownExecutors();
    if (shouldUnregister) {
      unregisterDelegate();
    }
  }

  @Override
  public void recordMetrics() {
    final int tasksInQueueCount = taskExecutor.getQueue().size();
    final long tasksExecutionCount = taskExecutor.getActiveCount();
    metricRegistry.recordGaugeValue(TASKS_IN_QUEUE, new String[] {DELEGATE_NAME}, tasksInQueueCount);
    metricRegistry.recordGaugeValue(TASKS_CURRENTLY_EXECUTING, new String[] {DELEGATE_NAME}, tasksExecutionCount);
  }

  protected abstract Socket getSocket();

  protected String getVersion() {
    return versionInfoManager.getVersionInfo().getVersion();
  }

  private void finalizeSocket() {
    closingSocket.set(true);
    getSocket().close();
  }

  private void shutdownExecutors() throws InterruptedException {
    log.info("Initiating delegate shutdown");
    acquireTasks.set(false);

    final long shutdownStart = clock.millis();
    log.info("Stopping executors");
    taskExecutor.shutdown();
    taskPollExecutor.shutdown();

    final boolean terminatedTaskExec = taskExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    final boolean terminatedPoll = taskPollExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

    log.info("Executors terminated after {}s. All tasks completed? Task [{}], Polling [{}]",
        Duration.ofMillis(clock.millis() - shutdownStart).toMillis() * 1000, terminatedTaskExec, terminatedPoll);

    //    if (perpetualTaskWorker != null) {
    //      log.info("Stopping perpetual task workers");
    //      perpetualTaskWorker.stop();
    //      log.info("Stopped perpetual task workers");
    //    }

    if (restartableServiceManager != null) {
      restartableServiceManager.stop();
    }

    //    if (chronicleEventTailer != null) {
    //      log.info("Stopping chronicle event trailer");
    //      chronicleEventTailer.stopAsync().awaitTerminated();
    //      log.info("Stopped chronicle event trailer");
    //    }
  }

  private void unregisterDelegate() {
    final DelegateUnregisterRequest request = new DelegateUnregisterRequest(delegateId, HOST_NAME, DELEGATE_NG,
        DELEGATE_TYPE, getLocalHostAddress(), DELEGATE_ORG_IDENTIFIER, DELEGATE_PROJECT_IDENTIFIER);
    try {
      log.info("Unregistering delegate {}", delegateId);
      executeRestCall(delegateAgentManagerClient.unregisterDelegate(delegateConfiguration.getAccountId(), request));
    } catch (final IOException e) {
      log.error("Failed unregistering delegate {}", delegateId, e);
    }
  }

  protected <T> T executeRestCall(Call<T> call) throws IOException {
    Response<T> response = null;
    try {
      response = call.execute();
      return response.body();
    } catch (Exception e) {
      log.error("error executing rest call", e);
      throw e;
    } finally {
      if (response != null && !response.isSuccessful()) {
        String errorResponse = response.errorBody().string();

        log.warn("Received Error Response: {}", errorResponse);

        if (errorResponse.contains(INVALID_TOKEN.name())) {
          log.warn("Delegate used invalid token. Self destruct procedure will be initiated.");
          initiateSelfDestruct();
        } else if (errorResponse.contains(
                       format(DUPLICATE_DELEGATE_ERROR_MESSAGE, delegateId, DELEGATE_CONNECTION_ID))) {
          initiateSelfDestruct();
        } else if (errorResponse.contains(EXPIRED_TOKEN.name())) {
          log.warn("Delegate used expired token. It will be frozen and drained.");
          freeze();
        } else if (errorResponse.contains(REVOKED_TOKEN.name()) || errorResponse.contains("Revoked Delegate Token")) {
          log.warn("Delegate used revoked token. It will be frozen and drained.");
          freeze();
        }

        response.errorBody().close();
      }
    }
  }

  protected void initiateSelfDestruct() {
    log.info("Self destruct sequence initiated...");
    acquireTasks.set(false);
    //    upgradePending.set(false);
    //    upgradeNeeded.set(false);
    //    restartNeeded.set(false);
    selfDestruct.set(true);

    if (getSocket() != null) {
      finalizeSocket();
    }

    DelegateStackdriverLogAppender.setManagerClient(null);
    //    if (perpetualTaskWorker != null) {
    //      perpetualTaskWorker.stop();
    //    }
  }
}
