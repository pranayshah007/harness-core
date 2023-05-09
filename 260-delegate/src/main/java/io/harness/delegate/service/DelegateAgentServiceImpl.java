/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateTimeBasedUuid;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.app.DelegateApplication.getProcessId;
import static io.harness.delegate.beans.DelegateType.HELM_DELEGATE;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.delegate.clienttools.InstallUtils.areClientToolsInstalled;
import static io.harness.delegate.clienttools.InstallUtils.setupClientTools;
import static io.harness.delegate.message.ManagerMessageConstants.MIGRATE;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.delegate.message.ManagerMessageConstants.UPDATE_PERPETUAL_TASK;
import static io.harness.delegate.message.MessageConstants.DELEGATE_DASH;
import static io.harness.delegate.message.MessageConstants.DELEGATE_GO_AHEAD;
import static io.harness.delegate.message.MessageConstants.DELEGATE_HEARTBEAT;
import static io.harness.delegate.message.MessageConstants.DELEGATE_ID;
import static io.harness.delegate.message.MessageConstants.DELEGATE_IS_NEW;
import static io.harness.delegate.message.MessageConstants.DELEGATE_JRE_VERSION;
import static io.harness.delegate.message.MessageConstants.DELEGATE_MIGRATE;
import static io.harness.delegate.message.MessageConstants.DELEGATE_READY;
import static io.harness.delegate.message.MessageConstants.DELEGATE_RESTART_NEEDED;
import static io.harness.delegate.message.MessageConstants.DELEGATE_RESUME;
import static io.harness.delegate.message.MessageConstants.DELEGATE_SELF_DESTRUCT;
import static io.harness.delegate.message.MessageConstants.DELEGATE_SHUTDOWN_PENDING;
import static io.harness.delegate.message.MessageConstants.DELEGATE_SHUTDOWN_STARTED;
import static io.harness.delegate.message.MessageConstants.DELEGATE_STARTED;
import static io.harness.delegate.message.MessageConstants.DELEGATE_START_GRPC;
import static io.harness.delegate.message.MessageConstants.DELEGATE_STOP_ACQUIRING;
import static io.harness.delegate.message.MessageConstants.DELEGATE_STOP_GRPC;
import static io.harness.delegate.message.MessageConstants.DELEGATE_SWITCH_STORAGE;
import static io.harness.delegate.message.MessageConstants.DELEGATE_TOKEN_NAME;
import static io.harness.delegate.message.MessageConstants.DELEGATE_VERSION;
import static io.harness.delegate.message.MessageConstants.MIGRATE_TO_JRE_VERSION;
import static io.harness.delegate.message.MessageConstants.UNREGISTERED;
import static io.harness.delegate.message.MessageConstants.WATCHER_DATA;
import static io.harness.delegate.message.MessageConstants.WATCHER_HEARTBEAT;
import static io.harness.delegate.message.MessageConstants.WATCHER_PROCESS;
import static io.harness.delegate.message.MessageConstants.WATCHER_VERSION;
import static io.harness.delegate.message.MessengerType.DELEGATE;
import static io.harness.delegate.message.MessengerType.WATCHER;
import static io.harness.delegate.metrics.DelegateMetric.DELEGATE_CONNECTED;
import static io.harness.delegate.metrics.DelegateMetric.RESOURCE_CONSUMPTION_ABOVE_THRESHOLD;
import static io.harness.delegate.metrics.DelegateMetric.TASKS_CURRENTLY_EXECUTING;
import static io.harness.delegate.metrics.DelegateMetric.TASK_COMPLETED;
import static io.harness.delegate.metrics.DelegateMetric.TASK_EXECUTION_TIME;
import static io.harness.delegate.metrics.DelegateMetric.TASK_FAILED;
import static io.harness.delegate.metrics.DelegateMetric.TASK_REJECTED;
import static io.harness.delegate.metrics.DelegateMetric.TASK_TIMEOUT;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.REVOKED_TOKEN;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.filesystem.FileIo.acquireLock;
import static io.harness.filesystem.FileIo.isLocked;
import static io.harness.filesystem.FileIo.releaseLock;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.logging.Misc.getDurationString;
import static io.harness.network.Localhost.getLocalHostAddress;
import static io.harness.network.Localhost.getLocalHostName;
import static io.harness.network.SafeHttpCall.execute;
import static io.harness.threading.Morpheus.sleep;
import static io.harness.utils.MemoryPerformanceUtils.memoryUsage;
import static io.harness.utils.SecretUtils.isBase64SecretIdentifier;

import static software.wings.beans.TaskType.SCRIPT;
import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static lombok.AccessLevel.PACKAGE;
import static org.apache.commons.io.filefilter.FileFilterUtils.falseFileFilter;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.concurrent.HTimeLimiter;
import io.harness.configuration.DeployMode;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.DelegateServiceAgentClient;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateParams.DelegateParamsBuilder;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.core.beans.ExecutionStatusResponse;
import io.harness.delegate.expression.DelegateExpressionEvaluator;
import io.harness.delegate.logging.DelegateStackdriverLogAppender;
import io.harness.delegate.message.Message;
import io.harness.delegate.message.MessageService;
import io.harness.delegate.service.common.DelegateTaskExecutionData;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.Cd1ApplicationAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.common.DelegateRunnableTask;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.delegate.task.validation.DelegateConnectionResultDetail;
import io.harness.event.client.impl.tailer.ChronicleEventTailer;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.UnexpectedException;
import io.harness.filesystem.FileIo;
import io.harness.grpc.util.RestartableServiceManager;
import io.harness.logging.AutoLogContext;
import io.harness.logstreaming.LogStreamingClient;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.logstreaming.LogStreamingSanitizer;
import io.harness.logstreaming.LogStreamingTaskClient;
import io.harness.logstreaming.LogStreamingTaskClient.LogStreamingTaskClientBuilder;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.network.FibonacciBackOff;
import io.harness.network.Http;
import io.harness.perpetualtask.PerpetualTaskWorker;
import io.harness.reflection.ExpressionReflectionUtils;
import io.harness.rest.RestResponse;
import io.harness.security.TokenGenerator;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.json.JsonUtils;
import io.harness.taskprogress.TaskProgressClient;
import io.harness.threading.Schedulable;
import io.harness.utils.ProcessControl;
import io.harness.version.VersionInfoManager;

import software.wings.beans.DelegateTaskFactory;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.delegation.CommandParameters;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.beans.dto.Command;
import software.wings.beans.shellscript.provisioner.ShellScriptProvisionParameters;
import software.wings.delegatetasks.ActivityBasedLogSanitizer;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.GenericLogSanitizer;
import software.wings.delegatetasks.LogSanitizer;
import software.wings.delegatetasks.delegatecapability.CapabilityCheckController;
import software.wings.delegatetasks.validation.core.DelegateValidateTask;
import software.wings.misc.MemoryHelper;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody.Part;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.internal.http2.StreamResetException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URIBuilder;
import org.asynchttpclient.AsyncHttpClient;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request.METHOD;
import org.atmosphere.wasync.Request.TRANSPORT;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Socket.STATUS;
import org.atmosphere.wasync.transport.TransportNotSupported;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;
import retrofit2.Call;
import retrofit2.Response;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateAgentServiceImpl implements DelegateAgentService {
  private static final int POLL_INTERVAL_SECONDS = 3;
  private static final long UPGRADE_TIMEOUT = TimeUnit.HOURS.toMillis(2);
  private static final long HEARTBEAT_TIMEOUT = TimeUnit.MINUTES.toMillis(15);
  private static final long HEARTBEAT_SOCKET_TIMEOUT = TimeUnit.MINUTES.toMillis(5);
  private static final long FROZEN_TIMEOUT = TimeUnit.HOURS.toMillis(2);
  private static final long WATCHER_HEARTBEAT_TIMEOUT = TimeUnit.MINUTES.toMillis(3);
  private static final long WATCHER_VERSION_MATCH_TIMEOUT = TimeUnit.MINUTES.toMillis(2);
  private static final long DELEGATE_JRE_VERSION_TIMEOUT = TimeUnit.MINUTES.toMillis(10);
  private static final String DELEGATE_SEQUENCE_CONFIG_FILE = "./delegate_sequence_config";
  private static final int KEEP_ALIVE_INTERVAL = 23000;
  private static final int CLIENT_TOOL_RETRIES = 5;
  private static final int LOCAL_HEARTBEAT_INTERVAL = 10;
  private static final String TOKEN = "[TOKEN]";
  private static final String SEQ = "[SEQ]";
  private static final String WATCHER_EXPRESSION = "Dwatchersourcedir";

  // Marker string to indicate task events.
  private static final String TASK_EVENT_MARKER = "{\"eventType\":\"DelegateTaskEvent\"";
  private static final String ABORT_EVENT_MARKER = "{\"eventType\":\"DelegateTaskAbortEvent\"";
  private static final String HEARTBEAT_RESPONSE = "{\"eventType\":\"DelegateHeartbeatResponseStreaming\"";

  private static final String HOST_NAME = getLocalHostName();
  private static String DELEGATE_NAME =
      isNotBlank(System.getenv().get("DELEGATE_NAME")) ? System.getenv().get("DELEGATE_NAME") : "";

  private static String DELEGATE_TYPE = System.getenv().get("DELEGATE_TYPE");
  private static final boolean IsEcsDelegate = "ECS".equals(DELEGATE_TYPE);
  private static String DELEGATE_GROUP_NAME = System.getenv().get("DELEGATE_GROUP_NAME");
  private final String delegateGroupId = System.getenv().get("DELEGATE_GROUP_ID");

  private static final String START_SH = "start.sh";
  private static final String DUPLICATE_DELEGATE_ERROR_MESSAGE =
      "Duplicate delegate with same delegateId:%s and connectionId:%s exists";

  private final String delegateTags = System.getenv().get("DELEGATE_TAGS");
  private final String delegateOrgIdentifier = System.getenv().get("DELEGATE_ORG_IDENTIFIER");
  private final String delegateProjectIdentifier = System.getenv().get("DELEGATE_PROJECT_IDENTIFIER");
  private final String delegateDescription = System.getenv().get("DELEGATE_DESCRIPTION");
  private boolean delegateNg = isNotBlank(System.getenv().get("DELEGATE_SESSION_IDENTIFIER"))
      || (isNotBlank(System.getenv().get("NEXT_GEN")) && Boolean.parseBoolean(System.getenv().get("NEXT_GEN")));
  public static final String JAVA_VERSION = "java.version";
  public static final int DEFAULT_MAX_THRESHOLD = 80;
  private final double RESOURCE_USAGE_THRESHOLD = isNotBlank(System.getenv().get("DELEGATE_RESOURCE_THRESHOLD"))
      ? (Integer.parseInt(System.getenv().get("DELEGATE_RESOURCE_THRESHOLD")))
      : DEFAULT_MAX_THRESHOLD;
  private final boolean dynamicRequestHandling = isNotBlank(System.getenv().get("DYNAMIC_REQUEST_HANDLING"))
      && Boolean.parseBoolean(System.getenv().get("DYNAMIC_REQUEST_HANDLING"));
  private String MANAGER_PROXY_CURL = System.getenv().get("MANAGER_PROXY_CURL");
  private String MANAGER_HOST_AND_PORT = System.getenv().get("MANAGER_HOST_AND_PORT");
  private static final String DEFAULT_PATCH_VERSION = "000";

  private final boolean BLOCK_SHELL_TASK = Boolean.parseBoolean(System.getenv().get("BLOCK_SHELL_TASK"));

  private static volatile String delegateId;
  private static final String delegateInstanceId = generateUuid();
  private final int MAX_ATTEMPTS = 3;

  @Inject
  @Getter(value = PACKAGE, onMethod = @__({ @VisibleForTesting }))
  private DelegateConfiguration delegateConfiguration;
  @Inject private RestartableServiceManager restartableServiceManager;

  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  @Inject @Named("healthMonitorExecutor") private ScheduledExecutorService healthMonitorExecutor;
  @Inject @Named("watcherMonitorExecutor") private ScheduledExecutorService watcherMonitorExecutor;
  @Inject @Named("inputExecutor") private ScheduledExecutorService inputExecutor;
  @Inject @Named("backgroundExecutor") private ExecutorService backgroundExecutor;
  @Inject @Named("taskPollExecutor") private ScheduledExecutorService taskPollExecutor;
  @Inject @Named("taskExecutor") private ThreadPoolExecutor taskExecutor;
  @Inject @Named("timeoutExecutor") private ThreadPoolExecutor timeoutEnforcement;
  @Inject @Named("grpcServiceExecutor") private ExecutorService grpcServiceExecutor;
  @Inject @Named("taskProgressExecutor") private ExecutorService taskProgressExecutor;

  @Inject private SignalService signalService;
  @Inject private MessageService messageService;
  @Inject private Injector injector;
  @Inject private TokenGenerator tokenGenerator;
  @Inject private AsyncHttpClient asyncHttpClient;
  @Inject private Clock clock;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private DelegateDecryptionService delegateDecryptionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private EncryptionService encryptionService;
  @Inject private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Inject(optional = true) @Nullable private PerpetualTaskWorker perpetualTaskWorker;
  @Inject(optional = true) @Nullable private LogStreamingClient logStreamingClient;
  @Inject DelegateTaskFactory delegateTaskFactory;
  @Inject(optional = true) @Nullable private DelegateServiceAgentClient delegateServiceAgentClient;
  @Inject private KryoSerializer kryoSerializer;
  @Nullable @Inject(optional = true) private ChronicleEventTailer chronicleEventTailer;
  @Inject HarnessMetricRegistry metricRegistry;

  private final AtomicBoolean waiter = new AtomicBoolean(true);

  private final Set<String> currentlyAcquiringTasks = ConcurrentHashMap.newKeySet();
  private final Map<String, DelegateTaskPackage> currentlyValidatingTasks = new ConcurrentHashMap<>();
  private final Map<String, DelegateTaskPackage> currentlyExecutingTasks = new ConcurrentHashMap<>();
  private final Map<String, DelegateTaskExecutionData> currentlyExecutingFutures = new ConcurrentHashMap<>();

  private final AtomicInteger maxValidatingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingFuturesCount = new AtomicInteger();
  private final AtomicInteger heartbeatSuccessCalls = new AtomicInteger();

  private final AtomicLong lastHeartbeatSentAt = new AtomicLong(System.currentTimeMillis());
  private final AtomicLong frozenAt = new AtomicLong(-1);
  private final AtomicLong lastHeartbeatReceivedAt = new AtomicLong(System.currentTimeMillis());
  private final AtomicBoolean restartNeeded = new AtomicBoolean(false);
  private final AtomicBoolean acquireTasks = new AtomicBoolean(true);
  private final AtomicBoolean frozen = new AtomicBoolean(false);
  private final AtomicBoolean executingProfile = new AtomicBoolean(false);
  private final AtomicBoolean selfDestruct = new AtomicBoolean(false);
  private final AtomicBoolean multiVersionWatcherStarted = new AtomicBoolean(false);
  private final AtomicBoolean switchStorage = new AtomicBoolean(false);
  private final AtomicBoolean closingSocket = new AtomicBoolean(false);
  private final AtomicBoolean sentFirstHeartbeat = new AtomicBoolean(false);
  private final Set<String> supportedTaskTypes = new HashSet<>();

  private Client client;
  private Socket socket;
  private String migrateTo;
  private long startTime;
  private long upgradeStartedAt;
  private long stoppedAcquiringAt;
  private String accountId;
  private long watcherVersionMatchedAt = System.currentTimeMillis();
  private long delegateJreVersionChangedAt;
  private TimeLimiter delegateHealthTimeLimiter;
  private TimeLimiter delegateTaskTimeLimiter;

  private final String delegateConnectionId = generateTimeBasedUuid();
  private volatile boolean switchStorageMsgSent;
  private DelegateConnectionHeartbeat connectionHeartbeat;
  private String migrateToJreVersion = System.getProperty(JAVA_VERSION);
  private boolean sendJreInformationToWatcher;

  private final boolean multiVersion = DeployMode.KUBERNETES.name().equals(System.getenv().get(DeployMode.DEPLOY_MODE))
      || TRUE.toString().equals(System.getenv().get("MULTI_VERSION"));
  private boolean isImmutableDelegate;

  private double maxProcessRSSThresholdMB;
  private double maxPodRSSThresholdMB;
  private final AtomicBoolean rejectRequest = new AtomicBoolean(false);

  public static Optional<String> getDelegateId() {
    return Optional.ofNullable(delegateId);
  }

  public boolean isHeartbeatHealthy() {
    return sentFirstHeartbeat.get() && ((clock.millis() - lastHeartbeatSentAt.get()) <= HEARTBEAT_TIMEOUT);
  }

  public boolean isSocketHealthy() {
    return socket.status() == STATUS.OPEN || socket.status() == STATUS.REOPENED;
  }

  @Override
  public void shutdown(final boolean shouldUnregister) throws InterruptedException {
    shutdownExecutors();
    if (shouldUnregister) {
      unregisterDelegate();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void run(final boolean watched, final boolean isImmutableDelegate) {
    this.isImmutableDelegate = isImmutableDelegate;
    delegateConfiguration.setImmutable(isImmutableDelegate);

    // check if someone used the older stateful set yaml with immutable image
    checkForMismatchBetweenImageAndK8sResourceType();

    try {
      // Initialize delegate process in background.
      backgroundExecutor.submit(() -> { initDelegateProcess(watched); });

      if (!this.isImmutableDelegate) {
        // Wait till we receive notify event.
        log.info("Waiting indefinitely for stop event");
        synchronized (waiter) {
          while (waiter.get()) {
            waiter.wait();
          }
        }
        log.info("Got stop message from watcher, shutting down now");
        clearData();
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Exception while starting/running delegate", e);
    } catch (Exception e) {
      log.error("Exception while starting/running delegate", e);
    }
  }

  /**
   * for immutable delegate check if the hostname ends with a number.
   * This will indicate that the customer started an immutable delegate with older stateful set yaml
   */
  @SuppressWarnings("PMD")
  private void checkForMismatchBetweenImageAndK8sResourceType() {
    if (!KUBERNETES.equals(DELEGATE_TYPE) && !HELM_DELEGATE.equals(DELEGATE_TYPE)) {
      return;
    }

    int index = HOST_NAME.lastIndexOf("-");
    if (index < 0) {
      return;
    }

    try {
      int delegateIndex = Integer.parseInt(HOST_NAME.substring(index + 1));
      // a delegate can have a name like test-8bbd86b7b-23455 in which case we don't want to fail
      if (delegateIndex < 1000 && isImmutableDelegate) {
        log.error("It appears that you have used a legacy delegate yaml with the newer delegate image."
            + " Please note that for the delegate images formatted as YY.MM.XXXXX you should download a fresh yaml and not reuse legacy delegate yaml");
        System.exit(1);
      }
    } catch (NumberFormatException e) {
      // if there is NumberFormatException then its a deployment
      log.info("{} is not from a stateful set, checking whether its using immutable image", HOST_NAME);
      if (!isImmutableDelegate) {
        log.error(
            "It appears that you have used a legacy delegate image with newer delegate yaml. Please use images formatted as YY.MM.XXXXX");
        System.exit(1);
      }
    } catch (StringIndexOutOfBoundsException e) {
      log.info("{} is an unexpected name, continuing", HOST_NAME);
    }
  }

  private void initDelegateProcess(final boolean watched) {
    try {
      if (delegateConfiguration.isLocalNgDelegate()) {
        delegateNg = true;
        DELEGATE_GROUP_NAME = "localDelegate";
        // Setting delegate type as kubernetes, as NG doesn't allow shell delegates.
        DELEGATE_TYPE = KUBERNETES;
        DELEGATE_NAME = "LocalDelegate";
      }
      accountId = delegateConfiguration.getAccountId();
      if (perpetualTaskWorker != null) {
        log.info("Starting perpetual task workers");
        perpetualTaskWorker.setAccountId(accountId);
        perpetualTaskWorker.start();
      }
      log.info("Delegate will start running on JRE {}", System.getProperty(JAVA_VERSION));
      log.info("The deploy mode for delegate is [{}]", System.getenv().get("DEPLOY_MODE"));
      startTime = clock.millis();
      delegateHealthTimeLimiter = HTimeLimiter.create(healthMonitorExecutor);
      delegateTaskTimeLimiter = HTimeLimiter.create(taskExecutor);
      DelegateStackdriverLogAppender.setTimeLimiter(delegateHealthTimeLimiter);
      DelegateStackdriverLogAppender.setManagerClient(delegateAgentManagerClient);

      logProxyConfiguration();

      connectionHeartbeat = DelegateConnectionHeartbeat.builder()
                                .delegateConnectionId(delegateConnectionId)
                                .version(getVersion())
                                .location(Paths.get("").toAbsolutePath().toString())
                                .build();

      if (watched) {
        log.info("[New] Delegate process started. Sending confirmation");
        messageService.writeMessage(DELEGATE_STARTED);
        startInputCheck();
        log.info("[New] Waiting for go ahead from watcher");
        Message message = messageService.waitForMessage(DELEGATE_GO_AHEAD, TimeUnit.MINUTES.toMillis(5), false);
        log.info(message != null ? "[New] Got go-ahead. Proceeding"
                                 : "[New] Timed out waiting for go-ahead. Proceeding anyway");
        messageService.removeData(DELEGATE_DASH + getProcessId(), DELEGATE_IS_NEW);
        startLocalHeartbeat();
        watcherMonitorExecutor.scheduleWithFixedDelay(() -> {
          try {
            log.info("Checking for watcher upgrade");
            watcherUpgrade(false);
          } catch (Exception e) {
            log.error("Error while upgrading watcher", e);
          }
        }, 0, 60, TimeUnit.MINUTES);
      } else {
        log.info("Delegate process started");
        if (delegateConfiguration.isGrpcServiceEnabled()) {
          restartableServiceManager.start();
        }
      }

      if (delegateConfiguration.isInstallClientToolsInBackground()) {
        log.info("Client tools will be setup in the background, while delegate registers");
        backgroundExecutor.submit(() -> {
          int retries = CLIENT_TOOL_RETRIES;
          while (!areClientToolsInstalled() && retries > 0) {
            setupClientTools(delegateConfiguration);
            sleep(ofSeconds(15L));
            retries--;
          }
        });
      } else {
        log.info("Client tools will be setup synchronously, before delegate registers");
        setupClientTools(delegateConfiguration);
      }

      long start = clock.millis();
      String descriptionFromConfigFile = isBlank(delegateDescription) ? "" : delegateDescription;
      String description = "description here".equals(delegateConfiguration.getDescription())
          ? descriptionFromConfigFile
          : delegateConfiguration.getDescription();

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
      supportedTaskTypes.addAll(supportedTasks);
      final DelegateParamsBuilder builder =
          DelegateParams.builder()
              .ip(getLocalHostAddress())
              .accountId(accountId)
              .orgIdentifier(delegateOrgIdentifier)
              .projectIdentifier(delegateProjectIdentifier)
              .hostName(HOST_NAME)
              .delegateName(DELEGATE_NAME)
              .delegateGroupName(DELEGATE_GROUP_NAME)
              .delegateGroupId(delegateGroupId)
              .delegateProfileId(isNotBlank(delegateProfile) ? delegateProfile : null)
              .description(description)
              .version(getVersion())
              .delegateType(DELEGATE_TYPE)
              .supportedTaskTypes(supportedTasks)
              //.proxy(set to true if there is a system proxy)
              .pollingModeEnabled(delegateConfiguration.isPollForTasks())
              .ng(delegateNg)
              .tags(isNotBlank(delegateTags) ? new ArrayList<>(asList(delegateTags.trim().split("\\s*,+\\s*,*\\s*")))
                                             : emptyList())
              .location(Paths.get("").toAbsolutePath().toString())
              .heartbeatAsObject(true)
              .immutable(isImmutableDelegate)
              .ceEnabled(Boolean.parseBoolean(System.getenv("ENABLE_CE")));

      delegateId = registerDelegate(builder);
      DelegateAgentCommonVariables.setDelegateId(delegateId);
      log.info("[New] Delegate registered in {} ms", clock.millis() - start);
      DelegateStackdriverLogAppender.setDelegateId(delegateId);
      if (isImmutableDelegate && dynamicRequestHandling) {
        // Enable dynamic throttling of requests only for immutable and FF enabled
        startDynamicHandlingOfTasks();
      }

      if (isPollingForTasksEnabled()) {
        log.info("Polling is enabled for Delegate");
        startHeartbeat(builder);
        startTaskPolling();
      } else {
        client = org.atmosphere.wasync.ClientFactory.getDefault().newClient();

        RequestBuilder requestBuilder = prepareRequestBuilder();

        Options clientOptions = client.newOptionsBuilder()
                                    .runtime(asyncHttpClient, true)
                                    .reconnect(true)
                                    .reconnectAttempts(Integer.MAX_VALUE)
                                    .pauseBeforeReconnectInSeconds(5)
                                    .build();
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
              }
            });

        socket.open(requestBuilder.build());

        startHeartbeat(builder, socket);
        // TODO(Abhinav): Check if we can avoid separate call for ECS delegates.
        if (isEcsDelegate()) {
          startKeepAlivePacket(builder);
        }
      }

      startChroniqleQueueMonitor();

      startMonitoringWatcher();
      checkForSSLCertVerification(accountId);

      log.info("Delegate started with config {} ", getDelegateConfig());
      messageService.writeMessage(DELEGATE_READY);
      log.info("Manager Authority:{}, Manager Target:{}", delegateConfiguration.getManagerAuthority(),
          delegateConfiguration.getManagerTarget());

      if (!isImmutableDelegate) {
        startProfileCheck();
      }

      if (delegateLocalConfigService.isLocalConfigPresent()) {
        Map<String, String> localSecrets = delegateLocalConfigService.getLocalDelegateSecrets();
        if (isNotEmpty(localSecrets)) {
          delegateLogService.registerLogSanitizer(new GenericLogSanitizer(new HashSet<>(localSecrets.values())));
        }
      }
    } catch (RuntimeException | IOException e) {
      log.error("Exception while starting/running delegate", e);
    }
  }

  private String getDelegateConfig() {
    String delegateConfig = delegateConfiguration.toString();
    delegateConfig += ", Multiversion: " + multiVersion;
    return delegateConfig;
  }

  private void maybeUpdateTaskRejectionStatus() {
    final long currentRSSMB = MemoryHelper.getProcessMemoryMB();
    if (currentRSSMB >= maxProcessRSSThresholdMB) {
      log.warn(
          "Memory resource reached threshold, temporarily reject incoming task request. CurrentProcessRSSMB {} ThresholdMB {}",
          currentRSSMB, maxProcessRSSThresholdMB);
      rejectRequest.compareAndSet(false, true);
      metricRegistry.recordGaugeValue(
          RESOURCE_CONSUMPTION_ABOVE_THRESHOLD.getMetricName(), new String[] {DELEGATE_NAME}, 1.0);
      metricRegistry.recordCounterInc(TASK_REJECTED.getMetricName(), new String[] {DELEGATE_NAME});
      return;
    }

    final long currentPodRSSMB = MemoryHelper.getPodRSSFromCgroupMB();
    if (currentPodRSSMB >= maxPodRSSThresholdMB) {
      log.warn(
          "Memory resource reached threshold, temporarily reject incoming task request. CurrentPodRSSMB {} ThresholdMB {}",
          currentPodRSSMB, maxPodRSSThresholdMB);
      rejectRequest.compareAndSet(false, true);
      metricRegistry.recordGaugeValue(
          RESOURCE_CONSUMPTION_ABOVE_THRESHOLD.getMetricName(), new String[] {DELEGATE_NAME}, 1.0);
      metricRegistry.recordCounterInc(TASK_REJECTED.getMetricName(), new String[] {DELEGATE_NAME});
      return;
    }
    log.debug("Process info CurrentProcessRSSMB {} ThresholdProcessMB {} currentPodRSSMB {} ThresholdPodMemoryMB {}",
        currentRSSMB, maxProcessRSSThresholdMB, currentPodRSSMB, maxPodRSSThresholdMB);
    final double cpuLoad = getCPULoadAverage();
    if (cpuLoad > RESOURCE_USAGE_THRESHOLD) {
      log.warn(
          "CPU resource reached threshold, temporarily reject incoming task request, CPU consumption above threshold, {}%",
          BigDecimal.valueOf(cpuLoad));
      rejectRequest.compareAndSet(false, true);
      metricRegistry.recordGaugeValue(
          RESOURCE_CONSUMPTION_ABOVE_THRESHOLD.getMetricName(), new String[] {DELEGATE_NAME}, 1.0);
      metricRegistry.recordCounterInc(TASK_REJECTED.getMetricName(), new String[] {DELEGATE_NAME});
      return;
    }

    if (rejectRequest.compareAndSet(true, false)) {
      log.info(
          "Accepting incoming task request. CurrentProcessRSSMB {} ThresholdProcessMB {} currentPodRSSMB {} ThresholdPodMemoryMB {}",
          currentRSSMB, maxProcessRSSThresholdMB, currentPodRSSMB, maxPodRSSThresholdMB);
      metricRegistry.recordGaugeValue(
          RESOURCE_CONSUMPTION_ABOVE_THRESHOLD.getMetricName(), new String[] {DELEGATE_NAME}, 0.0);
    }
  }

  private void clearData() {
    log.info("Clearing data for delegate process {}", getProcessId());
    messageService.closeData(DELEGATE_DASH + getProcessId());
    messageService.closeChannel(DELEGATE, getProcessId());

    removeDelegateVersionFromCapsule();
    cleanupOldDelegateVersionFromBackup();
  }

  private RequestBuilder prepareRequestBuilder() {
    try {
      URIBuilder uriBuilder =
          new URIBuilder(delegateConfiguration.getManagerUrl().replace("/api/", "/stream/") + "delegate/" + accountId)
              .addParameter("delegateId", delegateId)
              .addParameter("delegateTokenName", DelegateAgentCommonVariables.getDelegateTokenName())
              .addParameter("delegateConnectionId", delegateConnectionId)
              .addParameter("token", tokenGenerator.getToken("https", "localhost", 9090, HOST_NAME))
              .addParameter("sequenceNum", getSequenceNumForEcsDelegate())
              .addParameter("delegateToken", getRandomTokenForEcsDelegate())
              .addParameter("version", getVersion());

      URI uri = uriBuilder.build();

      // Stream the request body
      final RequestBuilder requestBuilder = client.newRequestBuilder().method(METHOD.GET).uri(uri.toString());

      requestBuilder
          .encoder(new Encoder<DelegateParams, Reader>() { // Do not change this, wasync doesn't like lambdas
            @Override
            public Reader encode(DelegateParams s) {
              return new StringReader(JsonUtils.asJson(s));
            }
          })
          .transport(TRANSPORT.WEBSOCKET);

      // send accountId + delegateId as header for delegate gateway to log websocket connection with account.
      requestBuilder.header("accountId", this.delegateConfiguration.getAccountId());
      final String agent = "delegate/" + this.versionInfoManager.getVersionInfo().getVersion();
      requestBuilder.header("User-Agent", agent);
      requestBuilder.header("delegateId", DelegateAgentCommonVariables.getDelegateId());

      return requestBuilder;
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Unable to prepare uri", e);
    }
  }

  /**
   * This is just a workAround. We need to make sure, when delegate.sh is baked into container, its initialized with
   * proper value. We need to update watcher / manager communication for that. Its a bigger change, and will be handled
   * in a separate JIRA
   */
  private boolean isPollingForTasksEnabled() {
    if (isEcsDelegate()) {
      return "true".equals(System.getenv("POLL_FOR_TASKS"));
    }

    return delegateConfiguration.isPollForTasks();
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

  private void handleOpen(Object o) {
    log.info("Event:{}, message:[{}]", Event.OPEN.name(), o.toString());
    metricRegistry.recordGaugeValue(DELEGATE_CONNECTED.getMetricName(), new String[] {DELEGATE_NAME}, 1.0);
  }

  private void handleClose(Object o) {
    log.info("Event:{}, trying to reconnect, message:[{}]", Event.CLOSE.name(), o);
    metricRegistry.recordGaugeValue(DELEGATE_CONNECTED.getMetricName(), new String[] {DELEGATE_NAME}, 0.0);
  }

  private void handleError(final Exception e) {
    log.info("Event:{}", Event.ERROR.name(), e);
    metricRegistry.recordGaugeValue(DELEGATE_CONNECTED.getMetricName(), new String[] {DELEGATE_NAME}, 0.0);
  }

  private void finalizeSocket() {
    closingSocket.set(true);
    socket.close();
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
    } else if (StringUtils.equals(message, SELF_DESTRUCT + delegateId)) {
      initiateSelfDestruct();
    } else if (StringUtils.startsWith(message, SELF_DESTRUCT)) {
      if (StringUtils.startsWith(message, SELF_DESTRUCT + delegateId + "-")) {
        int len = (SELF_DESTRUCT + delegateId + "-").length();
        if (message.substring(len).equals(delegateConnectionId)) {
          initiateSelfDestruct();
        }
      }
    } else if (StringUtils.contains(message, UPDATE_PERPETUAL_TASK)) {
      updateTasks();
    } else if (StringUtils.startsWith(message, MIGRATE)) {
      migrate(StringUtils.substringAfter(message, MIGRATE));
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

  private void closeAndReconnectSocket() {
    try {
      finalizeSocket();
      if (socket.status() == STATUS.OPEN || socket.status() == STATUS.REOPENED) {
        log.error("Unable to close socket");
        closingSocket.set(false);
        return;
      }
      RequestBuilder requestBuilder = prepareRequestBuilder();
      socket.open(requestBuilder.build());
      if (socket.status() == STATUS.OPEN || socket.status() == STATUS.REOPENED) {
        log.info("Socket reopened, status {}", socket.status());
        closingSocket.set(false);
      }
    } catch (RuntimeException | IOException e) {
      log.error("Exception while opening web socket connection delegate", e);
    }
  }

  private void stopGrpcService() {
    if (delegateConfiguration.isGrpcServiceEnabled() && restartableServiceManager.isRunning()) {
      grpcServiceExecutor.submit(() -> restartableServiceManager.stop());
    }
  }

  private void startGrpcService() {
    if (delegateConfiguration.isGrpcServiceEnabled() && acquireTasks.get() && !restartableServiceManager.isRunning()) {
      grpcServiceExecutor.submit(() -> { restartableServiceManager.start(); });
    }
  }

  private void updateTasks() {
    if (perpetualTaskWorker != null) {
      perpetualTaskWorker.updateTasks();
    }
  }

  private void updateJreVersion(String targetJreVersion) {
    if (!targetJreVersion.equals(migrateToJreVersion)) {
      log.info("JRE version different. Migrating to {}", targetJreVersion);
      delegateJreVersionChangedAt = clock.millis();
      migrateToJreVersion = targetJreVersion;
      sendJreInformationToWatcher = false;
    } else {
      sendJreInformationToWatcher = clock.millis() - delegateJreVersionChangedAt > DELEGATE_JRE_VERSION_TIMEOUT;
    }

    log.debug("Send info to watcher {}", sendJreInformationToWatcher);
  }

  @Override
  public void pause() {
    if (!delegateConfiguration.isPollForTasks()) {
      finalizeSocket();
    }
  }

  private void resume() {
    try {
      if (!delegateConfiguration.isPollForTasks()) {
        FibonacciBackOff.executeForEver(() -> {
          RequestBuilder requestBuilder = prepareRequestBuilder();
          return socket.open(requestBuilder.build());
        });
      }
      if (perpetualTaskWorker != null) {
        perpetualTaskWorker.start();
      }
      restartNeeded.set(false);
      acquireTasks.set(true);
    } catch (IOException e) {
      log.error("Failed to resume.", e);
      stop();
    }
  }

  @Override
  public void stop() {
    synchronized (waiter) {
      waiter.set(false);
      waiter.notifyAll();
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
      handleResponse(response);
    }
  }

  private <T> T executeCallWithRetryableException(Call<T> call, String failureMessage) throws IOException {
    T responseBody = null;
    Response<T> response = null;
    int attempt = 1;
    while (attempt <= MAX_ATTEMPTS && responseBody == null) {
      try {
        response = call.clone().execute();
        responseBody = response.body();
      } catch (Exception exception) {
        if (exception instanceof StreamResetException && attempt < MAX_ATTEMPTS) {
          attempt++;
          log.warn(String.format("%s : Attempt: %d", failureMessage, attempt));
        } else {
          throw exception;
        }
      }
    }
    return responseBody;
  }

  private <T> T executeAcquireCallWithRetry(Call<T> call, String failureMessage) throws IOException {
    Response<T> response = null;
    try {
      return executeCallWithRetryableException(call, failureMessage);
    } catch (Exception e) {
      log.error("error executing rest call", e);
      throw e;
    } finally {
      handleResponse(response);
    }
  }

  private <T> void handleResponse(Response<T> response) throws IOException {
    if (response != null && !response.isSuccessful()) {
      String errorResponse = response.errorBody().string();

      log.warn("Received Error Response: {}", errorResponse);

      if (errorResponse.contains(INVALID_TOKEN.name())) {
        log.warn("Delegate used invalid token. Self destruct procedure will be initiated.");
        initiateSelfDestruct();
      } else if (errorResponse.contains(format(DUPLICATE_DELEGATE_ERROR_MESSAGE, delegateId, delegateConnectionId))) {
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

  private String registerDelegate(final DelegateParamsBuilder builder) {
    updateBuilderIfEcsDelegate(builder);
    AtomicInteger attempts = new AtomicInteger(0);
    while (acquireTasks.get() && shouldContactManager()) {
      RestResponse<DelegateRegisterResponse> restResponse;
      try {
        attempts.incrementAndGet();
        String attemptString = attempts.get() > 1 ? " (Attempt " + attempts.get() + ")" : "";
        log.info("Registering delegate" + attemptString);
        DelegateParams delegateParams = builder.build()
                                            .toBuilder()
                                            .lastHeartBeat(clock.millis())
                                            .delegateType(DELEGATE_TYPE)
                                            .description(delegateConfiguration.getDescription())
                                            //.proxy(set to true if there is a system proxy)
                                            .pollingModeEnabled(delegateConfiguration.isPollForTasks())
                                            .ceEnabled(Boolean.parseBoolean(System.getenv("ENABLE_CE")))
                                            .heartbeatAsObject(true)
                                            .build();
        restResponse = executeRestCall(delegateAgentManagerClient.registerDelegate(accountId, delegateParams));
      } catch (Exception e) {
        String msg = "Unknown error occurred while registering Delegate [" + accountId + "] with manager";
        log.error(msg, e);
        sleep(ofMinutes(1));
        continue;
      }
      if (restResponse == null || restResponse.getResource() == null) {
        log.error(
            "Error occurred while registering delegate with manager for account '{}' - Please see the manager log for more information.",
            accountId);
        sleep(ofMinutes(1));
        continue;
      }

      DelegateRegisterResponse delegateResponse = restResponse.getResource();
      String responseDelegateId = delegateResponse.getDelegateId();
      handleEcsDelegateRegistrationResponse(delegateResponse);

      if (DelegateRegisterResponse.Action.SELF_DESTRUCT == delegateResponse.getAction()) {
        initiateSelfDestruct();
        sleep(ofMinutes(1));
        continue;
      }
      if (DelegateRegisterResponse.Action.MIGRATE == delegateResponse.getAction()) {
        migrate(delegateResponse.getMigrateUrl());
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

  private void unregisterDelegate() {
    final DelegateUnregisterRequest request = new DelegateUnregisterRequest(delegateId, HOST_NAME, delegateNg,
        DELEGATE_TYPE, getLocalHostAddress(), delegateOrgIdentifier, delegateProjectIdentifier);
    try {
      log.info("Unregistering delegate {}", delegateId);
      executeRestCall(delegateAgentManagerClient.unregisterDelegate(accountId, request));
    } catch (final IOException e) {
      log.error("Failed unregistering delegate {}", delegateId, e);
    }
  }

  private void startProfileCheck() {
    healthMonitorExecutor.scheduleWithFixedDelay(() -> {
      boolean forCodeFormattingOnly; // This line is here for clang-format
      synchronized (this) {
        checkForProfile();
      }
    }, 0, 3, TimeUnit.MINUTES);
  }

  void checkForProfile() {
    if (shouldContactManager() && !executingProfile.get() && !isLocked(new File("profile")) && !frozen.get()) {
      try {
        log.debug("Checking for profile ...");
        DelegateProfileParams profileParams = getProfile();
        boolean resultExists = new File("profile.result").exists();
        String profileId = profileParams == null ? "" : profileParams.getProfileId();
        long updated = profileParams == null || !resultExists ? 0L : profileParams.getProfileLastUpdatedAt();
        RestResponse<DelegateProfileParams> response =
            HTimeLimiter.callInterruptible21(delegateHealthTimeLimiter, Duration.ofSeconds(15),
                ()
                    -> executeRestCall(
                        delegateAgentManagerClient.checkForProfile(delegateId, accountId, profileId, updated)));
        if (response != null) {
          applyProfile(response.getResource());
        }
      } catch (UncheckedTimeoutException ex) {
        log.warn("Timed out checking for profile", ex);
      } catch (Exception e) {
        log.error("Error checking for profile", e);
      }
    } else {
      log.warn(
          "Unable to check/start delegate profile, shouldContactManager :{}, currently executing profile :{}, isLocked :{}, frozen :{}.",
          shouldContactManager(), executingProfile.get(), isLocked(new File("profile")), frozen.get());
      File profileFile = new File("profile");
      if (!executingProfile.get() && isLocked(profileFile)) {
        releaseLock(new File("profile"));
      }
    }
  }

  private DelegateProfileParams getProfile() {
    File profile = new File("profile");
    if (profile.exists()) {
      try {
        return JsonUtils.asObject(
            FileUtils.readFileToString(profile, java.nio.charset.StandardCharsets.UTF_8), DelegateProfileParams.class);
      } catch (Exception e) {
        log.error("Error reading profile", e);
      }
    }
    return null;
  }

  private void applyProfile(DelegateProfileParams profile) {
    if (profile != null && executingProfile.compareAndSet(false, true)) {
      File profileFile = new File("profile");
      if (acquireLock(profileFile, ofMinutes(5))) {
        try {
          if ("NONE".equals(profile.getProfileId())) {
            FileUtils.deleteQuietly(profileFile);
            FileUtils.deleteQuietly(new File("profile.result"));
            return;
          }

          log.info("Updating delegate profile to [{} : {}], last update {} ...", profile.getProfileId(),
              profile.getName(), profile.getProfileLastUpdatedAt());
          String script = profile.getScriptContent();
          List<String> result = new ArrayList<>();
          int exitCode = 0;

          if (!isBlank(script)) {
            Logger scriptLogger = LoggerFactory.getLogger("delegate-profile-" + profile.getProfileId());
            scriptLogger.info("Executing profile script: {}", profile.getName());

            ProcessExecutor processExecutor = new ProcessExecutor()
                                                  .timeout(10, TimeUnit.MINUTES)
                                                  .command("/bin/bash", "-c", script)
                                                  .readOutput(true)
                                                  .redirectOutput(new LogOutputStream() {
                                                    @Override
                                                    protected void processLine(String line) {
                                                      scriptLogger.info(line);
                                                      result.add(line);
                                                    }
                                                  })
                                                  .redirectError(new LogOutputStream() {
                                                    @Override
                                                    protected void processLine(String line) {
                                                      scriptLogger.error(line);
                                                      result.add("ERROR: " + line);
                                                    }
                                                  });
            exitCode = processExecutor.execute().getExitValue();
          }

          saveProfile(profile, result);
          uploadProfileResult(exitCode);
          log.info("Profile applied");
        } catch (IOException e) {
          log.error("Error applying profile [{}]", profile.getName(), e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
          log.info("Timed out", e);
        } catch (UncheckedTimeoutException ex) {
          log.error("Timed out sending profile result", ex);
        } catch (Exception e) {
          log.error("Error applying profile", e);
        } finally {
          executingProfile.set(false);
          if (!releaseLock(profileFile)) {
            log.error("Failed to release lock {}", profileFile.getPath());
          }
        }
      }
    }
  }

  private void saveProfile(DelegateProfileParams profile, List<String> result) {
    log.info("Saving profile result");
    try {
      File profileFile = new File("profile");
      if (profileFile.exists()) {
        FileUtils.forceDelete(profileFile);
      }
      FileUtils.touch(profileFile);
      FileUtils.write(profileFile, JsonUtils.asPrettyJson(profile), java.nio.charset.StandardCharsets.UTF_8);

      File resultFile = new File("profile.result");
      if (resultFile.exists()) {
        FileUtils.forceDelete(resultFile);
      }
      FileUtils.touch(resultFile);
      FileUtils.writeLines(resultFile, result);
    } catch (IOException e) {
      log.error("Error writing profile [{}]", profile.getName(), e);
    }
  }

  private void uploadProfileResult(int exitCode) throws Exception {
    log.info("Uploading profile result");
    // create RequestBody instance from file
    File profileResult = new File("profile.result");
    RequestBody requestFile = RequestBody.create(MediaType.parse(MULTIPART_FORM_DATA), profileResult);

    // MultipartBody.Part is used to send also the actual file name
    Part part = Part.createFormData("file", profileResult.getName(), requestFile);
    HTimeLimiter.callInterruptible21(delegateHealthTimeLimiter, Duration.ofSeconds(15),
        ()
            -> executeRestCall(delegateAgentManagerClient.saveProfileResult(
                delegateId, accountId, exitCode != 0, FileBucket.PROFILE_RESULTS, part)));
  }

  private void startInputCheck() {
    inputExecutor.scheduleWithFixedDelay(
        messageService.getMessageCheckingRunnable(TimeUnit.SECONDS.toMillis(2), message -> {
          if (DELEGATE_STOP_ACQUIRING.equals(message.getMessage())) {
            handleStopAcquiringMessage(message.getFromProcess());
          } else if (DELEGATE_RESUME.equals(message.getMessage())) {
            resume();
          } else if (DELEGATE_START_GRPC.equals(message.getMessage())) {
            startGrpcService();
          } else if (DELEGATE_STOP_GRPC.equals(message.getMessage())) {
            stopGrpcService();
          }
        }), 0, 1, TimeUnit.SECONDS);
  }

  public void freeze() {
    log.warn("Delegate with id: {} was put in freeze mode.", delegateId);
    frozenAt.set(System.currentTimeMillis());
    frozen.set(true);
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

    if (perpetualTaskWorker != null) {
      log.info("Stopping perpetual task workers");
      perpetualTaskWorker.stop();
      log.info("Stopped perpetual task workers");
    }

    if (restartableServiceManager != null) {
      restartableServiceManager.stop();
    }

    if (chronicleEventTailer != null) {
      log.info("Stopping chronicle event trailer");
      chronicleEventTailer.stopAsync().awaitTerminated();
      log.info("Stopped chronicle event trailer");
    }
  }

  private void handleStopAcquiringMessage(String sender) {
    log.info("Got stop-acquiring message from watcher {}", sender);
    if (acquireTasks.getAndSet(false)) {
      stoppedAcquiringAt = clock.millis();
      Map<String, Object> shutdownData = new HashMap<>();
      shutdownData.put(DELEGATE_SHUTDOWN_PENDING, true);
      shutdownData.put(DELEGATE_SHUTDOWN_STARTED, stoppedAcquiringAt);
      messageService.putAllData(DELEGATE_DASH + getProcessId(), shutdownData);

      backgroundExecutor.submit(() -> {
        long started = clock.millis();
        long now = started;
        while (!currentlyExecutingTasks.isEmpty() && now - started < UPGRADE_TIMEOUT) {
          sleep(ofSeconds(1));
          now = clock.millis();
          log.info("[Old] Completing {} tasks... ({} seconds elapsed): {}", currentlyExecutingTasks.size(),
              (now - started) / 1000L, currentlyExecutingTasks.keySet());
        }
        log.info(now - started < UPGRADE_TIMEOUT ? "[Old] Delegate finished with tasks. Pausing"
                                                 : "[Old] Timed out waiting to complete tasks. Pausing");
        signalService.pause();
        log.info("[Old] Shutting down");

        signalService.stop();
      });
      if (perpetualTaskWorker != null) {
        perpetualTaskWorker.stop();
      }
    }
  }

  private void startTaskPolling() {
    taskPollExecutor.scheduleAtFixedRate(
        new Schedulable("Failed to poll for task", () -> pollForTask()), 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  private void startChroniqleQueueMonitor() {
    if (chronicleEventTailer != null) {
      chronicleEventTailer.setAccountId(accountId);
      chronicleEventTailer.startAsync().awaitRunning();
    }
  }

  private void pollForTask() {
    if (shouldContactManager()) {
      try {
        DelegateTaskEventsResponse taskEventsResponse =
            HTimeLimiter.callInterruptible21(delegateTaskTimeLimiter, Duration.ofSeconds(15),
                () -> executeRestCall(delegateAgentManagerClient.pollTaskEvents(delegateId, accountId)));
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

  private boolean shouldProcessDelegateTaskEvents(DelegateTaskEventsResponse taskEventsResponse) {
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

  private void startDynamicHandlingOfTasks() {
    log.info("Starting dynamic handling of tasks tp {} ms", 1000);
    try {
      maxProcessRSSThresholdMB = MemoryHelper.getProcessMaxMemoryMB() * RESOURCE_USAGE_THRESHOLD * 100;
      maxPodRSSThresholdMB = MemoryHelper.getPodMaxMemoryMB() * RESOURCE_USAGE_THRESHOLD * 100;

      if (maxPodRSSThresholdMB < 1 || maxProcessRSSThresholdMB < 1) {
        log.info("Error while fetching memory information, will not enable dynamic handling of tasks");
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
      log.info("Error while fetching memory information, will not enable dynamic handling of tasks", ex);
    }
  }

  private void startHeartbeat(DelegateParamsBuilder builder, Socket socket) {
    log.debug("Starting heartbeat at interval {} ms", delegateConfiguration.getHeartbeatIntervalMs());
    healthMonitorExecutor.scheduleAtFixedRate(() -> {
      try {
        sendHeartbeat(builder, socket);
        if (heartbeatSuccessCalls.incrementAndGet() > 100) {
          log.info("Sent {} heartbeat calls to manager", heartbeatSuccessCalls.getAndSet(0));
        }
      } catch (Exception ex) {
        log.error("Exception while sending heartbeat", ex);
      }
      // Log delegate performance after every 60 sec i.e. heartbeat interval.
      logCurrentTasks();
    }, 0, delegateConfiguration.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
  }

  private void startHeartbeat(DelegateParamsBuilder builder) {
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
    }, 0, delegateConfiguration.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
  }

  private void startKeepAlivePacket(DelegateParamsBuilder builder) {
    log.info("Starting Keep Alive Request at interval {} ms", KEEP_ALIVE_INTERVAL);
    healthMonitorExecutor.scheduleAtFixedRate(() -> {
      try {
        sendKeepAlivePacket(builder);
      } catch (Exception ex) {
        log.error("Exception while sending Keep Alive Request: ", ex);
      }
    }, 0, KEEP_ALIVE_INTERVAL, TimeUnit.MILLISECONDS);
  }

  private void startLocalHeartbeat() {
    log.debug("Starting local heartbeat");
    healthMonitorExecutor.scheduleAtFixedRate(() -> {
      try {
        log.debug("Sending local heartbeat");
        sendLocalHeartBeat();
      } catch (Exception e) {
        log.error("Exception while scheduling local heartbeat and filling status data", e);
      }
    }, 0, LOCAL_HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
  }

  private void sendLocalHeartBeat() {
    log.debug("Filling status data");
    Map<String, Object> statusData = new HashMap<>();
    if (selfDestruct.get()) {
      statusData.put(DELEGATE_SELF_DESTRUCT, true);
    } else {
      statusData.put(DELEGATE_HEARTBEAT, clock.millis());
      statusData.put(DELEGATE_VERSION, getVersionWithPatch());
      statusData.put(DELEGATE_IS_NEW, false);
      statusData.put(DELEGATE_RESTART_NEEDED, doRestartDelegate());
      statusData.put(DELEGATE_SHUTDOWN_PENDING, !acquireTasks.get());
      // dont pass null delegateId, instead pass "Unregistered" as delegateId
      statusData.put(DELEGATE_ID, getDelegateId().orElse(UNREGISTERED));
      statusData.put(DELEGATE_TOKEN_NAME, DelegateAgentCommonVariables.getDelegateTokenName());
      if (switchStorage.get() && !switchStorageMsgSent) {
        statusData.put(DELEGATE_SWITCH_STORAGE, TRUE);
        log.info("Switch storage message sent");
        switchStorageMsgSent = true;
      }
      if (sendJreInformationToWatcher) {
        statusData.put(DELEGATE_JRE_VERSION, System.getProperty(JAVA_VERSION));
        statusData.put(MIGRATE_TO_JRE_VERSION, migrateToJreVersion);
      }
      if (!acquireTasks.get()) {
        if (stoppedAcquiringAt == 0) {
          stoppedAcquiringAt = clock.millis();
        }
        statusData.put(DELEGATE_SHUTDOWN_STARTED, stoppedAcquiringAt);
      }
      if (isNotBlank(migrateTo)) {
        statusData.put(DELEGATE_MIGRATE, migrateTo);
      }
    }
    messageService.putAllData(DELEGATE_DASH + getProcessId(), statusData);
  }

  private void startMonitoringWatcher() {
    watcherMonitorExecutor.scheduleAtFixedRate(() -> {
      try {
        long watcherHeartbeat = Optional.ofNullable(messageService.getData(WATCHER_DATA, WATCHER_HEARTBEAT, Long.class))
                                    .orElse(clock.millis());
        boolean heartbeatTimedOut = clock.millis() - watcherHeartbeat > WATCHER_HEARTBEAT_TIMEOUT;
        if (heartbeatTimedOut) {
          log.warn("Watcher heartbeat not seen for {} seconds", WATCHER_HEARTBEAT_TIMEOUT / 1000L);
          watcherUpgrade(true);
        }
      } catch (Exception e) {
        log.error("Exception while scheduling local heartbeat", e);
      }
    }, 0, LOCAL_HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
  }

  private void watcherUpgrade(boolean heartbeatTimedOut) {
    String watcherVersion = messageService.getData(WATCHER_DATA, WATCHER_VERSION, String.class);
    String expectedVersion = substringBefore(findExpectedWatcherVersion(), "-").trim();
    if (expectedVersion == null || StringUtils.equals(expectedVersion, watcherVersion)) {
      watcherVersionMatchedAt = clock.millis();
    }
    boolean versionMatchTimedOut = clock.millis() - watcherVersionMatchedAt > WATCHER_VERSION_MATCH_TIMEOUT;
    if (versionMatchTimedOut) {
      log.warn("Watcher version mismatched for {} seconds. Version is {} but should be {}",
          WATCHER_VERSION_MATCH_TIMEOUT / 1000L, watcherVersion, expectedVersion);
    }

    boolean multiVersionRestartNeeded = multiVersion && clock.millis() - startTime > WATCHER_VERSION_MATCH_TIMEOUT
        && !new File(getVersionWithPatch()).exists();

    if (heartbeatTimedOut || versionMatchTimedOut
        || (multiVersionRestartNeeded && multiVersionWatcherStarted.compareAndSet(false, true))) {
      String watcherProcess = messageService.getData(WATCHER_DATA, WATCHER_PROCESS, String.class);
      log.warn("Watcher process {} needs restart", watcherProcess);
      healthMonitorExecutor.submit(
          () -> performWatcherUpgrade(watcherProcess, multiVersionRestartNeeded, expectedVersion, heartbeatTimedOut));
    }
  }

  private void performWatcherUpgrade(
      String watcherProcess, boolean multiVersionRestartNeeded, String expectedVersion, boolean heartbeatTimedOut) {
    synchronized (this) {
      try {
        // Download watcher script before restarting watcher.
        if (!downloadRunScriptsForWatcher(expectedVersion) && heartbeatTimedOut) {
          // If hearbeat for watcher has been timedout means. watcher is either stuck or is dead and we failed to
          // download watcher start script. Hence we will not be able to start watcher with latest version.
          // Hence return early so that pod's liveliness check will fail and delegate will restart.
          log.error("Watcher heartbeat timed out and Delegate unable to download run script, skip starting watcher");
          return;
        }

        ProcessControl.ensureKilledForExpression(WATCHER_EXPRESSION);
        messageService.closeChannel(WATCHER, watcherProcess);
        sleep(ofSeconds(2));
        // Prevent a second restart attempt right away at next heartbeat by writing the watcher heartbeat and
        // resetting version matched timestamp
        messageService.putData(WATCHER_DATA, WATCHER_HEARTBEAT, clock.millis());
        watcherVersionMatchedAt = clock.millis();
        StartedProcess newWatcher = new ProcessExecutor()
                                        .command("nohup", "./start.sh")
                                        .redirectError(Slf4jStream.of("RestartWatcherScript").asError())
                                        .redirectOutput(Slf4jStream.of("RestartWatcherScript").asInfo())
                                        .readOutput(true)
                                        .setMessageLogger((log, format, arguments) -> log.info(format, arguments))
                                        .start();
        if (multiVersionRestartNeeded && newWatcher.getProcess().isAlive()) {
          sleep(ofSeconds(20L));
          FileUtils.forceDelete(new File("delegate.sh"));
          FileUtils.forceDelete(new File("delegate.jar"));
          restartNeeded.set(true);
        }
      } catch (Exception e) {
        log.error("Error restarting watcher {}", watcherProcess, e);
      }
    }
  }

  private boolean downloadRunScriptsForWatcher(String version) {
    RestResponse<DelegateScripts> restResponse;
    try {
      if (!delegateNg) {
        log.info("Calling getDelegateScripts with version{}}", version);
        restResponse = HTimeLimiter.callInterruptible21(delegateHealthTimeLimiter, Duration.ofMinutes(1),
            ()
                -> executeRestCall(delegateAgentManagerClient.getDelegateScripts(
                    accountId, version, DEFAULT_PATCH_VERSION, DELEGATE_NAME)));
      } else {
        log.info("Calling getDelegateScriptsNg with version{}}", version);
        restResponse = HTimeLimiter.callInterruptible21(delegateHealthTimeLimiter, Duration.ofMinutes(1),
            ()
                -> executeRestCall(delegateAgentManagerClient.getDelegateScriptsNg(
                    accountId, version, DEFAULT_PATCH_VERSION, DELEGATE_NAME)));
      }

      if (restResponse == null) {
        log.warn("Received empty response from manager while executing DelegateScript call.");
        return false;
      }

      DelegateScripts delegateScripts = restResponse.getResource();

      File scriptFile = new File(START_SH);
      String script = delegateScripts.getScriptByName(START_SH);

      if (isNotEmpty(script)) {
        Files.deleteIfExists(Paths.get(START_SH));
        try (BufferedWriter writer = Files.newBufferedWriter(scriptFile.toPath())) {
          writer.write(script, 0, script.length());
          writer.flush();
        }
        log.info("Done replacing file [{}]. Set User and Group permission", scriptFile);
        Files.setPosixFilePermissions(scriptFile.toPath(),
            newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
        log.info("Done setting file permissions");
        return true;
      }
      log.error("Script for file [{}] was not replaced", scriptFile);
      return false;
    } catch (Exception e) {
      log.error("Error executing DelegateScript call to manager", e);
      return false;
    }
  }

  private String findExpectedWatcherVersion() {
    if (multiVersion) {
      try {
        RestResponse<String> restResponse =
            executeRestCall(delegateAgentManagerClient.getWatcherVersion(delegateConfiguration.getAccountId()));
        if (restResponse != null) {
          return restResponse.getResource();
        }
      } catch (Exception e) {
        log.warn("Encountered error while fetching watcher version from manager ", e);
      }
    }
    try {
      // Try fetching watcher version from gcp
      String watcherMetadata = Http.getResponseStringFromUrl(delegateConfiguration.getWatcherCheckLocation(), 10, 10);
      return substringBefore(watcherMetadata, " ").trim();
    } catch (IOException e) {
      log.warn("Unable to fetch watcher version information", e);
      return null;
    }
  }

  private boolean doRestartDelegate() {
    if (!acquireTasks.get()) {
      // Skip checking other cases if Delegate isn't acquiring tasks.
      return true;
    }

    long now = clock.millis();

    boolean heartbeatExpired = ((now - lastHeartbeatSentAt.get()) > HEARTBEAT_TIMEOUT)
        || ((now - lastHeartbeatReceivedAt.get()) > HEARTBEAT_TIMEOUT);
    boolean freezeIntervalExpired = (now - frozenAt.get()) > FROZEN_TIMEOUT;

    final boolean doRestart = new File(START_SH).exists()
        && (restartNeeded.get() || (!frozen.get() && heartbeatExpired) || (frozen.get() && freezeIntervalExpired));
    if (doRestart) {
      log.info(
          "Restarting delegate - variable values: restartNeeded:[{}], frozen: [{}], freezeIntervalExpired: [{}],  heartbeatExpired:[{}], lastHeartbeatReceivedAt:[{}], lastHeartbeatSentAt:[{}]",
          restartNeeded.get(), frozen.get(), freezeIntervalExpired, heartbeatExpired, lastHeartbeatReceivedAt.get(),
          lastHeartbeatSentAt.get());
    }
    return doRestart;
  }

  private void processHeartbeat(DelegateHeartbeatResponseStreaming delegateHeartbeatResponse) {
    String receivedId = delegateHeartbeatResponse.getDelegateId();
    if (delegateId.equals(receivedId)) {
      final long now = clock.millis();
      final long diff = now - lastHeartbeatSentAt.longValue();
      if (diff > TimeUnit.MINUTES.toMillis(3)) {
        log.warn(
            "Delegate {} received heartbeat response {} after sending. {} since last recorded heartbeat response. Harness sent response {} back",
            receivedId, getDurationString(lastHeartbeatSentAt.get(), now),
            getDurationString(lastHeartbeatReceivedAt.get(), now),
            getDurationString(delegateHeartbeatResponse.getResponseSentAt(), now));
      } else {
        log.debug("Delegate {} received heartbeat response {} after sending. {} since last response.", receivedId,
            getDurationString(lastHeartbeatSentAt.get(), now), getDurationString(lastHeartbeatReceivedAt.get(), now));
      }
      if (isEcsDelegate()) {
        handleEcsDelegateSpecificMessage(delegateHeartbeatResponse);
      }
      lastHeartbeatReceivedAt.set(now);
    } else {
      log.info("Heartbeat response for another delegate received: {}", receivedId);
    }
  }

  private void sendHeartbeat(DelegateParamsBuilder builder, Socket socket) {
    if (!shouldContactManager() || !acquireTasks.get() || frozen.get()) {
      return;
    }
    log.info("Last heartbeat received at {} and sent to manager at {}", lastHeartbeatReceivedAt.get(),
        lastHeartbeatReceivedAt.get());
    long now = clock.millis();
    boolean heartbeatReceivedTimeExpired = lastHeartbeatReceivedAt.get() != 0
        && (now - lastHeartbeatReceivedAt.get()) > HEARTBEAT_SOCKET_TIMEOUT && !closingSocket.get();
    if (heartbeatReceivedTimeExpired) {
      log.info("Reconnecting delegate - web socket connection: lastHeartbeatReceivedAt:[{}], lastHeartbeatSentAt:[{}]",
          lastHeartbeatReceivedAt.get(), lastHeartbeatSentAt.get());
      closeAndReconnectSocket();
    }
    if (socket.status() == STATUS.OPEN || socket.status() == STATUS.REOPENED) {
      log.debug("Sending heartbeat...");

      // This will Add ECS delegate specific fields if DELEGATE_TYPE = "ECS"
      updateBuilderIfEcsDelegate(builder);
      DelegateParams delegateParamsECS = builder.build()
                                             .toBuilder()
                                             .delegateId(delegateId)
                                             .lastHeartBeat(clock.millis())
                                             .location(Paths.get("").toAbsolutePath().toString())
                                             .tokenName(DelegateAgentCommonVariables.getDelegateTokenName())
                                             .delegateConnectionId(delegateConnectionId)
                                             .token(tokenGenerator.getToken("https", "localhost", 9090, HOST_NAME))
                                             .build();

      // Send only minimal params over web socket to record HB
      DelegateParams delegatesParams = DelegateParams.builder()
                                           .delegateId(delegateId)
                                           .accountId(accountId)
                                           .lastHeartBeat(clock.millis())
                                           .version(getVersion())
                                           .location(Paths.get("").toAbsolutePath().toString())
                                           .tokenName(DelegateAgentCommonVariables.getDelegateTokenName())
                                           .delegateConnectionId(delegateConnectionId)
                                           .token(tokenGenerator.getToken("https", "localhost", 9090, HOST_NAME))
                                           .build();
      try {
        final DelegateParams delegateParams = isEcsDelegate() ? delegateParamsECS : delegatesParams;
        HTimeLimiter.callInterruptible21(
            delegateHealthTimeLimiter, Duration.ofSeconds(15), () -> socket.fire(JsonUtils.asJson(delegateParams)));
        lastHeartbeatSentAt.set(clock.millis());
        sentFirstHeartbeat.set(true);
      } catch (UncheckedTimeoutException ex) {
        log.warn("Timed out sending heartbeat", ex);
      } catch (Exception e) {
        log.error("Error sending heartbeat", e);
      }
    } else {
      log.warn("Socket is not open, status: {}", socket.status().toString());
    }
  }

  private void sendHeartbeat(DelegateParamsBuilder builder) {
    if (!shouldContactManager() || !acquireTasks.get() || frozen.get()) {
      return;
    }
    try {
      updateBuilderIfEcsDelegate(builder);
      DelegateParams delegateParams = builder.build()
                                          .toBuilder()
                                          .keepAlivePacket(false)
                                          .location(Paths.get("").toAbsolutePath().toString())
                                          .tokenName(DelegateAgentCommonVariables.getDelegateTokenName())
                                          .delegateConnectionId(delegateConnectionId)
                                          .token(tokenGenerator.getToken("https", "localhost", 9090, HOST_NAME))
                                          .version(getVersion())
                                          .build();
      lastHeartbeatSentAt.set(clock.millis());
      sentFirstHeartbeat.set(true);
      RestResponse<DelegateHeartbeatResponse> delegateParamsResponse =
          executeRestCall(delegateAgentManagerClient.delegateHeartbeat(accountId, delegateParams));
      long now = clock.millis();
      log.info("[Polling]: Delegate {} received heartbeat response {} after sending at {}. {} since last response.",
          delegateId, getDurationString(lastHeartbeatSentAt.get(), now), now,
          getDurationString(lastHeartbeatReceivedAt.get(), now));
      lastHeartbeatReceivedAt.set(now);

      DelegateHeartbeatResponse receivedDelegateResponse = delegateParamsResponse.getResource();

      if (delegateId.equals(receivedDelegateResponse.getDelegateId())) {
        if (DelegateInstanceStatus.DELETED == DelegateInstanceStatus.valueOf(receivedDelegateResponse.getStatus())) {
          initiateSelfDestruct();
        } else {
          builder.delegateId(receivedDelegateResponse.getDelegateId());
        }
        lastHeartbeatSentAt.set(clock.millis());
        lastHeartbeatReceivedAt.set(clock.millis());
        updateTokenAndSeqNumFromPollingResponse(
            receivedDelegateResponse.getDelegateRandomToken(), receivedDelegateResponse.getSequenceNumber());
      }

      setSwitchStorage(receivedDelegateResponse.isUseCdn());
      updateJreVersion(receivedDelegateResponse.getJreVersion());

      lastHeartbeatSentAt.set(clock.millis());

    } catch (UncheckedTimeoutException ex) {
      log.warn("Timed out sending heartbeat", ex);
    } catch (Exception e) {
      log.error("Error sending heartbeat", e);
    }
  }

  private void setSwitchStorage(boolean useCdn) {
    boolean usingCdn = delegateConfiguration.isUseCdn();
    if (usingCdn != useCdn) {
      log.debug("Switch storage - usingCdn: [{}], useCdn: [{}]", usingCdn, useCdn);
      switchStorage.set(true);
    }
  }

  private void sendKeepAlivePacket(DelegateParamsBuilder builder) {
    if (!shouldContactManager()) {
      return;
    }

    try {
      updateBuilderIfEcsDelegate(builder);
      DelegateParams delegateParams =
          builder.build().toBuilder().keepAlivePacket(true).pollingModeEnabled(true).build();
      executeRestCall(delegateAgentManagerClient.registerDelegate(accountId, delegateParams));
    } catch (UncheckedTimeoutException ex) {
      log.warn("Timed out sending Keep Alive Request", ex);
    } catch (Exception e) {
      log.error("Error sending Keep Alive Request", e);
    }
  }

  private void updateTokenAndSeqNumFromPollingResponse(String delegateRandomToken, String sequenceNumber) {
    if (isEcsDelegate()) {
      handleEcsDelegateSpecificMessage(TOKEN + delegateRandomToken + SEQ + sequenceNumber);
    }
  }

  @Getter(lazy = true)
  private final ImmutableMap<String, ThreadPoolExecutor> logExecutors =
      NullSafeImmutableMap.<String, ThreadPoolExecutor>builder()
          .putIfNotNull("taskExecutor", taskExecutor)
          .putIfNotNull("timeoutEnforcement", timeoutEnforcement)
          .build();

  public Map<String, String> obtainPerformance() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.put("maxValidatingTasksCount", Integer.toString(maxValidatingTasksCount.getAndSet(0)));
    builder.put("maxExecutingTasksCount", Integer.toString(maxExecutingTasksCount.getAndSet(0)));
    builder.put("maxExecutingFuturesCount", Integer.toString(maxExecutingFuturesCount.getAndSet(0)));

    OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    builder.put("cpu-process",
        BigDecimal.valueOf(osBean.getProcessCpuLoad() * 100).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
    builder.put("cpu-system",
        BigDecimal.valueOf(osBean.getSystemCpuLoad() * 100).setScale(2, BigDecimal.ROUND_HALF_UP).toString());

    for (Entry<String, ThreadPoolExecutor> executorEntry : getLogExecutors().entrySet()) {
      builder.put(executorEntry.getKey(), Integer.toString(executorEntry.getValue().getActiveCount()));
    }
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    memoryUsage(builder, "heap-", memoryMXBean.getHeapMemoryUsage());

    memoryUsage(builder, "non-heap-", memoryMXBean.getNonHeapMemoryUsage());

    return builder.build();
  }

  public double getCPULoadAverage() {
    double loadAvg = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    int cores = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
    return (loadAvg / cores) * 100;
  }

  private void logCurrentTasks() {
    try (AutoLogContext ignore = new AutoLogContext(obtainPerformance(), OVERRIDE_NESTS)) {
      log.info("Current performance");
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
    final String delegateTaskId = delegateTaskEvent.getDelegateTaskId();
    if (delegateTaskId == null) {
      log.warn("Delegate task id cannot be null");
      return;
    }

    if (!shouldContactManager()) {
      log.info("Dropping task, self destruct in progress");
      return;
    }

    if (rejectRequest.get()) {
      log.info("Delegate running out of resources, dropping this request");
      return;
    }

    if (currentlyExecutingFutures.containsKey(delegateTaskEvent.getDelegateTaskId())) {
      log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
      return;
    }

    if (delegateTaskEvent.getTaskType() != null) {
      if (!supportedTaskTypes.contains(delegateTaskEvent.getTaskType())) {
        log.info("Task {} of type {} not supported by delegate", delegateTaskId, delegateTaskEvent.getTaskType());
        return;
      }
    } else {
      log.info("Task type not available for Task {}", delegateTaskId);
    }

    DelegateTaskExecutionData taskExecutionData = DelegateTaskExecutionData.builder().build();
    if (currentlyExecutingFutures.putIfAbsent(delegateTaskId, taskExecutionData) == null) {
      final Future<?> taskFuture = taskExecutor.submit(() -> dispatchDelegateTask(delegateTaskEvent));
      taskExecutionData.setTaskFuture(taskFuture);
      updateCounterIfLessThanCurrent(maxExecutingFuturesCount, currentlyExecutingFutures.size());
      return;
    }

    log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
  }

  private void dispatchDelegateTask(DelegateTaskEvent delegateTaskEvent) {
    try (TaskLogContext ignore = new TaskLogContext(delegateTaskEvent.getDelegateTaskId(), OVERRIDE_ERROR)) {
      String delegateTaskId = delegateTaskEvent.getDelegateTaskId();

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

        if (currentlyValidatingTasks.containsKey(delegateTaskId)) {
          log.info("Task [DelegateTaskEvent: {}] already validating. Don't validate again", delegateTaskEvent);
          return;
        }

        currentlyAcquiringTasks.add(delegateTaskId);

        log.debug("Try to acquire DelegateTask - accountId: {}", accountId);
        Call<DelegateTaskPackage> acquireCall =
            delegateAgentManagerClient.acquireTask(delegateId, delegateTaskId, accountId, delegateInstanceId);

        DelegateTaskPackage delegateTaskPackage = executeAcquireCallWithRetry(
            acquireCall, String.format("Failed acquiring delegate task %s by delegate %s", delegateTaskId, delegateId));

        if (delegateTaskPackage == null || delegateTaskPackage.getData() == null) {
          if (delegateTaskPackage == null) {
            log.warn("Delegate task package is null");
          } else {
            log.info("task has been already acquired, executed or timed out");
          }
          return;
        } else {
          log.debug("received task package {} for delegateInstance {}", delegateTaskPackage, delegateInstanceId);
        }

        if (isEmpty(delegateTaskPackage.getDelegateInstanceId())) {
          // Not whitelisted. Perform validation.
          // TODO: Remove this once TaskValidation does not use secrets

          // applyDelegateSecretFunctor(delegatePackage);
          DelegateValidateTask delegateValidateTask = getDelegateValidateTask(delegateTaskEvent, delegateTaskPackage);
          injector.injectMembers(delegateValidateTask);
          currentlyValidatingTasks.put(delegateTaskPackage.getDelegateTaskId(), delegateTaskPackage);
          updateCounterIfLessThanCurrent(maxValidatingTasksCount, currentlyValidatingTasks.size());
          delegateValidateTask.validationResults();
        } else if (delegateInstanceId.equals(delegateTaskPackage.getDelegateInstanceId())) {
          applyDelegateSecretFunctor(delegateTaskPackage);

          // Whitelisted. Proceed immediately.
          log.debug("Delegate {} whitelisted for task and accountId: {}", delegateId, accountId);
          executeTask(delegateTaskPackage);
        }

      } catch (Exception e) {
        log.error("Unable to get task for validation", e);
      } finally {
        currentlyAcquiringTasks.remove(delegateTaskId);
        currentlyExecutingFutures.remove(delegateTaskId);
      }
    }
  }

  private DelegateValidateTask getDelegateValidateTask(
      DelegateTaskEvent delegateTaskEvent, DelegateTaskPackage delegateTaskPackage) {
    Consumer<List<DelegateConnectionResultDetail>> postValidationFunction =
        getPostValidationFunction(delegateTaskEvent, delegateTaskPackage.getDelegateTaskId());

    return new CapabilityCheckController(delegateId, delegateTaskPackage, postValidationFunction);
  }

  private Consumer<List<DelegateConnectionResultDetail>> getPostValidationFunction(
      DelegateTaskEvent delegateTaskEvent, String taskId) {
    return delegateConnectionResults -> {
      try (AutoLogContext ignored = new TaskLogContext(taskId, OVERRIDE_ERROR)) {
        // Tools might be installed asynchronously, so get the flag early on
        currentlyValidatingTasks.remove(taskId);
        List<DelegateConnectionResultDetail> results =
            Optional.ofNullable(delegateConnectionResults).orElse(emptyList());
        boolean validated = results.stream().allMatch(DelegateConnectionResultDetail::isValidated);
        log.info("Validation {} for task", validated ? "succeeded" : "failed");
        try {
          DelegateTaskPackage delegateTaskPackage = execute(
              delegateAgentManagerClient.reportConnectionResults(delegateId, delegateTaskEvent.getDelegateTaskId(),
                  accountId, delegateInstanceId, getDelegateConnectionResultDetails(results)));

          if (delegateTaskPackage != null && delegateTaskPackage.getData() != null
              && delegateInstanceId.equals(delegateTaskPackage.getDelegateInstanceId())) {
            applyDelegateSecretFunctor(delegateTaskPackage);
            executeTask(delegateTaskPackage);
          } else {
            log.warn("Did not get the go-ahead to proceed for task");
          }
        } catch (IOException e) {
          log.error("Unable to report validation results for task", e);
        }
      }
    };
  }

  private List<DelegateConnectionResultDetail> getDelegateConnectionResultDetails(
      List<DelegateConnectionResultDetail> results) {
    List<DelegateConnectionResultDetail> delegateConnectionResultDetails = new ArrayList<>();
    for (DelegateConnectionResultDetail source : results) {
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

    log.debug("DelegateTask acquired - accountId: {}, taskType: {}", accountId, taskData.getTaskType());
    Pair<String, Set<String>> activitySecrets = obtainActivitySecrets(delegateTaskPackage);
    Optional<LogSanitizer> sanitizer = getLogSanitizer(activitySecrets);
    ILogStreamingTaskClient logStreamingTaskClient = getLogStreamingTaskClient(activitySecrets, delegateTaskPackage);
    // At the moment used to download and render terraform json plan file and keep track of the download tf plans
    // so we can clean up at the end of the task. Expected mainly to be used in Shell Script Task
    // but not limited to usage in other tasks
    DelegateExpressionEvaluator delegateExpressionEvaluator = new DelegateExpressionEvaluator(
        injector, delegateTaskPackage.getAccountId(), delegateTaskPackage.getData().getExpressionFunctorToken());
    applyDelegateExpressionEvaluator(delegateTaskPackage, delegateExpressionEvaluator);

    DelegateRunnableTask delegateRunnableTask = delegateTaskFactory.getDelegateRunnableTask(
        TaskType.valueOf(taskData.getTaskType()), delegateTaskPackage, logStreamingTaskClient,
        getPostExecutionFunction(delegateTaskPackage.getDelegateTaskId(), sanitizer.orElse(null),
            logStreamingTaskClient, delegateExpressionEvaluator, delegateTaskPackage.isShouldSkipOpenStream()),
        getPreExecutionFunction(delegateTaskPackage, sanitizer.orElse(null), logStreamingTaskClient));
    if (delegateRunnableTask instanceof AbstractDelegateRunnableTask) {
      ((AbstractDelegateRunnableTask) delegateRunnableTask).setDelegateHostname(HOST_NAME);
    }
    injector.injectMembers(delegateRunnableTask);
    currentlyExecutingFutures.get(delegateTaskPackage.getDelegateTaskId()).setExecutionStartTime(clock.millis());

    // Submit execution for watching this task execution.
    timeoutEnforcement.submit(() -> enforceDelegateTaskTimeout(delegateTaskPackage.getDelegateTaskId(), taskData));

    // Start task execution in same thread and measure duration.
    if (isImmutableDelegate) {
      metricRegistry.recordGaugeDuration(TASK_EXECUTION_TIME.getMetricName(),
          new String[] {DELEGATE_NAME, taskData.getTaskType()}, delegateRunnableTask);
    } else {
      delegateRunnableTask.run();
    }
  }

  private ILogStreamingTaskClient getLogStreamingTaskClient(
      Pair<String, Set<String>> activitySecrets, DelegateTaskPackage delegateTaskPackage) {
    boolean logStreamingConfigPresent = false;
    boolean logCallbackConfigPresent = false;
    String appId = null;
    String activityId = null;

    if (logStreamingClient != null && !isBlank(delegateTaskPackage.getLogStreamingToken())
        && !isEmpty(delegateTaskPackage.getLogStreamingAbstractions())) {
      logStreamingConfigPresent = true;
    }

    // Extract appId and activityId from task params, in case LogCallback logging has to be used for backward
    // compatibility reasons
    Object[] taskParameters = delegateTaskPackage.getData().getParameters();
    if (taskParameters != null && taskParameters.length == 1 && taskParameters[0] instanceof Cd1ApplicationAccess
        && taskParameters[0] instanceof ActivityAccess) {
      Cd1ApplicationAccess applicationAccess = (Cd1ApplicationAccess) taskParameters[0];
      appId = applicationAccess.getAppId();

      ActivityAccess activityAccess = (ActivityAccess) taskParameters[0];
      activityId = activityAccess.getActivityId();
    }

    if (!isBlank(appId) && !isBlank(activityId)) {
      logCallbackConfigPresent = true;
    }

    if (!logStreamingConfigPresent && !logCallbackConfigPresent) {
      return null;
    }

    String logBaseKey;
    if (!StringUtils.isBlank(delegateTaskPackage.getBaseLogKey())) {
      logBaseKey = delegateTaskPackage.getBaseLogKey();
    } else {
      logBaseKey = delegateTaskPackage.getLogStreamingAbstractions() != null
          ? LogStreamingHelper.generateLogBaseKey(delegateTaskPackage.getLogStreamingAbstractions())
          : EMPTY;
    }

    LogStreamingTaskClientBuilder taskClientBuilder =
        LogStreamingTaskClient.builder()
            .logStreamingClient(logStreamingClient)
            .accountId(delegateTaskPackage.getAccountId())
            .token(delegateTaskPackage.getLogStreamingToken())
            .logStreamingSanitizer(
                LogStreamingSanitizer.builder()
                    .secrets(activitySecrets.getRight().stream().map(String::trim).collect(Collectors.toSet()))
                    .build())
            .baseLogKey(logBaseKey)
            .logService(delegateLogService)
            .taskProgressExecutor(taskProgressExecutor)
            .appId(appId)
            .activityId(activityId);

    if (isNotBlank(delegateTaskPackage.getDelegateCallbackToken()) && delegateServiceAgentClient != null) {
      taskClientBuilder.taskProgressClient(TaskProgressClient.builder()
                                               .accountId(delegateTaskPackage.getAccountId())
                                               .taskId(delegateTaskPackage.getDelegateTaskId())
                                               .delegateCallbackToken(delegateTaskPackage.getDelegateCallbackToken())
                                               .delegateServiceAgentClient(delegateServiceAgentClient)
                                               .kryoSerializer(kryoSerializer)
                                               .build());
    }

    return taskClientBuilder.build();
  }

  private Optional<LogSanitizer> getLogSanitizer(Pair<String, Set<String>> activitySecrets) {
    // Create log sanitizer only if activityId and secrets are present
    if (isNotBlank(activitySecrets.getLeft()) && isNotEmpty(activitySecrets.getRight())) {
      return Optional.of(new ActivityBasedLogSanitizer(activitySecrets.getLeft(), activitySecrets.getRight()));
    } else {
      return Optional.empty();
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
      } else if (parameters[0] instanceof ShellScriptProvisionParameters) {
        // Shell Script Provision
        ShellScriptProvisionParameters shellScriptProvisionParameters = (ShellScriptProvisionParameters) parameters[0];
        Map<String, EncryptedDataDetail> encryptedVariables = shellScriptProvisionParameters.getEncryptedVariables();
        if (isNotEmpty(encryptedVariables)) {
          for (Entry<String, EncryptedDataDetail> encryptedVariable : encryptedVariables.entrySet()) {
            secrets.add(String.valueOf(encryptionService.getDecryptedValue(encryptedVariable.getValue(), false)));
          }
        }
      } else if (parameters[0] instanceof CommandParameters) {
        // Command
        CommandParameters commandParameters = (CommandParameters) parameters[0];
        activityId = commandParameters.getActivityId();
        secrets.addAll(secretsFromMaskedVariables(
            commandParameters.getServiceVariables(), commandParameters.getSafeDisplayServiceVariables()));
      }
    } else {
      if (parameters.length >= 2 && parameters[0] instanceof Command
          && parameters[1] instanceof CommandExecutionContext) {
        // Command
        CommandExecutionContext context = (CommandExecutionContext) parameters[1];
        activityId = context.getActivityId();
        secrets.addAll(
            secretsFromMaskedVariables(context.getServiceVariables(), context.getSafeDisplayServiceVariables()));
      }
    }

    return Pair.of(activityId, secrets);
  }

  private void addSystemSecrets(Set<String> secrets) {
    // Add config file secrets
    secrets.add(delegateConfiguration.getDelegateToken());

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

  private BooleanSupplier getPreExecutionFunction(@NotNull DelegateTaskPackage delegateTaskPackage,
      LogSanitizer sanitizer, ILogStreamingTaskClient logStreamingTaskClient) {
    return () -> {
      if (logStreamingTaskClient != null) {
        try {
          // Opens the log stream for task
          if (!delegateTaskPackage.isShouldSkipOpenStream()) {
            logStreamingTaskClient.openStream(null);
          }
        } catch (Exception ex) {
          log.error("Unexpected error occurred while opening the log stream.");
        }
      }

      if (!currentlyExecutingTasks.containsKey(delegateTaskPackage.getDelegateTaskId())) {
        log.debug("Adding task to executing tasks");
        currentlyExecutingTasks.put(delegateTaskPackage.getDelegateTaskId(), delegateTaskPackage);
        updateCounterIfLessThanCurrent(maxExecutingTasksCount, currentlyExecutingTasks.size());
        if (sanitizer != null) {
          delegateLogService.registerLogSanitizer(sanitizer);
        }
        return true;
      } else {
        // We should have already checked this before acquiring this task. If we here, than we
        // should log an error and abort execution.
        log.info("Task is already being executed");
        return false;
      }
    };
  }

  private void updateCounterIfLessThanCurrent(AtomicInteger counter, int current) {
    counter.updateAndGet(value -> Math.max(value, current));
  }

  private Consumer<DelegateTaskResponse> getPostExecutionFunction(String taskId, LogSanitizer sanitizer,
      ILogStreamingTaskClient logStreamingTaskClient, DelegateExpressionEvaluator delegateExpressionEvaluator,
      boolean shouldSkipCloseStream) {
    return taskResponse -> {
      if (logStreamingTaskClient != null) {
        try {
          // Closes the log stream for the task
          if (!shouldSkipCloseStream) {
            logStreamingTaskClient.closeStream(null);
          }
        } catch (Exception ex) {
          log.error("Unexpected error occurred while closing the log stream.");
        }
      }

      try {
        sendTaskResponse(taskId, taskResponse);
      } finally {
        if (sanitizer != null) {
          delegateLogService.unregisterLogSanitizer(sanitizer);
        }

        if (delegateExpressionEvaluator != null) {
          delegateExpressionEvaluator.cleanup();
        }
        currentlyExecutingTasks.remove(taskId);
        if (currentlyExecutingFutures.remove(taskId) != null) {
          log.debug("Removed from executing futures on post execution");
        }
      }
    };
  }

  private void enforceDelegateTaskTimeout(String taskId, TaskData taskData) {
    long startingTime = currentlyExecutingFutures.get(taskId).getExecutionStartTime();
    boolean stillRunning = true;
    long timeout = taskData.getTimeout() + TimeUnit.SECONDS.toMillis(30L);
    Future<?> taskFuture = null;
    while (stillRunning && clock.millis() - startingTime < timeout) {
      if (log.isDebugEnabled()) {
        log.debug("Task time remaining for {}, taskype {}: {} ms", taskId, taskData.getTaskType(),
            startingTime + timeout - clock.millis());
      }
      sleep(ofSeconds(5));
      taskFuture = currentlyExecutingFutures.get(taskId).getTaskFuture();
      if (taskFuture != null) {
        log.info("Task future: {} - done:{}, cancelled:{}", taskId, taskFuture.isDone(), taskFuture.isCancelled());
      }
      stillRunning = taskFuture != null && !taskFuture.isDone() && !taskFuture.isCancelled();
    }
    if (stillRunning) {
      log.info("Task {} of taskType {} timed out after {} milliseconds", taskId, taskData.getTaskType(), timeout);
      metricRegistry.recordCounterInc(
          TASK_TIMEOUT.getMetricName(), new String[] {DELEGATE_NAME, taskData.getTaskType()});
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
        log.info("Task {} was cancelled", taskId);
      } catch (Exception e) {
        log.error("Error from task future {}", taskId, e);
      }
    }
    currentlyExecutingTasks.remove(taskId);
    if (currentlyExecutingFutures.remove(taskId) != null) {
      log.info("Removed {} from executing futures on timeout", taskId);
    }
  }

  private void cleanupOldDelegateVersionFromBackup() {
    try {
      cleanup(new File(System.getProperty("user.dir")), getVersion(), "backup.");
    } catch (Exception ex) {
      log.error("Failed to clean delegate version [{}] from Backup", ex);
    }
  }

  private void removeDelegateVersionFromCapsule() {
    try {
      cleanup(new File(System.getProperty("capsule.dir")).getParentFile(), getVersionWithPatch(), "delegate-");
    } catch (Exception ex) {
      log.error("Failed to clean delegate version [{}] from Capsule", ex);
    }
  }

  private void cleanup(File dir, String currentVersion, String pattern) {
    FileUtils.listFilesAndDirs(dir, falseFileFilter(), FileFilterUtils.prefixFileFilter(pattern)).forEach(file -> {
      if (!dir.equals(file) && !file.getName().contains(currentVersion)) {
        log.info("[Old] File Name to be deleted = " + file.getAbsolutePath());
        FileUtils.deleteQuietly(file);
      }
    });
  }

  private String getVersion() {
    return versionInfoManager.getVersionInfo().getVersion();
  }

  private String getVersionWithPatch() {
    String version = getVersion();
    if (multiVersion) {
      // Appending '000' as delegate does not support patch version.
      return version + "-000";
    }
    return version;
  }

  private void initiateSelfDestruct() {
    log.info("Self destruct sequence initiated...");
    acquireTasks.set(false);
    restartNeeded.set(false);
    selfDestruct.set(true);

    if (socket != null) {
      finalizeSocket();
    }

    DelegateStackdriverLogAppender.setManagerClient(null);
    if (perpetualTaskWorker != null) {
      perpetualTaskWorker.stop();
    }
  }

  private void migrate(String newUrl) {
    if (!newUrl.endsWith("/")) {
      newUrl = newUrl + "/";
    }
    migrateTo = newUrl;
  }

  private void handleEcsDelegateSpecificMessage(String message) {
    int indexForToken = message.lastIndexOf(TOKEN);
    int indexForSeqNum = message.lastIndexOf(SEQ);
    String token = message.substring(indexForToken + 7, indexForSeqNum);
    String sequenceNum = message.substring(indexForSeqNum + 5);
    // Did not receive correct data, skip updating token and sequence
    if (isInvalidData(token) || isInvalidData(sequenceNum)) {
      return;
    }

    try {
      FileIo.writeWithExclusiveLockAcrossProcesses(
          TOKEN + token + SEQ + sequenceNum, DELEGATE_SEQUENCE_CONFIG_FILE, StandardOpenOption.TRUNCATE_EXISTING);
      log.info("Token Received From Manager : {}, SeqNum Received From Manager: {}", token, sequenceNum);
    } catch (Exception e) {
      log.error("Failed to write registration response into delegate_sequence file", e);
    }
  }

  private void handleEcsDelegateSpecificMessage(DelegateHeartbeatResponseStreaming delegateHeartbeatResponse) {
    String token = delegateHeartbeatResponse.getDelegateRandomToken();
    String sequenceNum = delegateHeartbeatResponse.getSequenceNumber();

    // Did not receive correct data, skip updating token and sequence
    if (isInvalidData(token) || isInvalidData(sequenceNum)) {
      return;
    }

    try {
      FileIo.writeWithExclusiveLockAcrossProcesses(
          TOKEN + token + SEQ + sequenceNum, DELEGATE_SEQUENCE_CONFIG_FILE, StandardOpenOption.TRUNCATE_EXISTING);
      log.info("Token Received From Manager : {}, SeqNum Received From Manager: {}", token, sequenceNum);
    } catch (Exception e) {
      log.error("Failed to write registration response into delegate_sequence file", e);
    }
  }

  private boolean isInvalidData(String value) {
    return isBlank(value) || "null".equalsIgnoreCase(value);
  }

  private void updateBuilderIfEcsDelegate(DelegateParamsBuilder builder) {
    if (!isEcsDelegate()) {
      return;
    }

    try {
      if (!FileIo.checkIfFileExist(DELEGATE_SEQUENCE_CONFIG_FILE)) {
        generateEcsDelegateSequenceConfigFile();
      }

      builder.delegateRandomToken(getRandomTokenForEcsDelegate());
      builder.sequenceNum(getSequenceNumForEcsDelegate());
    } catch (Exception e) {
      log.warn("Failed while reading seqNum and delegateToken from disk file", e);
    }
  }

  private String getSequenceConfigData() {
    if (!isEcsDelegate()) {
      return "";
    }

    try {
      FileUtils.touch(new File(DELEGATE_SEQUENCE_CONFIG_FILE));
      return FileIo.getFileContentsWithSharedLockAcrossProcesses(DELEGATE_SEQUENCE_CONFIG_FILE);
    } catch (Exception e) {
      return "";
    }
  }

  private String getSequenceNumForEcsDelegate() {
    if (!isEcsDelegate()) {
      return null;
    }
    String content = getSequenceConfigData();
    if (isBlank(content)) {
      return null;
    }

    String seqNum = content.substring(content.lastIndexOf(SEQ) + 5);
    return isBlank(seqNum) ? null : seqNum;
  }

  private String getRandomTokenForEcsDelegate() {
    if (!isEcsDelegate()) {
      return null;
    }

    String token = readTokenFromFile();

    if (token == null) {
      FileUtils.deleteQuietly(new File(DELEGATE_SEQUENCE_CONFIG_FILE));
      generateEcsDelegateSequenceConfigFile();
      token = readTokenFromFile();
    }

    return token;
  }

  private String readTokenFromFile() {
    if (!isEcsDelegate()) {
      return null;
    }
    String content = getSequenceConfigData();
    if (isBlank(content)) {
      return null;
    }

    String token = content.substring(7, content.lastIndexOf(SEQ));
    return isBlank(token) ? null : token;
  }

  private void handleEcsDelegateRegistrationResponse(DelegateRegisterResponse delegateResponse) {
    if (!isEcsDelegate()) {
      return;
    }

    try {
      FileIo.writeWithExclusiveLockAcrossProcesses(
          TOKEN + delegateResponse.getDelegateRandomToken() + SEQ + delegateResponse.getSequenceNum(),
          DELEGATE_SEQUENCE_CONFIG_FILE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (Exception e) {
      log.error("Failed to write registration response into delegate_sequence file", e);
    }
  }

  private boolean isEcsDelegate() {
    return IsEcsDelegate;
  }

  private void generateEcsDelegateSequenceConfigFile() {
    try {
      FileUtils.touch(new File(DELEGATE_SEQUENCE_CONFIG_FILE));
      String randomToken = UUIDGenerator.generateUuid();
      FileIo.writeWithExclusiveLockAcrossProcesses(
          TOKEN + randomToken + SEQ, DELEGATE_SEQUENCE_CONFIG_FILE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      log.warn("Failed to create DelegateSequenceConfigFile", e);
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

  @VisibleForTesting
  void applyDelegateSecretFunctor(DelegateTaskPackage delegateTaskPackage) {
    try {
      Map<String, EncryptionConfig> encryptionConfigs = delegateTaskPackage.getEncryptionConfigs();
      Map<String, SecretDetail> secretDetails = delegateTaskPackage.getSecretDetails();
      if (isEmpty(encryptionConfigs) || isEmpty(secretDetails)) {
        return;
      }

      Map<EncryptionConfig, List<EncryptedRecord>> encryptionConfigListMap = new HashMap<>();
      secretDetails.forEach((key, secretDetail) -> {
        addToEncryptedConfigListMap(encryptionConfigListMap, encryptionConfigs.get(secretDetail.getConfigUuid()),
            secretDetail.getEncryptedRecord());
      });

      Map<String, char[]> decryptedRecords = delegateDecryptionService.decrypt(encryptionConfigListMap);
      Map<String, char[]> secretUuidToValues = new HashMap<>();

      secretDetails.forEach((key, value) -> {
        char[] secretValue = decryptedRecords.get(value.getEncryptedRecord().getUuid());
        secretUuidToValues.put(key, secretValue);

        // Adds secret values from the 3 phase decryption to the list of task secrets to be masked
        String secretValueStr =
            isBase64SecretIdentifier(key) ? EncodingUtils.encodeBase64(secretValue) : String.valueOf(secretValue);
        delegateTaskPackage.getSecrets().add(secretValueStr);
      });

      DelegateExpressionEvaluator delegateExpressionEvaluator = new DelegateExpressionEvaluator(
          secretUuidToValues, delegateTaskPackage.getData().getExpressionFunctorToken());
      applyDelegateExpressionEvaluator(delegateTaskPackage, delegateExpressionEvaluator);
    } catch (Exception e) {
      sendErrorResponse(delegateTaskPackage, e);
      throw e;
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

  private boolean shouldContactManager() {
    return !selfDestruct.get();
  }

  @Override
  public void recordMetrics() {
    long tasksExecutionCount = taskExecutor.getActiveCount();
    metricRegistry.recordGaugeValue(
        TASKS_CURRENTLY_EXECUTING.getMetricName(), new String[] {DELEGATE_NAME}, tasksExecutionCount);
  }

  public void sendTaskResponse(final String taskId, final DelegateTaskResponse taskResponse) {
    Response<ResponseBody> response = null;
    try {
      int retries = 5;
      for (int attempt = 0; attempt < retries; attempt++) {
        response = delegateAgentManagerClient.sendTaskStatus(delegateId, taskId, accountId, taskResponse).execute();
        if (response != null && response.code() >= 200 && response.code() <= 299) {
          log.debug("Task {} type {},  response sent to manager", taskId, taskResponse.getTaskTypeName());
          metricRegistry.recordCounterInc(
              TASK_COMPLETED.getMetricName(), new String[] {DELEGATE_NAME, taskResponse.getTaskTypeName()});
          break;
        }
        log.warn("Failed to send response for task {}: {}. error: {}. requested url: {} {}", taskId,
            response == null ? "null" : response.code(),
            response == null || response.errorBody() == null ? "null" : response.errorBody().string(),
            response == null || response.raw() == null || response.raw().request() == null
                ? "null"
                : response.raw().request().url(),
            attempt < (retries - 1) ? "Retrying." : "Giving up.");
        if (attempt < retries - 1) {
          // Do not sleep for last loop round, as we are going to fail.
          sleep(ofSeconds(FibonacciBackOff.getFibonacciElement(attempt)));
        }
      }
    } catch (IOException e) {
      log.error("Unable to send response to manager", e);
      metricRegistry.recordCounterInc(
          TASK_FAILED.getMetricName(), new String[] {DELEGATE_NAME, taskResponse.getTaskTypeName()});
    } finally {
      if (response != null && response.errorBody() != null && !response.isSuccessful()) {
        response.errorBody().close();
      }
      if (response != null && response.body() != null && response.isSuccessful()) {
        response.body().close();
      }
    }
  }

  @Override
  public void sendTaskResponse(final String taskId, final ExecutionStatusResponse taskResponse) {
    throw new UnsupportedOperationException("Proto task status only supported for plugin delegate");
  }

  private void sendErrorResponse(DelegateTaskPackage delegateTaskPackage, Exception exception) {
    String taskId = delegateTaskPackage.getDelegateTaskId();
    DelegateTaskResponse taskResponse =
        DelegateTaskResponse.builder()
            .accountId(delegateTaskPackage.getAccountId())
            .taskTypeName(delegateTaskPackage.getData().getTaskType())
            .responseCode(DelegateTaskResponse.ResponseCode.FAILED)
            .response(ErrorNotifyResponseData.builder().errorMessage(ExceptionUtils.getMessage(exception)).build())
            .build();
    metricRegistry.recordCounterInc(
        TASK_FAILED.getMetricName(), new String[] {DELEGATE_NAME, taskResponse.getTaskTypeName()});
    log.error("Sending error response for task{} due to exception", taskId, exception);
    try {
      Response<ResponseBody> resp;
      int retries = 5;
      for (int attempt = 0; attempt < retries; attempt++) {
        resp = delegateAgentManagerClient.sendTaskStatus(delegateId, taskId, accountId, taskResponse).execute();
        if (resp != null && resp.code() >= 200 && resp.code() <= 299) {
          log.info("Task {} response sent to manager", taskId);
          return;
        }
        log.warn("Failed to send response for task {}: {}. error: {}. requested url: {} {}", taskId,
            resp == null ? "null" : resp.code(),
            resp == null || resp.errorBody() == null ? "null" : resp.errorBody().string(),
            resp == null || resp.raw() == null || resp.raw().request() == null ? "null" : resp.raw().request().url(),
            "Retrying.");
        sleep(ofSeconds(FibonacciBackOff.getFibonacciElement(attempt)));
      }
    } catch (Exception e) {
      log.error("Unable to send response to manager", e);
    }
  }

  // TODO: ARPIT remove this after 1-2 months when we have communicated with the customers.
  private void checkForSSLCertVerification(String accountId) {
    if (isBlank(MANAGER_PROXY_CURL)) {
      MANAGER_PROXY_CURL = EMPTY;
    }
    if (isBlank(MANAGER_HOST_AND_PORT)) {
      MANAGER_HOST_AND_PORT = EMPTY;
    }

    try {
      String commandToCheckAccountStatus =
          "curl " + MANAGER_PROXY_CURL + " -s " + MANAGER_HOST_AND_PORT + "/api/account/" + accountId + "/status";
      log.info("Checking for SSL cert verification with command {}", commandToCheckAccountStatus);
      ProcessExecutor processExecutor =
          new ProcessExecutor().timeout(10, TimeUnit.SECONDS).command("/bin/bash", "-c", commandToCheckAccountStatus);

      int exitcode = processExecutor.execute().getExitValue();
      if (exitcode != 0) {
        log.error("SSL cert verification command failed with exitCode {}", exitcode);
      } else {
        log.info("SSL cert verification successful.");
      }
    } catch (Exception e) {
      log.error("SSL Cert Verification failed with exception ", e);
    }
  }
}
