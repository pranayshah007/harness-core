/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.platform;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASK_EXECUTION_TIME;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASK_TIMEOUT;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.REVOKED_TOKEN;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.logging.Misc.getDurationString;
import static io.harness.network.Localhost.getLocalHostAddress;
import static io.harness.network.SafeHttpCall.execute;
import static io.harness.threading.Morpheus.sleep;
import static io.harness.utils.MemoryPerformanceUtils.memoryUsage;

import static software.wings.beans.TaskType.SCRIPT;
import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;

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
import io.harness.configuration.DeployMode;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.expression.DelegateExpressionEvaluator;
import io.harness.delegate.logging.DelegateStackdriverLogAppender;
import io.harness.delegate.service.common.AbstractDelegateAgentServiceImpl;
import io.harness.delegate.service.common.DelegateTaskExecutionData;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.delegate.task.validation.DelegateConnectionResultDetail;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.UnexpectedException;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.logging.AutoLogContext;
import io.harness.network.FibonacciBackOff;
import io.harness.rest.RestResponse;
import io.harness.security.TokenGenerator;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.JsonUtils;
import io.harness.threading.Schedulable;

import software.wings.beans.TaskType;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.delegatetasks.ActivityBasedLogSanitizer;
import software.wings.delegatetasks.LogSanitizer;
import software.wings.delegatetasks.bash.BashScriptTask;
import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.misc.MemoryHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
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
import retrofit2.Response;

@Slf4j
public class DelegatePlatformService extends AbstractDelegateAgentServiceImpl {
  private static final double RESOURCE_USAGE_THRESHOLD = 0.90;
  private static final int POLL_INTERVAL_SECONDS = 3;
  // Marker string to indicate task events.
  private static final String TASK_EVENT_MARKER = "{\"eventType\":\"DelegateTaskEvent\"";
  private static final String ABORT_EVENT_MARKER = "{\"eventType\":\"DelegateTaskAbortEvent\"";
  private static final String HEARTBEAT_RESPONSE = "{\"eventType\":\"DelegateHeartbeatResponseStreaming\"";

  private final AtomicBoolean rejectRequest = new AtomicBoolean();
  private final AtomicInteger heartbeatSuccessCalls = new AtomicInteger();
  private final AtomicLong lastHeartbeatSentAt = new AtomicLong(System.currentTimeMillis());
  private final AtomicLong lastHeartbeatReceivedAt = new AtomicLong(System.currentTimeMillis());

  private final AtomicInteger maxValidatingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingFuturesCount = new AtomicInteger();

  private final Set<String> currentlyAcquiringTasks = ConcurrentHashMap.newKeySet();
  private final Map<String, DelegateTaskPackage> currentlyValidatingTasks = new ConcurrentHashMap<>();
  private final Map<String, DelegateTaskPackage> currentlyExecutingTasks = new ConcurrentHashMap<>();
  private final Map<String, DelegateTaskExecutionData> currentlyExecutingFutures = new ConcurrentHashMap<>();

  @Inject @Named("healthMonitorExecutor") private ScheduledExecutorService healthMonitorExecutor;
  //  @Inject @Named("watcherMonitorExecutor") private ScheduledExecutorService watcherMonitorExecutor;
  //  @Inject @Named("inputExecutor") private ScheduledExecutorService inputExecutor;
  //  @Inject @Named("backgroundExecutor") private ExecutorService backgroundExecutor;
  //  @Inject @Named("taskPollExecutor") private ScheduledExecutorService taskPollExecutor;
  //  @Inject @Named("taskProgressExecutor") private ExecutorService taskProgressExecutor;
  //  @Inject @Named("grpcServiceExecutor") private ExecutorService grpcServiceExecutor;
  @Inject @Named("taskExecutor") private ThreadPoolExecutor taskExecutor;
  @Inject @Named("timeoutExecutor") private ThreadPoolExecutor timeoutEnforcement;

  @Inject private Injector injector;
  //  @Inject private DelegateTaskFactory delegateTaskFactory;
  // FIXME DelegateLogService and coresponding fields are needed for streaming logs to UI, but is an optional feature
  // and has heavy deps to 930-tasks and other big modules. Disabling in interest of time
  //  @Inject private DelegateLogService delegateLogService;
  //  @Inject(optional = true) @Nullable private LogStreamingClient logStreamingClient;
  //  @Inject(optional = true) @Nullable private DelegateServiceAgentClient delegateServiceAgentClient;
  //  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateDecryptionService delegateDecryptionService;
  @Inject private AsyncHttpClient asyncHttpClient;
  @Inject private TokenGenerator tokenGenerator;

  private TimeLimiter delegateHealthTimeLimiter;
  private TimeLimiter delegateTaskTimeLimiter;
  private double maxProcessRSSThresholdMB;
  private double maxPodRSSThresholdMB;
  private Client client;
  @Getter(AccessLevel.PROTECTED) private Socket socket;

  @Getter(lazy = true)
  private final ImmutableMap<String, ThreadPoolExecutor> logExecutors =
      NullSafeImmutableMap.<String, ThreadPoolExecutor>builder()
          .putIfNotNull("taskExecutor", taskExecutor)
          .putIfNotNull("timeoutEnforcement", timeoutEnforcement)
          .build();

  @Override
  public void run(final boolean watched, final boolean isImmutableDelegate) {
    try {
      initDelegateProcess();
    } catch (Exception e) {
      log.error("Exception while starting/running delegate", e);
    }
  }

  @Override
  public void stop() {
    log.info("Stopping delegate platform service, nothing to do!");
  }

  private void initDelegateProcess() {
    try {
      log.info("Delegate will start running on JRE {}", System.getProperty("java.version"));
      log.info("The deploy mode for delegate is [{}]", System.getenv("DEPLOY_MODE"));

      delegateHealthTimeLimiter = HTimeLimiter.create(healthMonitorExecutor);
      delegateTaskTimeLimiter = HTimeLimiter.create(taskExecutor);
      DelegateStackdriverLogAppender.setTimeLimiter(delegateHealthTimeLimiter);
      DelegateStackdriverLogAppender.setManagerClient(getDelegateAgentManagerClient());

      logProxyConfiguration();

      log.info("Delegate process started");
      if (getDelegateConfiguration().isGrpcServiceEnabled()) {
        getRestartableServiceManager().start();
      }

      long start = getClock().millis();
      final String delegateDescription = System.getenv().get("DELEGATE_DESCRIPTION");
      String descriptionFromConfigFile = isBlank(delegateDescription) ? "" : delegateDescription;
      String description = "description here".equals(getDelegateConfiguration().getDescription())
          ? descriptionFromConfigFile
          : getDelegateConfiguration().getDescription();

      if (isNotEmpty(DELEGATE_NAME)) {
        log.info("Registering delegate with delegate name: {}", DELEGATE_NAME);
      }

      String delegateProfile = System.getenv().get("DELEGATE_PROFILE");
      if (isNotBlank(delegateProfile)) {
        log.info("Registering delegate with delegate profile: {}", delegateProfile);
      }

      final List<String> supportedTasks = Arrays.stream(TaskType.values()).map(Enum::name).collect(toList());

      // Remove tasks which are in TaskTypeV2 and only specified with onlyV2 as true
      final List<String> unsupportedTasks =
          Arrays.stream(TaskType.values()).filter(TaskType::isUnsupported).map(Enum::name).collect(toList());

      if (BLOCK_SHELL_TASK) {
        log.info("Delegate is blocked from executing shell script tasks.");
        unsupportedTasks.add(SCRIPT.name());
        unsupportedTasks.add(SHELL_SCRIPT_TASK_NG.name());
      }

      supportedTasks.removeAll(unsupportedTasks);

      if (isNotBlank(DELEGATE_TYPE)) {
        log.info("Registering delegate with delegate Type: {}, DelegateGroupName: {} that supports tasks: {}",
            DELEGATE_TYPE, DELEGATE_GROUP_NAME, supportedTasks);
      }

      final DelegateParams.DelegateParamsBuilder builder =
          DelegateParams.builder()
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
              .supportedTaskTypes(supportedTasks)
              //.proxy(set to true if there is a system proxy)
              .pollingModeEnabled(getDelegateConfiguration().isPollForTasks())
              .ng(DELEGATE_NG)
              .tags(isNotBlank(DELEGATE_TAGS) ? Arrays.asList(DELEGATE_TAGS.trim().split("\\s*,+\\s*,*\\s*"))
                                              : emptyList())
              .sampleDelegate(false)
              .location(Paths.get("").toAbsolutePath().toString())
              .heartbeatAsObject(true)
              .immutable(getDelegateConfiguration().isImmutable())
              .ceEnabled(Boolean.parseBoolean(System.getenv("ENABLE_CE")));

      final String delegateId = registerDelegate(builder);

      DelegateAgentCommonVariables.setDelegateId(delegateId);
      log.info("[New] Delegate registered in {} ms", getClock().millis() - start);
      DelegateStackdriverLogAppender.setDelegateId(delegateId);
      if (getDelegateConfiguration().isDynamicHandlingOfRequestEnabled()
          && DeployMode.KUBERNETES.name().equals(System.getenv().get(DeployMode.DEPLOY_MODE))) {
        // Enable dynamic throttling of requests only for kubernetes pod(s)
        startDynamicHandlingOfTasks();
      }

      if (getDelegateConfiguration().isPollForTasks()) {
        log.info("Polling is enabled for Delegate");
        startHeartbeat(builder);
        startTaskPolling();
      } else {
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

        startHeartbeat(builder);
      }

      log.info("Delegate started with config {} ", getDelegateConfiguration());
      log.info("Manager Authority:{}, Manager Target:{}", getDelegateConfiguration().getManagerAuthority(),
          getDelegateConfiguration().getManagerTarget());

      //      if (delegateLocalConfigService.isLocalConfigPresent()) {
      //        Map<String, String> localSecrets = delegateLocalConfigService.getLocalDelegateSecrets();
      //        if (isNotEmpty(localSecrets)) {
      //          delegateLogService.registerLogSanitizer(new GenericLogSanitizer(new
      //          HashSet<>(localSecrets.values())));
      //        }
      //      }
    } catch (RuntimeException | IOException e) {
      log.error("Exception while starting/running delegate", e);
    }
  }

  private String registerDelegate(final DelegateParams.DelegateParamsBuilder builder) {
    AtomicInteger attempts = new AtomicInteger(0);
    while (getAcquireTasks().get() && shouldContactManager()) {
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
        restResponse = executeRestCall(getDelegateAgentManagerClient().registerDelegate(
            getDelegateConfiguration().getAccountId(), delegateParams));
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
    getTaskPollExecutor().scheduleAtFixedRate(
        new Schedulable("Failed to poll for task", this::pollForTask), 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  private void pollForTask() {
    if (shouldContactManager()) {
      try {
        DelegateTaskEventsResponse taskEventsResponse =
            HTimeLimiter.callInterruptible21(delegateTaskTimeLimiter, Duration.ofSeconds(15),
                ()
                    -> executeRestCall(getDelegateAgentManagerClient().pollTaskEvents(
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

  private void startHeartbeat(DelegateParams.DelegateParamsBuilder builder) {
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
      // Log delegate performance after every 60 sec i.e. heartbeat interval.
      logCurrentTasks();
    }, 0, getDelegateConfiguration().getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
  }

  private void sendHeartbeat(DelegateParams.DelegateParamsBuilder builder) {
    if (!shouldContactManager() || !getAcquireTasks().get() || getFrozen().get()) {
      return;
    }

    DelegateParams delegateParams =
        builder.build()
            .toBuilder()
            .lastHeartBeat(getClock().millis())
            .pollingModeEnabled(getDelegateConfiguration().isPollForTasks())
            .heartbeatAsObject(true)
            .currentlyExecutingDelegateTasks(
                currentlyExecutingTasks.values().stream().map(DelegateTaskPackage::getDelegateTaskId).collect(toList()))
            .location(Paths.get("").toAbsolutePath().toString())
            .build();

    try {
      if (getDelegateConfiguration().isPollForTasks()) {
        RestResponse<DelegateHeartbeatResponse> delegateParamsResponse =
            executeRestCall(getDelegateAgentManagerClient().delegateHeartbeat(
                getDelegateConfiguration().getAccountId(), delegateParams));

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
                -> executeRestCall(
                    getDelegateAgentManagerClient().doConnectionHeartbeat(DelegateAgentCommonVariables.getDelegateId(),
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

  private void handleMessageSubmit(String message) {
    if (StringUtils.startsWith(message, TASK_EVENT_MARKER) || StringUtils.startsWith(message, ABORT_EVENT_MARKER)) {
      // For task events, continue in same thread. We will decode the task and assign it for execution.
      log.info("New Task event received: " + message);
      try {
        DelegateTaskEvent delegateTaskEvent = JsonUtils.asObject(message, DelegateTaskEvent.class);
        try (TaskLogContext ignore = new TaskLogContext(delegateTaskEvent.getDelegateTaskId(), OVERRIDE_ERROR)) {
          if (!(delegateTaskEvent instanceof DelegateTaskAbortEvent)) {
            dispatchDelegateTaskAsync(delegateTaskEvent);
          } else {
            taskExecutor.submit(() -> abortDelegateTask((DelegateTaskAbortEvent) delegateTaskEvent));
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
  private void handleMessage(String message) {
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
      final String agent = "delegate/" + getVersionInfoManager().getVersionInfo().getVersion();
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

  private void startDynamicHandlingOfTasks() {
    log.info("Starting dynamic handling of tasks tp {} ms", 1000);
    try {
      maxProcessRSSThresholdMB = MemoryHelper.getProcessMaxMemoryMB() * RESOURCE_USAGE_THRESHOLD;
      maxPodRSSThresholdMB = MemoryHelper.getPodMaxMemoryMB() * RESOURCE_USAGE_THRESHOLD;

      if (maxPodRSSThresholdMB < 1 || maxProcessRSSThresholdMB < 1) {
        log.error("Error while fetching memory information, will not enable dynamic handling of tasks");
        return;
      }

      healthMonitorExecutor.scheduleAtFixedRate(() -> {
        try {
          maybeUpdateTaskRejectionStatus();
        } catch (Exception ex) {
          log.error("Exception while determining delegate behaviour", ex);
        }
      }, 0, 5, TimeUnit.SECONDS);
    } catch (Exception ex) {
      log.error("Error while fetching memory information, will not enable dynamic handling of tasks");
    }
  }

  private void maybeUpdateTaskRejectionStatus() {
    final long currentRSSMB = MemoryHelper.getProcessMemoryMB();
    if (currentRSSMB >= maxProcessRSSThresholdMB) {
      log.warn(
          "Reached resource threshold, temporarily reject incoming task request. CurrentProcessRSSMB {} ThresholdMB {}",
          currentRSSMB, maxProcessRSSThresholdMB);
      rejectRequest.compareAndSet(false, true);
      return;
    }

    final long currentPodRSSMB = MemoryHelper.getPodRSSFromCgroupMB();
    if (currentPodRSSMB >= maxPodRSSThresholdMB) {
      log.warn(
          "Reached resource threshold, temporarily reject incoming task request. CurrentPodRSSMB {} ThresholdMB {}",
          currentPodRSSMB, maxPodRSSThresholdMB);
      rejectRequest.compareAndSet(false, true);
      return;
    }
    log.debug("Process info CurrentProcessRSSMB {} ThresholdProcessMB {} currentPodRSSMB {} ThresholdPodMemoryMB {}",
        currentRSSMB, maxProcessRSSThresholdMB, currentPodRSSMB, maxPodRSSThresholdMB);

    if (rejectRequest.compareAndSet(true, false)) {
      log.info(
          "Accepting incoming task request. CurrentProcessRSSMB {} ThresholdProcessMB {} currentPodRSSMB {} ThresholdPodMemoryMB {}",
          currentRSSMB, maxProcessRSSThresholdMB, currentPodRSSMB, maxPodRSSThresholdMB);
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
        abortDelegateTask((DelegateTaskAbortEvent) taskEvent);
      } else {
        dispatchDelegateTaskAsync(taskEvent);
      }
    }
  }

  private void abortDelegateTask(DelegateTaskAbortEvent delegateTaskEvent) {
    log.info("Aborting task {}", delegateTaskEvent);
    currentlyValidatingTasks.remove(delegateTaskEvent.getDelegateTaskId());
    log.info("Removed from validating futures on abort");

    Optional.ofNullable(currentlyExecutingFutures.get(delegateTaskEvent.getDelegateTaskId()).getTaskFuture())
        .ifPresent(future -> future.cancel(true));
    currentlyExecutingTasks.remove(delegateTaskEvent.getDelegateTaskId());
    if (currentlyExecutingFutures.remove(delegateTaskEvent.getDelegateTaskId()) != null) {
      log.info("Removed from executing futures on abort");
    }
  }

  private void dispatchDelegateTaskAsync(DelegateTaskEvent delegateTaskEvent) {
    String delegateTaskId = delegateTaskEvent.getDelegateTaskId();
    if (delegateTaskId == null) {
      log.warn("Delegate task id cannot be null");
      return;
    }

    if (!shouldContactManager()) {
      log.info("Dropping task, self destruct in progress: " + delegateTaskId);
      return;
    }

    if (rejectRequest.get()) {
      log.info("Delegate running out of resources, dropping this request [{}] " + delegateTaskId);
      return;
    }

    if (currentlyExecutingFutures.containsKey(delegateTaskEvent.getDelegateTaskId())) {
      log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
      return;
    }

    DelegateTaskExecutionData taskExecutionData = DelegateTaskExecutionData.builder().build();
    if (currentlyExecutingFutures.putIfAbsent(delegateTaskId, taskExecutionData) == null) {
      final Future<?> taskFuture = taskExecutor.submit(() -> dispatchDelegateTask(delegateTaskEvent));
      log.info("TaskId: {} submitted for execution", delegateTaskId);
      taskExecutionData.setTaskFuture(taskFuture);
      updateCounterIfLessThanCurrent(maxExecutingFuturesCount, currentlyExecutingFutures.size());
      return;
    }

    log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
  }

  private void dispatchDelegateTask(DelegateTaskEvent delegateTaskEvent) {
    try (TaskLogContext ignore = new TaskLogContext(delegateTaskEvent.getDelegateTaskId(), OVERRIDE_ERROR)) {
      log.info("DelegateTaskEvent received - {}", delegateTaskEvent);
      String delegateTaskId = delegateTaskEvent.getDelegateTaskId();

      try {
        if (getFrozen().get()) {
          log.info(
              "Delegate process with detected time out of sync or with revoked token is running. Won't acquire tasks.");
          return;
        }

        if (!getAcquireTasks().get()) {
          log.info("[Old] Upgraded process is running. Won't acquire task while completing other tasks");
          return;
        }

        if (currentlyAcquiringTasks.contains(delegateTaskId)) {
          log.info("Task [DelegateTaskEvent: {}] currently acquiring. Don't acquire again", delegateTaskEvent);
          return;
        }

        if (currentlyValidatingTasks.containsKey(delegateTaskId)) {
          log.info("Task [DelegateTaskEvent: {}] already validating. Don't validate again", delegateTaskEvent);
          return;
        }

        currentlyAcquiringTasks.add(delegateTaskId);

        log.debug("Try to acquire DelegateTask - accountId: {}", getDelegateConfiguration().getAccountId());

        DelegateTaskPackage delegateTaskPackage =
            executeRestCall(getDelegateAgentManagerClient().acquireTask(DelegateAgentCommonVariables.getDelegateId(),
                delegateTaskId, getDelegateConfiguration().getAccountId(), DELEGATE_INSTANCE_ID));
        if (delegateTaskPackage == null || delegateTaskPackage.getData() == null) {
          if (delegateTaskPackage == null) {
            log.warn("Delegate task package is null for task: {} - accountId: {}", delegateTaskId,
                delegateTaskEvent.getAccountId());
          } else {
            log.warn("Delegate task data not available for task: {} - accountId: {}", delegateTaskId,
                delegateTaskEvent.getAccountId());
          }
          return;
        } else {
          log.info("received task package {} for delegateInstance {}", delegateTaskPackage, DELEGATE_INSTANCE_ID);
        }

        if (isEmpty(delegateTaskPackage.getDelegateInstanceId())
            || DELEGATE_INSTANCE_ID.equals(delegateTaskPackage.getDelegateInstanceId())) {
          applyDelegateSecretFunctor(delegateTaskPackage);
          // Whitelisted. Proceed immediately.
          log.info("Delegate {} whitelisted for task and accountId: {}", DelegateAgentCommonVariables.getDelegateId(),
              getDelegateConfiguration().getAccountId());
          executeTask(delegateTaskPackage);
        } else {
          throw new IllegalArgumentException("Delegate received a task intended for delegate with instanceId "
              + delegateTaskPackage.getDelegateInstanceId());
        }

      } catch (IOException e) {
        log.error("Unable to get task for validation", e);
      } finally {
        currentlyAcquiringTasks.remove(delegateTaskId);
        currentlyExecutingFutures.remove(delegateTaskId);
      }
    }
  }

  private Consumer<List<DelegateConnectionResult>> getPostValidationFunction(
      DelegateTaskEvent delegateTaskEvent, String taskId) {
    return delegateConnectionResults -> {
      try (AutoLogContext ignored = new TaskLogContext(taskId, OVERRIDE_ERROR)) {
        // Tools might be installed asynchronously, so get the flag early on
        currentlyValidatingTasks.remove(taskId);
        log.info("Removed from validating futures on post validation");
        List<DelegateConnectionResult> results = Optional.ofNullable(delegateConnectionResults).orElse(emptyList());
        boolean validated = results.stream().allMatch(DelegateConnectionResult::isValidated);
        log.info("Validation {} for task", validated ? "succeeded" : "failed");
        try {
          DelegateTaskPackage delegateTaskPackage = execute(
              getDelegateAgentManagerClient().reportConnectionResults(DelegateAgentCommonVariables.getDelegateId(),
                  delegateTaskEvent.getDelegateTaskId(), getDelegateConfiguration().getAccountId(),
                  DELEGATE_INSTANCE_ID, getDelegateConnectionResultDetails(results)));

          if (delegateTaskPackage != null && delegateTaskPackage.getData() != null
              && DELEGATE_INSTANCE_ID.equals(delegateTaskPackage.getDelegateInstanceId())) {
            applyDelegateSecretFunctor(delegateTaskPackage);
            executeTask(delegateTaskPackage);
          } else {
            log.info("Did not get the go-ahead to proceed for task");
            if (validated) {
              log.info("Task validated but was not assigned");
            }
          }
        } catch (IOException e) {
          log.error("Unable to report validation results for task", e);
        }
      }
    };
  }

  private List<DelegateConnectionResultDetail> getDelegateConnectionResultDetails(
      List<DelegateConnectionResult> results) {
    List<DelegateConnectionResultDetail> delegateConnectionResultDetails = new ArrayList<>();
    for (DelegateConnectionResult source : results) {
      DelegateConnectionResultDetail target = DelegateConnectionResultDetail.builder().build();
      target.setAccountId(source.getAccountId());
      target.setCriteria(source.getCriteria());
      target.setDelegateId(source.getDelegateId());
      target.setDuration(source.getDuration());
      target.setLastUpdatedAt(source.getLastUpdatedAt());
      target.setUuid(source.getUuid());
      target.setValidated(source.isValidated());
      target.setValidUntil(source.getValidUntil());
      delegateConnectionResultDetails.add(target);
    }

    return delegateConnectionResultDetails;
  }

  private void executeTask(@NotNull DelegateTaskPackage delegateTaskPackage) {
    TaskData taskData = delegateTaskPackage.getData();

    log.debug("DelegateTask acquired - accountId: {}, taskType: {}", getDelegateConfiguration().getAccountId(),
        taskData.getTaskType());
    Pair<String, Set<String>> activitySecrets = obtainActivitySecrets(delegateTaskPackage);
    Optional<LogSanitizer> sanitizer = getLogSanitizer(activitySecrets);
    //    ILogStreamingTaskClient logStreamingTaskClient = getLogStreamingTaskClient(activitySecrets,
    //    delegateTaskPackage);
    // At the moment used to download and render terraform json plan file and keep track of the download tf plans
    // so we can clean up at the end of the task. Expected mainly to be used in Shell Script Task
    // but not limited to usage in other tasks
    //    DelegateExpressionEvaluator delegateExpressionEvaluator = new DelegateExpressionEvaluator(
    //        injector, delegateTaskPackage.getAccountId(), delegateTaskPackage.getData().getExpressionFunctorToken());
    //    applyDelegateExpressionEvaluator(delegateTaskPackage, delegateExpressionEvaluator);

    final TaskType taskType = TaskType.valueOf(taskData.getTaskType());
    if (taskType != SCRIPT && taskType != SHELL_SCRIPT_TASK_NG) {
      throw new IllegalArgumentException("PlatformDelegate can only take shel script tasks");
    }

    final BooleanSupplier preExecutionFunction = getPreExecutionFunction(delegateTaskPackage);
    final Consumer<DelegateTaskResponse> postExecutionFunction =
        getPostExecutionFunction(delegateTaskPackage.getDelegateTaskId());

    //    DelegateRunnableTask delegateRunnableTask = delegateTaskFactory.getDelegateRunnableTask(
    //        taskType, delegateTaskPackage, logStreamingTaskClient, postExecutionFunction, preExecutionFunction);
    final BashScriptTask delegateRunnableTask =
        new BashScriptTask(delegateTaskPackage, preExecutionFunction, postExecutionFunction);

    //    ((AbstractDelegateRunnableTask) delegateRunnableTask).setDelegateHostname(HOST_NAME);
    injector.injectMembers(delegateRunnableTask);
    currentlyExecutingFutures.get(delegateTaskPackage.getDelegateTaskId()).setExecutionStartTime(getClock().millis());

    // Submit execution for watching this task execution.
    timeoutEnforcement.submit(() -> enforceDelegateTaskTimeout(delegateTaskPackage.getDelegateTaskId(), taskData));

    // Start task execution in same thread and measure duration.
    if (getDelegateConfiguration().isImmutable()) {
      getMetricRegistry().recordGaugeDuration(
          TASK_EXECUTION_TIME, new String[] {DELEGATE_NAME, taskData.getTaskType()}, delegateRunnableTask);
    } else {
      delegateRunnableTask.run();
    }
  }

  private Pair<String, Set<String>> obtainActivitySecrets(@NotNull DelegateTaskPackage delegateTaskPackage) {
    TaskData taskData = delegateTaskPackage.getData();

    String activityId = null;
    Set<String> secrets = new HashSet<>(delegateTaskPackage.getSecrets());

    // Add other system secrets
    addSystemSecrets(secrets);

    // TODO: This gets secrets for Shell Script, Shell Script Provision, and Command only
    // When secret decryption is moved to delegate for each task then those secrets can be used instead.
    Object[] parameters = taskData.getParameters();
    if (parameters.length == 1 && parameters[0] instanceof TaskParameters) {
      if (parameters[0] instanceof ActivityAccess) {
        activityId = ((ActivityAccess) parameters[0]).getActivityId();
      }

      if (parameters[0] instanceof ShellScriptParameters) {
        // Shell Script
        ShellScriptParameters shellScriptParameters = (ShellScriptParameters) parameters[0];
        secrets.addAll(secretsFromMaskedVariables(
            shellScriptParameters.getServiceVariables(), shellScriptParameters.getSafeDisplayServiceVariables()));
      } /* else if (parameters[0] instanceof ShellScriptProvisionParameters) {
         // Shell Script Provision
         ShellScriptProvisionParameters shellScriptProvisionParameters = (ShellScriptProvisionParameters) parameters[0];
         Map<String, EncryptedDataDetail> encryptedVariables = shellScriptProvisionParameters.getEncryptedVariables();
         if (isNotEmpty(encryptedVariables)) {
           for (Map.Entry<String, EncryptedDataDetail> encryptedVariable : encryptedVariables.entrySet()) {
             secrets.add(String.valueOf(encryptionService.getDecryptedValue(encryptedVariable.getValue(), false)));
           }
         }
       } else if (parameters[0] instanceof CommandParameters) {
         // Command
         CommandParameters commandParameters = (CommandParameters) parameters[0];
         activityId = commandParameters.getActivityId();
         secrets.addAll(secretsFromMaskedVariables(
                 commandParameters.getServiceVariables(), commandParameters.getSafeDisplayServiceVariables()));
       }*/
      //    } else {
      //      if (parameters.length >= 2 && parameters[0] instanceof Command
      //              && parameters[1] instanceof CommandExecutionContext) {
      //        // Command
      //        CommandExecutionContext context = (CommandExecutionContext) parameters[1];
      //        activityId = context.getActivityId();
      //        secrets.addAll(
      //                secretsFromMaskedVariables(context.getServiceVariables(),
      //                context.getSafeDisplayServiceVariables()));
      //      }
    }

    return Pair.of(activityId, secrets);
  }

  private void addSystemSecrets(Set<String> secrets) {
    // Add config file secrets
    secrets.add(getDelegateConfiguration().getDelegateToken());

    // Add environment variable secrets
    String delegateProfileId = System.getenv().get("DELEGATE_PROFILE");
    if (isNotBlank(delegateProfileId)) {
      secrets.add(delegateProfileId);
    }

    String proxyUser = System.getenv().get("PROXY_USER");
    if (isNotBlank(proxyUser)) {
      secrets.add(proxyUser);
    }

    String proxyPassword = System.getenv().get("PROXY_PASSWORD");
    if (isNotBlank(proxyPassword)) {
      secrets.add(proxyPassword);
    }
  }

  /**
   * Create set of secrets from two maps. Both contain all variables, secret and plain.
   * The first does not mask secrets while the second does
   *
   * @param serviceVariables            contains all variables, secret and plain, unmasked
   * @param safeDisplayServiceVariables contains all variables with secret ones masked
   * @return set of secret variable values, unmasked
   */
  private static Set<String> secretsFromMaskedVariables(
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables) {
    Set<String> secrets = new HashSet<>();
    if (isNotEmpty(serviceVariables) && isNotEmpty(safeDisplayServiceVariables)) {
      for (Map.Entry<String, String> entry : safeDisplayServiceVariables.entrySet()) {
        if (SECRET_MASK.equals(entry.getValue())) {
          secrets.add(serviceVariables.get(entry.getKey()));
        }
      }
    }
    return secrets;
  }

  private BooleanSupplier getPreExecutionFunction(@NotNull DelegateTaskPackage delegateTaskPackage) {
    return () -> {
      //      if (logStreamingTaskClient != null) {
      //        try {
      //          // Opens the log stream for task
      //          logStreamingTaskClient.openStream(null);
      //        } catch (Exception ex) {
      //          log.error("Unexpected error occurred while opening the log stream.");
      //        }
      //      }

      if (!currentlyExecutingTasks.containsKey(delegateTaskPackage.getDelegateTaskId())) {
        log.debug("Adding task to executing tasks");
        currentlyExecutingTasks.put(delegateTaskPackage.getDelegateTaskId(), delegateTaskPackage);
        updateCounterIfLessThanCurrent(maxExecutingTasksCount, currentlyExecutingTasks.size());
        //        if (sanitizer != null) {
        //          delegateLogService.registerLogSanitizer(sanitizer);
        //        }
        return true;
      } else {
        // We should have already checked this before acquiring this task. If we here, than we
        // should log an error and abort execution.
        log.error("Task is already being executed");
        return false;
      }
    };
  }

  private Consumer<DelegateTaskResponse> getPostExecutionFunction(String taskId) {
    return taskResponse -> {
      //      if (logStreamingTaskClient != null) {
      //        try {
      //          // Closes the log stream for the task
      //          logStreamingTaskClient.closeStream(null);
      //        } catch (Exception ex) {
      //          log.error("Unexpected error occurred while closing the log stream.");
      //        }
      //      }

      Response<ResponseBody> response = null;
      try {
        int retries = 5;
        for (int attempt = 0; attempt < retries; attempt++) {
          response = getDelegateAgentManagerClient()
                         .sendTaskStatus(DelegateAgentCommonVariables.getDelegateId(), taskId,
                             getDelegateConfiguration().getAccountId(), taskResponse)
                         .execute();
          if (response.code() >= 200 && response.code() <= 299) {
            log.info("Task {} response sent to manager", taskId);
            break;
          }
          log.warn("Failed to send response for task {}: {}. error: {}. requested url: {} {}", taskId, response.code(),
              response.errorBody() == null ? "null" : response.errorBody().string(), response.raw().request().url(),
              attempt < (retries - 1) ? "Retrying." : "Giving up.");
          if (attempt < retries - 1) {
            // Do not sleep for last loop round, as we are going to fail.
            sleep(ofSeconds(FibonacciBackOff.getFibonacciElement(attempt)));
          }
        }
      } catch (Exception e) {
        log.error("Unable to send response to manager", e);
      } finally {
        //        if (sanitizer != null) {
        //          delegateLogService.unregisterLogSanitizer(sanitizer);
        //        }

        currentlyExecutingTasks.remove(taskId);
        if (currentlyExecutingFutures.remove(taskId) != null) {
          log.debug("Removed from executing futures on post execution");
        }
        if (response != null && response.errorBody() != null && !response.isSuccessful()) {
          response.errorBody().close();
        }
        if (response != null && response.body() != null && response.isSuccessful()) {
          response.body().close();
        }
      }
    };
  }

  private void enforceDelegateTaskTimeout(String taskId, TaskData taskData) {
    long startingTime = currentlyExecutingFutures.get(taskId).getExecutionStartTime();
    boolean stillRunning = true;
    long timeout = taskData.getTimeout() + TimeUnit.SECONDS.toMillis(30L);
    Future<?> taskFuture = null;
    while (stillRunning && getClock().millis() - startingTime < timeout) {
      log.info("Task time remaining for {}, taskype {}: {} ms", taskId, taskData.getTaskType(),
          startingTime + timeout - getClock().millis());
      sleep(ofSeconds(5));
      taskFuture = currentlyExecutingFutures.get(taskId).getTaskFuture();
      if (taskFuture != null) {
        log.info("Task future: {} - done:{}, cancelled:{}", taskId, taskFuture.isDone(), taskFuture.isCancelled());
      }
      stillRunning = taskFuture != null && !taskFuture.isDone() && !taskFuture.isCancelled();
    }
    if (stillRunning) {
      log.error("Task {} of taskType {} timed out after {} milliseconds", taskId, taskData.getTaskType(), timeout);
      getMetricRegistry().recordGaugeInc(TASK_TIMEOUT, new String[] {DELEGATE_NAME, taskData.getTaskType()});
      Optional.ofNullable(currentlyExecutingFutures.get(taskId).getTaskFuture())
          .ifPresent(future -> future.cancel(true));
    }
    if (taskFuture != null) {
      try {
        HTimeLimiter.callInterruptible21(delegateTaskTimeLimiter, Duration.ofSeconds(5), taskFuture::get);
      } catch (UncheckedTimeoutException e) {
        ignoredOnPurpose(e);
        log.error("Timed out getting task future");
      } catch (CancellationException e) {
        ignoredOnPurpose(e);
        log.error("Task {} was cancelled", taskId);
      } catch (Exception e) {
        log.error("Error from task future {}", taskId, e);
      }
    }
    currentlyExecutingTasks.remove(taskId);
    if (currentlyExecutingFutures.remove(taskId) != null) {
      log.info("Removed {} from executing futures on timeout", taskId);
    }
  }

  //  private ILogStreamingTaskClient getLogStreamingTaskClient(
  //      Pair<String, Set<String>> activitySecrets, DelegateTaskPackage delegateTaskPackage) {
  //    boolean logStreamingConfigPresent = false;
  //    boolean logCallbackConfigPresent = false;
  //    String appId = null;
  //    String activityId = null;
  //
  //    if (logStreamingClient != null && !isBlank(delegateTaskPackage.getLogStreamingToken())
  //        && !isEmpty(delegateTaskPackage.getLogStreamingAbstractions())) {
  //      logStreamingConfigPresent = true;
  //    }
  //
  //    // Extract appId and activityId from task params, in case LogCallback logging has to be used for backward
  //    // compatibility reasons
  //    Object[] taskParameters = delegateTaskPackage.getData().getParameters();
  //    if (taskParameters != null && taskParameters.length == 1 && taskParameters[0] instanceof Cd1ApplicationAccess
  //        && taskParameters[0] instanceof ActivityAccess) {
  //      Cd1ApplicationAccess applicationAccess = (Cd1ApplicationAccess) taskParameters[0];
  //      appId = applicationAccess.getAppId();
  //
  //      ActivityAccess activityAccess = (ActivityAccess) taskParameters[0];
  //      activityId = activityAccess.getActivityId();
  //    }
  //
  //    if (!isBlank(appId) && !isBlank(activityId)) {
  //      logCallbackConfigPresent = true;
  //    }
  //
  //    if (!logStreamingConfigPresent && !logCallbackConfigPresent) {
  //      return null;
  //    }
  //    String logBaseKey = delegateTaskPackage.getLogStreamingAbstractions() != null
  //        ? LogStreamingHelper.generateLogBaseKey(delegateTaskPackage.getLogStreamingAbstractions())
  //        : EMPTY;
  //
  //    LogStreamingTaskClient.LogStreamingTaskClientBuilder taskClientBuilder =
  //        LogStreamingTaskClient.builder()
  //            .logStreamingClient(logStreamingClient)
  //            .accountId(delegateTaskPackage.getAccountId())
  //            .token(delegateTaskPackage.getLogStreamingToken())
  //            .logStreamingSanitizer(
  //                LogStreamingSanitizer.builder()
  //                    .secrets(activitySecrets.getRight().stream().map(String::trim).collect(Collectors.toSet()))
  //                    .build())
  //            .baseLogKey(logBaseKey)
  //            .logService(delegateLogService)
  //            .taskProgressExecutor(taskProgressExecutor)
  //            .appId(appId)
  //            .activityId(activityId);
  //
  //    if (isNotBlank(delegateTaskPackage.getDelegateCallbackToken()) && delegateServiceAgentClient != null) {
  //      taskClientBuilder.taskProgressClient(TaskProgressClient.builder()
  //                                               .accountId(delegateTaskPackage.getAccountId())
  //                                               .taskId(delegateTaskPackage.getDelegateTaskId())
  //                                               .delegateCallbackToken(delegateTaskPackage.getDelegateCallbackToken())
  //                                               .delegateServiceAgentClient(delegateServiceAgentClient)
  //                                               .kryoSerializer(kryoSerializer)
  //                                               .build());
  //    }
  //
  //    return taskClientBuilder.build();
  //  }

  @VisibleForTesting
  void applyDelegateSecretFunctor(DelegateTaskPackage delegateTaskPackage) {
    try {
      Map<String, EncryptionConfig> encryptionConfigs = delegateTaskPackage.getEncryptionConfigs();
      Map<String, SecretDetail> secretDetails = delegateTaskPackage.getSecretDetails();
      if (isEmpty(encryptionConfigs) || isEmpty(secretDetails)) {
        return;
      }

      Map<EncryptionConfig, List<EncryptedRecord>> encryptionConfigListMap = new HashMap<>();
      secretDetails.forEach(
          (key, secretDetail)
              -> addToEncryptedConfigListMap(encryptionConfigListMap,
                  encryptionConfigs.get(secretDetail.getConfigUuid()), secretDetail.getEncryptedRecord()));

      Map<String, char[]> decryptedRecords = delegateDecryptionService.decrypt(encryptionConfigListMap);
      Map<String, char[]> secretUuidToValues = new HashMap<>();

      secretDetails.forEach((key, value) -> {
        char[] secretValue = decryptedRecords.get(value.getEncryptedRecord().getUuid());
        secretUuidToValues.put(key, secretValue);

        // Adds secret values from the 3 phase decryption to the list of task secrets to be masked
        delegateTaskPackage.getSecrets().add(String.valueOf(secretValue));
      });

      DelegateExpressionEvaluator delegateExpressionEvaluator = new DelegateExpressionEvaluator(
          secretUuidToValues, delegateTaskPackage.getData().getExpressionFunctorToken());
      applyDelegateExpressionEvaluator(delegateTaskPackage, delegateExpressionEvaluator);
    } catch (Exception e) {
      sendErrorResponse(delegateTaskPackage, e);
      throw e;
    }
  }

  private void addToEncryptedConfigListMap(Map<EncryptionConfig, List<EncryptedRecord>> encryptionConfigListMap,
      EncryptionConfig encryptionConfig, EncryptedRecord encryptedRecord) {
    if (encryptionConfigListMap.containsKey(encryptionConfig)) {
      encryptionConfigListMap.get(encryptionConfig).add(encryptedRecord);
    } else {
      List<EncryptedRecord> encryptedRecordList = new ArrayList<>();
      encryptedRecordList.add(encryptedRecord);
      encryptionConfigListMap.put(encryptionConfig, encryptedRecordList);
    }
  }

  private void applyDelegateExpressionEvaluator(
      DelegateTaskPackage delegateTaskPackage, DelegateExpressionEvaluator delegateExpressionEvaluator) {
    try {
      TaskData taskData = delegateTaskPackage.getData();
      if (taskData.getParameters() != null && taskData.getParameters().length == 1
          && taskData.getParameters()[0] instanceof TaskParameters) {
        log.debug("Applying DelegateExpression Evaluator for delegateTask");
        ExpressionReflectionUtils.applyExpression(taskData.getParameters()[0],
            (secretMode, value) -> delegateExpressionEvaluator.substitute(value, new HashMap<>()));
      }
    } catch (Exception e) {
      log.error("Exception occurred during applying DelegateExpression Evaluator for delegateTask.", e);
      throw e;
    }
  }

  private void sendErrorResponse(DelegateTaskPackage delegateTaskPackage, Exception exception) {
    String taskId = delegateTaskPackage.getDelegateTaskId();
    DelegateTaskResponse taskResponse =
        DelegateTaskResponse.builder()
            .accountId(delegateTaskPackage.getAccountId())
            .responseCode(DelegateTaskResponse.ResponseCode.FAILED)
            .response(ErrorNotifyResponseData.builder().errorMessage(ExceptionUtils.getMessage(exception)).build())
            .build();
    log.info("Sending error response for task{}", taskId);
    try {
      Response<ResponseBody> resp;
      int retries = 5;
      for (int attempt = 0; attempt < retries; attempt++) {
        resp = getDelegateAgentManagerClient()
                   .sendTaskStatus(DelegateAgentCommonVariables.getDelegateId(), taskId,
                       getDelegateConfiguration().getAccountId(), taskResponse)
                   .execute();
        if (resp.code() >= 200 && resp.code() <= 299) {
          log.info("Task {} response sent to manager", taskId);
          return;
        }
        log.warn("Failed to send response for task {}: {}. error: {}. requested url: {} {}", taskId, resp.code(),
            resp.errorBody() == null ? "null" : resp.errorBody().string(), resp.raw().request().url(), "Retrying.");
        sleep(ofSeconds(FibonacciBackOff.getFibonacciElement(attempt)));
      }
    } catch (Exception e) {
      log.error("Unable to send response to manager", e);
    }
  }

  private Optional<LogSanitizer> getLogSanitizer(Pair<String, Set<String>> activitySecrets) {
    // Create log sanitizer only if activityId and secrets are present
    if (isNotBlank(activitySecrets.getLeft()) && isNotEmpty(activitySecrets.getRight())) {
      return Optional.of(new ActivityBasedLogSanitizer(activitySecrets.getLeft(), activitySecrets.getRight()));
    } else {
      return Optional.empty();
    }
  }

  private void logProxyConfiguration() {
    String proxyHost = System.getProperty("https.proxyHost");

    if (isBlank(proxyHost)) {
      log.info("No proxy settings. Configure in proxy.config if needed");
      return;
    }

    String proxyScheme = System.getProperty("proxyScheme");
    String proxyPort = System.getProperty("https.proxyPort");
    log.info("Using {} proxy {}:{}", proxyScheme, proxyHost, proxyPort);
    String nonProxyHostsString = System.getProperty("http.nonProxyHosts");

    if (nonProxyHostsString == null || isBlank(nonProxyHostsString)) {
      return;
    }

    String[] suffixes = nonProxyHostsString.split("\\|");
    List<String> nonProxyHosts = Stream.of(suffixes).map(suffix -> suffix.substring(1)).collect(toList());
    log.info("No proxy for hosts with suffix in: {}", nonProxyHosts);
  }

  private boolean shouldContactManager() {
    return !getSelfDestruct().get();
  }

  private void logCurrentTasks() {
    try (AutoLogContext ignore = new AutoLogContext(obtainPerformance(), OVERRIDE_NESTS)) {
      log.info("Current performance");
    }
  }

  private Map<String, String> obtainPerformance() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.put("maxValidatingTasksCount", Integer.toString(maxValidatingTasksCount.getAndSet(0)));
    builder.put("maxExecutingTasksCount", Integer.toString(maxExecutingTasksCount.getAndSet(0)));
    builder.put("maxExecutingFuturesCount", Integer.toString(maxExecutingFuturesCount.getAndSet(0)));

    OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    builder.put("cpu-process", Double.toString(Precision.round(osBean.getProcessCpuLoad() * 100, 2)));
    builder.put("cpu-system", Double.toString(Precision.round(osBean.getSystemCpuLoad() * 100, 2)));

    for (Map.Entry<String, ThreadPoolExecutor> executorEntry : getLogExecutors().entrySet()) {
      builder.put(executorEntry.getKey(), Integer.toString(executorEntry.getValue().getActiveCount()));
    }
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    memoryUsage(builder, "heap-", memoryMXBean.getHeapMemoryUsage());

    memoryUsage(builder, "non-heap-", memoryMXBean.getNonHeapMemoryUsage());

    return builder.build();
  }

  private void updateCounterIfLessThanCurrent(AtomicInteger counter, int current) {
    counter.updateAndGet(value -> Math.max(value, current));
  }
}
