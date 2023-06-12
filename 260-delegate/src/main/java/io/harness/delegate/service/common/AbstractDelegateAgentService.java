/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateTimeBasedUuid;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateParams.DelegateParamsBuilder;
import static io.harness.delegate.beans.DelegateParams.builder;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.delegate.metrics.DelegateMetric.TASKS_CURRENTLY_EXECUTING;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.REVOKED_TOKEN;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.Misc.getDurationString;
import static io.harness.network.Localhost.getLocalHostAddress;
import static io.harness.network.Localhost.getLocalHostName;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.delegate.core.beans.ExecutionStatusResponse;
import io.harness.delegate.core.beans.TaskPayload;
import io.harness.delegate.logging.DelegateStackdriverLogAppender;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.delegate.service.core.client.DelegateCoreManagerClient;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.exception.UnexpectedException;
import io.harness.grpc.util.RestartableServiceManager;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.network.FibonacciBackOff;
import io.harness.rest.RestResponse;
import io.harness.security.TokenGenerator;
import io.harness.serializer.JsonUtils;
import io.harness.threading.Schedulable;
import io.harness.version.VersionInfoManager;

import software.wings.beans.TaskType;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.asynchttpclient.AsyncHttpClient;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.transport.TransportNotSupported;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
public abstract class AbstractDelegateAgentService implements DelegateAgentService {
  protected static final String HOST_NAME = getLocalHostName();
  private static final String DELEGATE_INSTANCE_ID = generateUuid();
  private static final int POLL_INTERVAL_SECONDS = 3;
  // Marker string to indicate task events.
  private static final String TASK_EVENT_MARKER = "{\"eventType\":\"DelegateTaskEvent\"";
  private static final String ABORT_EVENT_MARKER = "{\"eventType\":\"DelegateTaskAbortEvent\"";
  private static final String HEARTBEAT_RESPONSE = "{\"eventType\":\"DelegateHeartbeatResponseStreaming\"";

  private static final String DELEGATE_TYPE = System.getenv("DELEGATE_TYPE");
  protected static final String DELEGATE_NAME =
      isNotBlank(System.getenv("DELEGATE_NAME")) ? System.getenv("DELEGATE_NAME") : "";
  private static final String DELEGATE_GROUP_NAME = System.getenv("DELEGATE_GROUP_NAME");
  private static final boolean DELEGATE_NG =
      isNotBlank(System.getenv("DELEGATE_SESSION_IDENTIFIER")) || Boolean.parseBoolean(System.getenv("NEXT_GEN"));
  private static final long HEARTBEAT_TIMEOUT = TimeUnit.MINUTES.toMillis(15);
  private static final String DELEGATE_ORG_IDENTIFIER = System.getenv("DELEGATE_ORG_IDENTIFIER");
  private static final String DELEGATE_PROJECT_IDENTIFIER = System.getenv("DELEGATE_PROJECT_IDENTIFIER");
  private static final String DELEGATE_CONNECTION_ID = generateTimeBasedUuid();
  private static final String DELEGATE_GROUP_ID = System.getenv("DELEGATE_GROUP_ID");
  private static final String DELEGATE_TAGS = System.getenv("DELEGATE_TAGS");

  private static final String DUPLICATE_DELEGATE_ERROR_MESSAGE =
      "Duplicate delegate with same delegateId:%s and connectionId:%s exists";
  private static final int NUM_RESPONSE_RETRIES = 5;

  @Inject @Named("taskExecutor") @Getter(AccessLevel.PROTECTED) private ThreadPoolExecutor taskExecutor;
  @Inject @Named("taskPollExecutor") private ScheduledExecutorService taskPollExecutor;
  @Inject @Named("healthMonitorExecutor") private ScheduledExecutorService healthMonitorExecutor;

  @Inject @Getter(AccessLevel.PROTECTED) private DelegateConfiguration delegateConfiguration;
  @Inject @Getter(AccessLevel.PROTECTED) private HarnessMetricRegistry metricRegistry;
  @Inject @Getter(AccessLevel.PROTECTED) private Clock clock;
  @Inject private DelegateCoreManagerClient managerClient;
  @Inject private RestartableServiceManager restartableServiceManager;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private AsyncHttpClient asyncHttpClient;
  @Inject private TokenGenerator tokenGenerator;

  private TimeLimiter delegateHealthTimeLimiter;
  private Client client;
  private Socket socket;

  private final Set<String> currentlyAcquiringTasks = ConcurrentHashMap.newKeySet();

  private final AtomicBoolean sentFirstHeartbeat = new AtomicBoolean(false);
  private final AtomicLong lastHeartbeatSentAt = new AtomicLong(System.currentTimeMillis());
  private final AtomicLong lastHeartbeatReceivedAt = new AtomicLong(System.currentTimeMillis());
  private final AtomicInteger heartbeatSuccessCalls = new AtomicInteger();
  private final AtomicBoolean closingSocket = new AtomicBoolean(false);
  private final AtomicBoolean reconnectingSocket = new AtomicBoolean(false);
  private final AtomicLong frozenAt = new AtomicLong(-1);

  private final AtomicBoolean frozen = new AtomicBoolean(false);
  private final AtomicBoolean acquireTasks = new AtomicBoolean(true);
  private final AtomicBoolean selfDestruct = new AtomicBoolean(false);

  protected abstract void abortTask(DelegateTaskAbortEvent taskEvent);
  protected abstract void executeTask(String id, TaskPayload executeTask);
  protected abstract List<String> getCurrentlyExecutingTaskIds();
  protected abstract List<TaskType> getSupportedTasks();
  protected abstract void onDelegateStart();
  protected abstract void onDelegateRegistered();
  protected abstract void onHeartbeat();
  protected abstract void onPostExecute(String delegateTaskId);
  protected abstract void onPostExecute(String delegateTaskId, Future<?> taskFuture);

  /**
   * Runs before task is executed, if it returns true, the task will not be executed.
   *
   * @param delegateTaskEvent task event
   * @param delegateTaskId taskId
   * @return true if pre-execute checks failed which will cause the task to fail
   */
  protected abstract boolean onPreExecute(DelegateTaskEvent delegateTaskEvent, String delegateTaskId);
  protected abstract void onResponseSent(String taskId);
  // ToDo: add more onXXX lifecycle hooks

  @Override
  public void run(final boolean watched, final boolean isImmutableDelegate) {
    try {
      initDelegateProcess();
    } catch (final Exception e) {
      log.error("Exception while starting/running delegate", e);
    }
  }

  @Override
  public void pause() {
    if (!delegateConfiguration.isPollForTasks()) {
      finalizeSocket();
    }
  }

  @Override
  public void stop() {
    log.info("Stopping delegate platform service, nothing to do!");
  }

  @Override
  public void shutdown(boolean shouldUnregister) throws InterruptedException {
    shutdownExecutors();
    if (shouldUnregister) {
      unregisterDelegate();
    }
  }

  @Override
  public void freeze() {
    log.warn("Delegate with id: {} was put in freeze mode.", DelegateAgentCommonVariables.getDelegateId());
    frozenAt.set(System.currentTimeMillis());
    frozen.set(true);
  }

  @Override
  public boolean isHeartbeatHealthy() {
    return sentFirstHeartbeat.get() && ((clock.millis() - lastHeartbeatSentAt.get()) <= HEARTBEAT_TIMEOUT);
  }

  @Override
  public boolean isSocketHealthy() {
    return socket.status() == Socket.STATUS.OPEN || socket.status() == Socket.STATUS.REOPENED;
  }

  @Override
  public void recordMetrics() {
    final long tasksExecutionCount = taskExecutor.getActiveCount();
    metricRegistry.recordGaugeValue(
        TASKS_CURRENTLY_EXECUTING.getMetricName(), new String[] {DELEGATE_NAME}, tasksExecutionCount);
  }

  @Override
  public void sendTaskResponse(final String taskId, final ExecutionStatusResponse taskResponse) {
    try {
      for (int attempt = 0; attempt < NUM_RESPONSE_RETRIES; attempt++) {
        final Response<ResponseBody> response =
            managerClient.sendProtoTaskStatus(taskId, getDelegateConfiguration().getAccountId(), taskResponse)
                .execute();
        if (response.isSuccessful()) {
          log.info("Proto task {} response sent to manager", taskId);
          break;
        }
        log.warn("Failed to send proto response for task {}: {}. error: {}. requested url: {} {}", taskId,
            response.code(), response.errorBody() == null ? "null" : response.errorBody().string(),
            response.raw().request().url(), attempt < (NUM_RESPONSE_RETRIES - 1) ? "Retrying." : "Giving up.");
        if (attempt < NUM_RESPONSE_RETRIES - 1) {
          // Do not sleep for last loop round, as we are going to fail.
          sleep(ofSeconds(FibonacciBackOff.getFibonacciElement(attempt)));
        }
      }
    } catch (final Exception e) {
      log.error("Unable to send response to manager for task {}", taskId, e);
    } finally {
      onResponseSent(taskId);
    }
  }

  private void dispatchDelegateTask(@NonNull final DelegateTaskEvent delegateTaskEvent) {
    try (TaskLogContext ignore = new TaskLogContext(delegateTaskEvent.getDelegateTaskId(), OVERRIDE_ERROR)) {
      log.info("DelegateTaskEvent received - {}", delegateTaskEvent);
      final String delegateTaskId = delegateTaskEvent.getDelegateTaskId();

      try {
        if (frozen.get()) {
          log.info(
              "Delegate process with detected time out of sync or with revoked token is running. Won't acquire tasks.");
          return;
        }

        if (!acquireTasks.get()) {
          log.info("[Old] Upgraded process is running. Won't acquire task while completing other tasks");
          return;
        }

        if (currentlyAcquiringTasks.contains(delegateTaskId)) {
          log.info("Task [DelegateTaskEvent: {}] currently acquiring. Don't acquire again", delegateTaskEvent);
          return;
        }

        currentlyAcquiringTasks.add(delegateTaskId);
        log.debug("Try to acquire DelegateTask - accountId: {}", getDelegateConfiguration().getAccountId());

        final var taskGroup = acquireTask(delegateTaskId);
        executeTask(taskGroup.getExecutionInfraId(), taskGroup.getTask(0));
      } catch (final IOException e) {
        log.error("Unable to get task for validation", e);
      } catch (final Exception e) {
        log.error("Unable to execute task", e);
      } finally {
        currentlyAcquiringTasks.remove(delegateTaskId);
        onPostExecute(delegateTaskId);
      }
    }
  }

  protected AcquireTasksResponse acquireTask(final String delegateTaskId) throws IOException {
    final var response = executeRestCall(managerClient.acquireProtoTask(DelegateAgentCommonVariables.getDelegateId(),
        delegateTaskId, getDelegateConfiguration().getAccountId(), DELEGATE_INSTANCE_ID));

    final var pluginDescriptors = response.getTaskList();
    log.info("Delegate {} received tasks group {} of {} tasks for delegateInstance {}",
        DelegateAgentCommonVariables.getDelegateId(), response.getExecutionInfraId(), response.getTaskList().size(),
        DELEGATE_INSTANCE_ID);
    return response;
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

    if (restartableServiceManager != null) {
      restartableServiceManager.stop();
    }
  }

  private void unregisterDelegate() {
    final DelegateUnregisterRequest request =
        new DelegateUnregisterRequest(DelegateAgentCommonVariables.getDelegateId(), HOST_NAME, DELEGATE_NG,
            DELEGATE_TYPE, getLocalHostAddress(), DELEGATE_ORG_IDENTIFIER, DELEGATE_PROJECT_IDENTIFIER);
    try {
      log.info("Unregistering delegate {}", DelegateAgentCommonVariables.getDelegateId());
      executeRestCall(managerClient.unregisterDelegate(delegateConfiguration.getAccountId(), request));
    } catch (final IOException e) {
      log.error("Failed unregistering delegate {}", DelegateAgentCommonVariables.getDelegateId(), e);
    }
  }

  private <T> T executeRestCall(Call<T> call) throws IOException {
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
        } else if (errorResponse.contains(format(DUPLICATE_DELEGATE_ERROR_MESSAGE,
                       DelegateAgentCommonVariables.getDelegateId(), DELEGATE_CONNECTION_ID))) {
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

  private void initiateSelfDestruct() {
    log.info("Self destruct sequence initiated...");
    acquireTasks.set(false);
    selfDestruct.set(true);

    if (socket != null) {
      finalizeSocket();
    }
    DelegateStackdriverLogAppender.setManagerClient(null);
  }

  private void initDelegateProcess() {
    try {
      log.info("Delegate will start running on JRE {}", System.getProperty("java.version"));
      log.info("The deploy mode for delegate is [{}]", System.getenv("DEPLOY_MODE"));

      delegateHealthTimeLimiter = HTimeLimiter.create(healthMonitorExecutor);
      DelegateStackdriverLogAppender.setTimeLimiter(delegateHealthTimeLimiter);
      // FIXME: ReIntroduce remote stackdriver logging
      //      DelegateStackdriverLogAppender.setManagerClient(getDelegateAgentManagerClient());

      logProxyConfiguration();

      log.info("Delegate process started");
      long start = getClock().millis();

      if (isNotEmpty(DELEGATE_NAME)) {
        log.info("Registering delegate with delegate name: {}", DELEGATE_NAME);
      }

      final DelegateParamsBuilder builder = createDelegateParamsBuilder();

      final String delegateId = registerDelegate(builder);

      DelegateAgentCommonVariables.setDelegateId(delegateId);
      DelegateStackdriverLogAppender.setDelegateId(delegateId);

      log.info("[New] Delegate registered in {} ms", getClock().millis() - start);

      onDelegateRegistered();

      startProcessingTasks(builder);

      log.info("Delegate started with config {} ", getDelegateConfiguration());
      log.info("Manager Authority:{}, Manager Target:{}", getDelegateConfiguration().getManagerAuthority(),
          getDelegateConfiguration().getManagerTarget());

    } catch (RuntimeException | IOException e) {
      log.error("Exception while starting/running delegate", e);
    }
  }

  private void startProcessingTasks(DelegateParamsBuilder builder) throws IOException {
    if (getDelegateConfiguration().isPollForTasks()) {
      log.info("Polling is enabled for Delegate");
      startHeartbeat(builder);
      startTaskPolling();
    } else {
      openWebsocket();
      startHeartbeat(builder);
    }
  }

  private DelegateParamsBuilder createDelegateParamsBuilder() {
    final List<TaskType> supportedTasks = getSupportedTasks();

    // Remove tasks which are in TaskTypeV2 and only specified with onlyV2 as true

    if (isNotBlank(DELEGATE_TYPE)) {
      log.info("Registering delegate with delegate Type: {}, DelegateGroupName: {} that supports tasks: {}",
          DELEGATE_TYPE, DELEGATE_GROUP_NAME, supportedTasks);
    }

    final String delegateDescription = System.getenv().get("DELEGATE_DESCRIPTION");
    final String descriptionFromConfigFile = isBlank(delegateDescription) ? "" : delegateDescription;
    final String description = "description here".equals(getDelegateConfiguration().getDescription())
        ? descriptionFromConfigFile
        : getDelegateConfiguration().getDescription();

    final String delegateProfile = System.getenv().get("DELEGATE_PROFILE");
    if (isNotBlank(delegateProfile)) {
      log.info("Registering delegate with delegate profile: {}", delegateProfile);
    }

    return builder()
        .ip(getLocalHostAddress())
        .accountId(getDelegateConfiguration().getAccountId())
        .orgIdentifier(DELEGATE_ORG_IDENTIFIER)
        .projectIdentifier(DELEGATE_PROJECT_IDENTIFIER)
        .hostName(HOST_NAME)
        .delegateName(DELEGATE_NAME)
        .delegateGroupName(DELEGATE_GROUP_NAME)
        .delegateGroupId(DELEGATE_GROUP_ID)
        .delegateProfileId(isNotBlank(delegateProfile) ? delegateProfile : null)
        .description(description)
        .version(getVersion())
        .delegateType(DELEGATE_TYPE)
        .supportedTaskTypes(supportedTasks.stream().map(Enum::name).collect(toList()))
        //.proxy(set to true if there is a system proxy)
        .pollingModeEnabled(getDelegateConfiguration().isPollForTasks())
        .ng(DELEGATE_NG)
        .tags(isNotBlank(DELEGATE_TAGS) ? Arrays.asList(DELEGATE_TAGS.trim().split("\\s*,+\\s*,*\\s*")) : emptyList())
        .location(Paths.get("").toAbsolutePath().toString())
        .heartbeatAsObject(true)
        .immutable(getDelegateConfiguration().isImmutable())
        .ceEnabled(Boolean.parseBoolean(System.getenv("ENABLE_CE")));
  }

  private void startHeartbeat(final DelegateParamsBuilder builder) {
    log.debug("Starting heartbeat at interval {} ms", getDelegateConfiguration().getHeartbeatIntervalMs());
    healthMonitorExecutor.scheduleAtFixedRate(() -> {
      try {
        sendHeartbeat(builder);
        if (heartbeatSuccessCalls.incrementAndGet() > 100) {
          log.info("Sent {} calls to manager", heartbeatSuccessCalls.getAndSet(0));
        }
      } catch (Exception ex) {
        log.error("Exception while sending heartbeat", ex);
      }
      onHeartbeat();
    }, 0, getDelegateConfiguration().getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
  }

  private void sendHeartbeat(final DelegateParamsBuilder builder) {
    if (!shouldContactManager() || !acquireTasks.get() || frozen.get()) {
      return;
    }

    DelegateParams delegateParams = builder.build()
                                        .toBuilder()
                                        .lastHeartBeat(getClock().millis())
                                        .pollingModeEnabled(getDelegateConfiguration().isPollForTasks())
                                        .heartbeatAsObject(true)
                                        .currentlyExecutingDelegateTasks(getCurrentlyExecutingTaskIds())
                                        .location(Paths.get("").toAbsolutePath().toString())
                                        .build();

    try {
      if (getDelegateConfiguration().isPollForTasks()) {
        RestResponse<DelegateHeartbeatResponse> delegateParamsResponse =
            executeRestCall(managerClient.delegateHeartbeat(getDelegateConfiguration().getAccountId(), delegateParams));

        long now = getClock().millis();
        log.info("[Polling]: Delegate {} received heartbeat response {} after sending at {}. {} since last response.",
            DelegateAgentCommonVariables.getDelegateId(), getDurationString(lastHeartbeatSentAt.get(), now), now,
            getDurationString(lastHeartbeatReceivedAt.get(), now));
        lastHeartbeatReceivedAt.set(now);

        DelegateHeartbeatResponse receivedDelegateResponse = delegateParamsResponse.getResource();

        if (DelegateAgentCommonVariables.getDelegateId().equals(receivedDelegateResponse.getDelegateId())) {
          if (DelegateInstanceStatus.DELETED == DelegateInstanceStatus.valueOf(receivedDelegateResponse.getStatus())) {
            initiateSelfDestruct();
          } else {
            builder.delegateId(receivedDelegateResponse.getDelegateId());
          }
          lastHeartbeatSentAt.set(getClock().millis());
          lastHeartbeatReceivedAt.set(getClock().millis());
        }
        final DelegateConnectionHeartbeat connectionHeartbeat = DelegateConnectionHeartbeat.builder()
                                                                    .delegateConnectionId(DELEGATE_CONNECTION_ID)
                                                                    .version(getVersion())
                                                                    .location(Paths.get("").toAbsolutePath().toString())
                                                                    .build();
        HTimeLimiter.callInterruptible21(delegateHealthTimeLimiter, Duration.ofSeconds(15),
            ()
                -> executeRestCall(managerClient.doConnectionHeartbeat(DelegateAgentCommonVariables.getDelegateId(),
                    getDelegateConfiguration().getAccountId(), connectionHeartbeat)));
        lastHeartbeatSentAt.set(getClock().millis());
      } else {
        if (socket.status() == Socket.STATUS.OPEN || socket.status() == Socket.STATUS.REOPENED) {
          log.debug("Sending heartbeat...");
          HTimeLimiter.callInterruptible21(
              delegateHealthTimeLimiter, Duration.ofSeconds(15), () -> socket.fire(delegateParams));

        } else {
          log.warn("Socket is not open, status: {}", socket.status().toString());
        }
      }
      lastHeartbeatSentAt.set(getClock().millis());
      sentFirstHeartbeat.set(true);
    } catch (UncheckedTimeoutException ex) {
      log.warn("Timed out sending heartbeat", ex);
    } catch (Exception e) {
      log.error("Error sending heartbeat", e);
    }
  }

  private void handleMessageSubmit(final String message) {
    if (StringUtils.startsWith(message, TASK_EVENT_MARKER) || StringUtils.startsWith(message, ABORT_EVENT_MARKER)) {
      // For task events, continue in same thread. We will decode the task and assign it for execution.
      log.info("New Task event received: " + message);
      try {
        final DelegateTaskEvent delegateTaskEvent = JsonUtils.asObject(message, DelegateTaskEvent.class);
        try (TaskLogContext ignore = new TaskLogContext(delegateTaskEvent.getDelegateTaskId(), OVERRIDE_ERROR)) {
          if (delegateTaskEvent instanceof DelegateTaskAbortEvent) {
            taskExecutor.submit(() -> abortTask((DelegateTaskAbortEvent) delegateTaskEvent));
          } else {
            dispatchTaskAsync(delegateTaskEvent);
          }
        }
      } catch (Exception e) {
        log.error("Exception while decoding task", e);
      }
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug("^^MSG: " + message);
    }

    // Handle Heartbeat message in Health-monitor thread-pool.
    if (StringUtils.startsWith(message, HEARTBEAT_RESPONSE)) {
      DelegateHeartbeatResponseStreaming delegateHeartbeatResponse =
          JsonUtils.asObject(message, DelegateHeartbeatResponseStreaming.class);
      healthMonitorExecutor.submit(() -> processHeartbeat(delegateHeartbeatResponse));
      return;
    }

    // Handle other messages in task executor thread-pool.
    taskExecutor.submit(() -> handleMessage(message));
  }

  @SuppressWarnings("PMD")
  private void handleMessage(final String message) {
    if (StringUtils.equals(message, SELF_DESTRUCT)) {
      initiateSelfDestruct();
    } else if (StringUtils.equals(message, SELF_DESTRUCT + DelegateAgentCommonVariables.getDelegateId())) {
      initiateSelfDestruct();
    } else if (StringUtils.startsWith(message, SELF_DESTRUCT)) {
      if (StringUtils.startsWith(message, SELF_DESTRUCT + DelegateAgentCommonVariables.getDelegateId() + "-")) {
        int len = (SELF_DESTRUCT + DelegateAgentCommonVariables.getDelegateId() + "-").length();
        if (message.substring(len).equals(DELEGATE_CONNECTION_ID)) {
          initiateSelfDestruct();
        }
      }
    } else if (StringUtils.contains(message, INVALID_TOKEN.name())) {
      log.warn("Delegate used invalid token. Self destruct procedure will be initiated.");
      initiateSelfDestruct();
    } else if (StringUtils.contains(message, EXPIRED_TOKEN.name())) {
      log.warn("Delegate used expired token. It will be frozen and drained.");
      freeze();
    } else if (StringUtils.contains(message, REVOKED_TOKEN.name())) {
      log.warn("Delegate used revoked token. It will be frozen and drained.");
      freeze();
    } else {
      log.warn("Delegate received unhandled message {}", message);
    }
  }

  private void dispatchTaskAsync(final DelegateTaskEvent delegateTaskEvent) {
    final String delegateTaskId = delegateTaskEvent.getDelegateTaskId();
    if (delegateTaskId == null) {
      log.warn("Delegate task id cannot be null");
      return;
    }

    if (!shouldContactManager()) {
      log.info("Dropping task {}, self destruct in progress", delegateTaskId);
      return;
    }

    if (onPreExecute(delegateTaskEvent, delegateTaskId)) {
      log.info("Dropping task {}, pre execute checks failed", delegateTaskId);
      return;
    }

    log.info("TaskId: {} submitted for execution", delegateTaskId);
    final Future<?> taskFuture = getTaskExecutor().submit(() -> dispatchDelegateTask(delegateTaskEvent));

    onPostExecute(delegateTaskId, taskFuture);
  }

  private void processHeartbeat(DelegateHeartbeatResponseStreaming delegateHeartbeatResponse) {
    String receivedId = delegateHeartbeatResponse.getDelegateId();
    if (DelegateAgentCommonVariables.getDelegateId().equals(receivedId)) {
      final long now = getClock().millis();
      final long diff = now - lastHeartbeatSentAt.longValue();
      if (diff > TimeUnit.MINUTES.toMillis(3)) {
        log.warn(
            "Delegate {} received heartbeat response {} after sending. {} since last recorded heartbeat response. Harness sent response {} back",
            receivedId, getDurationString(lastHeartbeatSentAt.get(), now),
            getDurationString(lastHeartbeatReceivedAt.get(), now),
            getDurationString(delegateHeartbeatResponse.getResponseSentAt(), now));
      } else {
        log.info("Delegate {} received heartbeat response {} after sending. {} since last response.", receivedId,
            getDurationString(lastHeartbeatSentAt.get(), now), getDurationString(lastHeartbeatReceivedAt.get(), now));
      }
      lastHeartbeatReceivedAt.set(now);
    } else {
      log.info("Heartbeat response for another delegate received: {}", receivedId);
    }
  }

  private void openWebsocket() throws IOException {
    client = org.atmosphere.wasync.ClientFactory.getDefault().newClient();

    RequestBuilder requestBuilder = prepareRequestBuilder();

    Options clientOptions = client.newOptionsBuilder().runtime(asyncHttpClient, true).reconnect(true).build();
    socket = client.create(clientOptions);
    socket
        .on(Event.MESSAGE,
            new Function<String>() { // Do not change this, wasync doesn't like lambdas
              @Override
              public void on(String message) {
                handleMessageSubmit(message);
              }
            })
        .on(Event.ERROR,
            new Function<Exception>() { // Do not change this, wasync doesn't like lambdas
              @Override
              public void on(Exception e) {
                log.error("Exception on websocket", e);
                handleError(e);
              }
            })
        .on(Event.OPEN,
            new Function<Object>() { // Do not change this, wasync doesn't like lambdas
              @Override
              public void on(Object o) {
                handleOpen(o);
              }
            })
        .on(Event.CLOSE,
            new Function<Object>() { // Do not change this, wasync doesn't like lambdas
              @Override
              public void on(Object o) {
                handleClose(o);
              }
            })
        .on(new Function<IOException>() {
          @Override
          public void on(IOException ioe) {
            log.error("Error occured while starting Delegate", ioe);
          }
        })
        .on(new Function<TransportNotSupported>() {
          public void on(TransportNotSupported ex) {
            log.error("Connection was terminated forcefully (most likely), trying to reconnect", ex);
            trySocketReconnect();
          }
        });

    socket.open(requestBuilder.build());
  }

  private RequestBuilder prepareRequestBuilder() {
    try {
      URIBuilder uriBuilder =
          new URIBuilder(getDelegateConfiguration().getManagerUrl().replace("/api/", "/stream/") + "delegate/"
              + getDelegateConfiguration().getAccountId())
              .addParameter("delegateId", DelegateAgentCommonVariables.getDelegateId())
              .addParameter("delegateTokenName", DelegateAgentCommonVariables.getDelegateTokenName())
              .addParameter("delegateConnectionId", DELEGATE_CONNECTION_ID)
              .addParameter("token", tokenGenerator.getToken("https", "localhost", 9090, HOST_NAME))
              .addParameter("version", getVersion());

      URI uri = uriBuilder.build();

      // Stream the request body
      final RequestBuilder requestBuilder = client.newRequestBuilder().method(Request.METHOD.GET).uri(uri.toString());

      requestBuilder
          .encoder(new Encoder<DelegateParams, Reader>() { // Do not change this, wasync doesn't like lambdas
            @Override
            public Reader encode(final DelegateParams s) {
              return new StringReader(JsonUtils.asJson(s));
            }
          })
          .transport(Request.TRANSPORT.WEBSOCKET);

      // send accountId + delegateId as header for delegate gateway to log websocket connection with account.
      requestBuilder.header("accountId", this.getDelegateConfiguration().getAccountId());
      final String agent = "delegate/" + versionInfoManager.getVersionInfo().getVersion();
      requestBuilder.header("User-Agent", agent);
      requestBuilder.header("delegateId", DelegateAgentCommonVariables.getDelegateId());

      return requestBuilder;
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Unable to prepare uri", e);
    }
  }

  private void handleOpen(Object o) {
    log.info("Event:{}, message:[{}]", Event.OPEN.name(), o.toString());
  }

  private void handleClose(Object o) {
    log.info("Event:{}, message:[{}] trying to reconnect", Event.CLOSE.name(), o.toString());
    // TODO(brett): Disabling the fallback to poll for tasks as it can cause too much traffic to ingress controller
    // pollingForTasks.set(true);
    trySocketReconnect();
  }

  private void handleError(final Exception e) {
    log.info("Event:{}, message:[{}]", Event.ERROR.name(), e.getMessage());
    if (!reconnectingSocket.get()) { // Don't restart if we are trying to reconnect
      if (e instanceof SSLException || e instanceof TransportNotSupported) {
        log.warn("Reopening connection to manager because of exception", e);
        trySocketReconnect();
      } else if (e instanceof ConnectException) {
        log.warn("Failed to connect.", e);
      } else if (e instanceof ConcurrentModificationException) {
        log.warn("ConcurrentModificationException on WebSocket ignoring");
        log.debug("ConcurrentModificationException on WebSocket.", e);
      } else {
        log.error("Exception: ", e);
        try {
          finalizeSocket();
        } catch (final Exception ex) {
          log.error("Failed closing the socket!", ex);
        }
      }
    }
  }

  private void trySocketReconnect() {
    if (!closingSocket.get() && reconnectingSocket.compareAndSet(false, true)) {
      try {
        log.info("Starting socket reconnecting");
        FibonacciBackOff.executeForEver(() -> {
          final RequestBuilder requestBuilder = prepareRequestBuilder();
          try {
            final Socket skt = socket.open(requestBuilder.build(), 15, TimeUnit.SECONDS);
            log.info("Socket status: {}", socket.status().toString());
            if (socket.status() == Socket.STATUS.CLOSE || socket.status() == Socket.STATUS.ERROR) {
              throw new IllegalStateException("Socket not opened");
            }
            return skt;
          } catch (Exception e) {
            log.error("Failed to reconnect to socket, trying again: ", e);
            throw new IOException("Try reconnect again");
          }
        });
      } catch (IOException ex) {
        log.error("Unable to open socket", ex);
      } finally {
        reconnectingSocket.set(false);
        log.info("Finished socket reconnecting");
      }
    } else {
      log.warn("Socket already reconnecting {} or closing {}, will not start the reconnect procedure again",
          closingSocket.get(), reconnectingSocket.get());
    }
  }

  private void finalizeSocket() {
    closingSocket.set(true);
    socket.close();
  }

  private String registerDelegate(final DelegateParamsBuilder builder) {
    AtomicInteger attempts = new AtomicInteger(0);
    while (acquireTasks.get() && shouldContactManager()) {
      RestResponse<DelegateRegisterResponse> restResponse;
      try {
        attempts.incrementAndGet();
        String attemptString = attempts.get() > 1 ? " (Attempt " + attempts.get() + ")" : "";
        log.info("Registering delegate" + attemptString);
        DelegateParams delegateParams = builder.build()
                                            .toBuilder()
                                            .lastHeartBeat(getClock().millis())
                                            .delegateType(DELEGATE_TYPE)
                                            .description(getDelegateConfiguration().getDescription())
                                            //.proxy(set to true if there is a system proxy)
                                            .pollingModeEnabled(getDelegateConfiguration().isPollForTasks())
                                            .ceEnabled(Boolean.parseBoolean(System.getenv("ENABLE_CE")))
                                            .heartbeatAsObject(true)
                                            .build();
        restResponse =
            executeRestCall(managerClient.registerDelegate(getDelegateConfiguration().getAccountId(), delegateParams));
      } catch (Exception e) {
        String msg = "Unknown error occurred while registering Delegate [" + getDelegateConfiguration().getAccountId()
            + "] with manager";
        log.error(msg, e);
        sleep(ofMinutes(1));
        continue;
      }
      if (restResponse == null || restResponse.getResource() == null) {
        log.error(
            "Error occurred while registering delegate with manager for account '{}' - Please see the manager log for more information.",
            getDelegateConfiguration().getAccountId());
        sleep(ofMinutes(1));
        continue;
      }

      DelegateRegisterResponse delegateResponse = restResponse.getResource();
      String responseDelegateId = delegateResponse.getDelegateId();

      if (DelegateRegisterResponse.Action.SELF_DESTRUCT == delegateResponse.getAction()) {
        initiateSelfDestruct();
        sleep(ofMinutes(1));
        continue;
      }

      builder.delegateId(responseDelegateId);
      log.info("Delegate registered with id {}", responseDelegateId);
      return responseDelegateId;
    }

    // Didn't register and not acquiring. Exiting.
    System.exit(1);
    return null;
  }

  private void startTaskPolling() {
    taskPollExecutor.scheduleAtFixedRate(
        new Schedulable("Failed to poll for task", this::pollForTask), 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  private void pollForTask() {
    if (shouldContactManager()) {
      try {
        DelegateTaskEventsResponse taskEventsResponse =
            HTimeLimiter.callInterruptible21(delegateHealthTimeLimiter, Duration.ofSeconds(15),
                ()
                    -> executeRestCall(managerClient.pollTaskEvents(
                        DelegateAgentCommonVariables.getDelegateId(), getDelegateConfiguration().getAccountId())));
        if (shouldProcessDelegateTaskEvents(taskEventsResponse)) {
          List<DelegateTaskEvent> taskEvents = taskEventsResponse.getDelegateTaskEvents();
          log.info("Processing DelegateTaskEvents {}", taskEvents);
          processDelegateTaskEventsInBlockingLoop(taskEvents);
        }
      } catch (UncheckedTimeoutException tex) {
        log.warn("Timed out fetching delegate task events", tex);
      } catch (InterruptedException ie) {
        log.warn("Delegate service is being shut down, this task is being interrupted.", ie);
      } catch (Exception e) {
        log.error("Exception while decoding task", e);
      }
    }
  }

  private boolean shouldProcessDelegateTaskEvents(final DelegateTaskEventsResponse taskEventsResponse) {
    return taskEventsResponse != null && isNotEmpty(taskEventsResponse.getDelegateTaskEvents());
  }

  private void processDelegateTaskEventsInBlockingLoop(List<DelegateTaskEvent> taskEvents) {
    taskEvents.forEach(this::processDelegateTaskEvent);
  }

  private void processDelegateTaskEvent(DelegateTaskEvent taskEvent) {
    try (TaskLogContext ignore = new TaskLogContext(taskEvent.getDelegateTaskId(), OVERRIDE_ERROR)) {
      if (taskEvent instanceof DelegateTaskAbortEvent) {
        abortTask((DelegateTaskAbortEvent) taskEvent);
      } else {
        dispatchTaskAsync(taskEvent);
      }
    }
  }

  private boolean shouldContactManager() {
    return !selfDestruct.get();
  }

  private String getVersion() {
    return versionInfoManager.getVersionInfo().getVersion();
  }

  private void logProxyConfiguration() {
    final String proxyHost = System.getProperty("https.proxyHost");

    if (isBlank(proxyHost)) {
      log.info("No proxy settings. Configure in proxy.config if needed");
      return;
    }

    final String proxyScheme = System.getProperty("proxyScheme");
    final String proxyPort = System.getProperty("https.proxyPort");
    log.info("Using {} proxy {}:{}", proxyScheme, proxyHost, proxyPort);
    final String nonProxyHostsString = System.getProperty("http.nonProxyHosts");

    if (nonProxyHostsString == null || isBlank(nonProxyHostsString)) {
      return;
    }

    final String[] suffixes = nonProxyHostsString.split("\\|");
    final List<String> nonProxyHosts = Stream.of(suffixes).map(suffix -> suffix.substring(1)).collect(toList());
    log.info("No proxy for hosts with suffix in: {}", nonProxyHosts);
  }
}
