/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.service;

import static io.harness.concurrent.HTimeLimiter.callInterruptible21;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateConfiguration.Action.SELF_DESTRUCT;
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
import static io.harness.delegate.message.MessageConstants.EXTRA_WATCHER;
import static io.harness.delegate.message.MessageConstants.MIGRATE_TO_JRE_VERSION;
import static io.harness.delegate.message.MessageConstants.NEW_DELEGATE;
import static io.harness.delegate.message.MessageConstants.NEW_WATCHER;
import static io.harness.delegate.message.MessageConstants.NEXT_WATCHER;
import static io.harness.delegate.message.MessageConstants.RUNNING_DELEGATES;
import static io.harness.delegate.message.MessageConstants.UNREGISTERED;
import static io.harness.delegate.message.MessageConstants.WATCHER_DATA;
import static io.harness.delegate.message.MessageConstants.WATCHER_GO_AHEAD;
import static io.harness.delegate.message.MessageConstants.WATCHER_HEARTBEAT;
import static io.harness.delegate.message.MessageConstants.WATCHER_PROCESS;
import static io.harness.delegate.message.MessageConstants.WATCHER_STARTED;
import static io.harness.delegate.message.MessageConstants.WATCHER_VERSION;
import static io.harness.delegate.message.MessengerType.DELEGATE;
import static io.harness.delegate.message.MessengerType.WATCHER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.threading.Morpheus.sleep;
import static io.harness.utils.MemoryPerformanceUtils.memoryUsage;
import static io.harness.watcher.app.WatcherApplication.getProcessId;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.synchronizedList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.io.filefilter.FileFilterUtils.falseFileFilter;
import static org.apache.commons.io.filefilter.FileFilterUtils.nameFileFilter;
import static org.apache.commons.io.filefilter.FileFilterUtils.or;
import static org.apache.commons.io.filefilter.FileFilterUtils.prefixFileFilter;
import static org.apache.commons.io.filefilter.FileFilterUtils.suffixFileFilter;
import static org.apache.commons.io.filefilter.FileFilterUtils.trueFileFilter;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.replace;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.message.Message;
import io.harness.delegate.message.MessageService;
import io.harness.exception.VersionInfoException;
import io.harness.filesystem.FileIo;
import io.harness.grpc.utils.DelegateGrpcConfigExtractor;
import io.harness.logging.AutoLogContext;
import io.harness.managerclient.ManagerClientV2;
import io.harness.managerclient.SafeHttpCall;
import io.harness.network.Http;
import io.harness.rest.RestResponse;
import io.harness.security.SignVerifier;
import io.harness.threading.Schedulable;
import io.harness.utils.ProcessControl;
import io.harness.version.VersionInfoManager;
import io.harness.watcher.app.WatcherApplication;
import io.harness.watcher.app.WatcherConfiguration;
import io.harness.watcher.logging.WatcherStackdriverLogAppender;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

/**
 * Created by brett on 10/26/17
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class WatcherServiceImpl implements WatcherService {
  private static final long DELEGATE_HEARTBEAT_TIMEOUT = MINUTES.toMillis(5);
  private static final long DELEGATE_STARTUP_TIMEOUT = MINUTES.toMillis(1);
  private static final long DELEGATE_SHUTDOWN_TIMEOUT = TimeUnit.HOURS.toMillis(2);
  private static final long DELEGATE_VERSION_MATCH_TIMEOUT = TimeUnit.HOURS.toMillis(2);
  private static final long DELEGATE_RESTART_TO_UPGRADE_JRE_TIMEOUT = MINUTES.toMillis(5);
  private static final int DELEGATE_VERSION_CHECK_FREQ_MINS = 25;
  private static final Pattern VERSION_PATTERN = Pattern.compile("^[1-9]\\.[0-9]\\.[0-9]*-?[0-9]*");
  private static final String DELEGATE_SEQUENCE_CONFIG_FILE = "./delegate_sequence_config";
  private static final String USER_DIR = "user.dir";
  private static final String DELEGATE_RESTART_SCRIPT = "DelegateRestartScript";
  private static final String NO_SPACE_LEFT_ON_DEVICE_ERROR = "No space left on device";
  private static final String FILE_HANDLES_LOGS_FOLDER = "file_handle_logs";
  private final String watcherJreVersion = System.getProperty("java.version");
  private long delegateRestartedToUpgradeJreAt;
  private static String WatcherVersion;

  private static final String DELEGATE_NAME =
      isNotBlank(System.getenv().get("DELEGATE_NAME")) ? System.getenv().get("DELEGATE_NAME") : "";

  private final boolean delegateNg = isNotBlank(System.getenv().get("DELEGATE_SESSION_IDENTIFIER"))
      || (isNotBlank(System.getenv().get("NEXT_GEN")) && Boolean.parseBoolean(System.getenv().get("NEXT_GEN")));

  private static final boolean multiVersion;

  private final Map<String, Process> delegateProcessMap = new ConcurrentHashMap<>();

  static {
    String deployMode = System.getenv().get("DEPLOY_MODE");
    multiVersion = isEmpty(deployMode) || !deployMode.equals("KUBERNETES_ONPREM");
  }
  private static final String DELEGATE_TYPE = System.getenv().get("DELEGATE_TYPE");

  private static final boolean IsShellDelegate = "SHELL_SCRIPT".equals(DELEGATE_TYPE);
  private static final String DELEGATE_SCRIPT = "delegate.sh";

  @Inject @Named("inputExecutor") private ScheduledExecutorService inputExecutor;
  @Inject @Named("heartbeatExecutor") private ScheduledExecutorService heartbeatExecutor;
  @Inject @Named("watchExecutor") private ScheduledExecutorService watchExecutor;
  @Inject @Named("upgradeExecutor") private ScheduledExecutorService upgradeExecutor;
  @Inject private ExecutorService executorService;
  @Inject private Clock clock;
  @Inject private TimeLimiter timeLimiter;
  @Inject private WatcherConfiguration watcherConfiguration;
  @Inject private MessageService messageService;
  @Inject private ManagerClientV2 managerClient;

  private final Object waiter = new Object();
  private final AtomicBoolean working = new AtomicBoolean(false);
  private final List<String> runningDelegates = synchronizedList(new ArrayList<>());

  private final long DISK_SPACE_IS_OK = -1;
  private final AtomicLong lastAvailableDiskSpace = new AtomicLong(DISK_SPACE_IS_OK);

  private final AtomicInteger minMinorVersion = new AtomicInteger(0);
  private final Set<Integer> illegalVersions = new HashSet<>();
  private final Map<String, Long> delegateVersionMatchedAt = new HashMap<>();
  private final AtomicReference<List<String>> cachedDelegateVersion = new AtomicReference<>();

  private final AtomicBoolean frozen = new AtomicBoolean(false);

  @Override
  public void run(boolean upgrade) {
    WatcherStackdriverLogAppender.setTimeLimiter(timeLimiter);
    WatcherStackdriverLogAppender.setManagerClient(managerClient);
    log.info("Watcher will start running on JRE {}", watcherJreVersion);

    performRecencyCheck();

    try {
      log.info(upgrade ? "[New] Upgraded watcher process started. Sending confirmation" : "Watcher process started");
      log.info("Multiversion: {}", multiVersion);
      if (!delegateNg) {
        log.info("Delegate is CG. Watcher will run CG delegates.");
      } else {
        log.info("Delegate is NG. Watcher will run NG delegates.");
      }
      startInputCheck();
      messageService.writeMessage(WATCHER_STARTED);

      generateEcsDelegateSequenceConfigFile();
      if (upgrade) {
        Message message = messageService.waitForMessage(WATCHER_GO_AHEAD, MINUTES.toMillis(5), true);
        log.info(message != null ? "[New] Got go-ahead. Proceeding"
                                 : "[New] Timed out waiting for go-ahead. Proceeding anyway");
      }

      messageService.removeData(WATCHER_DATA, NEXT_WATCHER);

      log.info(upgrade ? "[New] Watcher upgraded" : "Watcher started");
      log.info("Publish Authority:{}, Publish Target:{}", watcherConfiguration.getPublishAuthority(),
          watcherConfiguration.getPublishTarget());

      String proxyHost = System.getProperty("https.proxyHost");

      if (isNotBlank(proxyHost)) {
        String proxyScheme = System.getProperty("proxyScheme");
        String proxyPort = System.getProperty("https.proxyPort");
        log.info("Using {} proxy {}:{}", proxyScheme, proxyHost, proxyPort);
        String nonProxyHostsString = System.getProperty("http.nonProxyHosts");
        if (isNotBlank(nonProxyHostsString)) {
          String[] suffixes = nonProxyHostsString.split("\\|");
          List<String> nonProxyHosts = Stream.of(suffixes).map(suffix -> suffix.substring(1)).collect(toList());
          log.info("No proxy for hosts with suffix in: {}", nonProxyHosts);
        }
      } else {
        log.info("No proxy settings. Configure in proxy.config if needed");
      }

      checkAccountStatus();
      startWatching();
      startUpgradeCheck();
      startMonitoringFileHandles();

      synchronized (waiter) {
        waiter.wait();
      }

      log.info("Got stop message, shutting down now");
    } catch (InterruptedException e) {
      log.error("Interrupted while running watcher", e);
    }
  }

  private void startMonitoringFileHandles() {
    if (!watcherConfiguration.isFileHandlesMonitoringEnabled()) {
      log.info("File handles monitoring disabled.");
      return;
    }

    if (!isLsofCommandPresent()) {
      log.info("lsof command is not available.");
      return;
    }

    log.info("Scheduling logging of file handles...");
    watchExecutor.scheduleWithFixedDelay(
        new Schedulable("Unexpected exception occurred while logging file handles", this::logFileHandles), 0,
        watcherConfiguration.getFileHandlesMonitoringIntervalInMinutes(), MINUTES);
  }

  private boolean isLsofCommandPresent() {
    try {
      log.info("Checking if lsof command is available...");
      ProcessExecutor processExecutor = new ProcessExecutor();
      processExecutor.command("lsof", "-v");
      int exitCode = processExecutor.execute().getExitValue();

      return exitCode == 0;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException | TimeoutException ex) {
      log.warn("lsof command not present", ex);
      return false;
    }
  }

  private void logFileHandles() {
    try {
      log.debug("Making sure that folder exists...");
      File logsFolder = new File(FILE_HANDLES_LOGS_FOLDER);
      if (!logsFolder.exists()) {
        boolean created = logsFolder.mkdir();
        if (!created) {
          return;
        }
      }

      if (!deleteOldFiles(logsFolder)) {
        return;
      }

      log.debug("Logging all file handles...");
      logAllFileHandles();

      log.debug("Logging watcher file handles...");
      String watcherProcessId =
          Splitter.on("@").split(ManagementFactory.getRuntimeMXBean().getName()).iterator().next();
      logProcessFileHandles(watcherProcessId, true);

      log.debug("Logging delegate file handles...");
      ArrayList<String> monitoredProcesses = new ArrayList<>(runningDelegates);
      for (String pid : monitoredProcesses) {
        logProcessFileHandles(pid, false);
      }
      log.debug("File handles logging finished.");
    } catch (Exception ex) {
      log.error("Unexpected exception occurred while logging file handles.", ex);
    }
  }

  private boolean deleteOldFiles(File logsFolder) {
    long retentionPeriod = MINUTES.toMillis(watcherConfiguration.getFileHandlesLogsRetentionInMinutes());
    long cutoff = System.currentTimeMillis() - retentionPeriod;
    File[] filesToBeDeleted = logsFolder.listFiles((FileFilter) new AgeFileFilter(cutoff));
    if (filesToBeDeleted != null) {
      for (File logFile : filesToBeDeleted) {
        try {
          Files.delete(Paths.get(logFile.getAbsolutePath()));
        } catch (IOException ex) {
          log.warn("File handles log file {} could not be deleted.", logFile.getAbsolutePath(), ex);
          return false;
        }
      }
    }
    return true;
  }

  private void logProcessFileHandles(String pid, boolean isWatcher)
      throws InterruptedException, TimeoutException, IOException {
    log.debug("Logging file handles for pid {}", pid);

    String filePath = FILE_HANDLES_LOGS_FOLDER + File.separator + LocalDateTime.now()
        + (isWatcher ? "-watcher" : "-delegate") + "-pid-" + pid + ".txt";

    try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
      ProcessExecutor processExecutor = new ProcessExecutor();
      processExecutor.command("lsof", "-b", "-w", "-p", pid);
      processExecutor.redirectOutput(fileOutputStream);
      processExecutor.execute();
    }
  }

  private void logAllFileHandles() throws InterruptedException, TimeoutException, IOException {
    log.debug("Logging all file handles");

    String filePath = FILE_HANDLES_LOGS_FOLDER + File.separator + LocalDateTime.now() + "-all.txt";

    try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
      ProcessExecutor processExecutor = new ProcessExecutor();
      processExecutor.command("lsof", "-b", "-w");
      processExecutor.redirectOutput(fileOutputStream);
      processExecutor.execute();
    }
  }

  private void generateEcsDelegateSequenceConfigFile() {
    try {
      if (!"ECS".equals(System.getenv().get("DELEGATE_TYPE"))) {
        return;
      }

      // Only generate this file in case of ECS delegate
      log.info("Generating delegate_sequence_config file");
      FileUtils.touch(new File(DELEGATE_SEQUENCE_CONFIG_FILE));
      String randomToken = UUIDGenerator.generateUuid();
      FileIo.writeWithExclusiveLockAcrossProcesses(
          "[TOKEN]" + randomToken + "[SEQ]", DELEGATE_SEQUENCE_CONFIG_FILE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      log.warn("Failed to create DelegateSequenceConfigFile");
    }
  }

  private void stop() {
    synchronized (waiter) {
      waiter.notify();
    }
  }

  private void startInputCheck() {
    inputExecutor.scheduleWithFixedDelay(
        messageService.getMessageCheckingRunnable(TimeUnit.SECONDS.toMillis(4), null), 0, 1, TimeUnit.SECONDS);
  }

  private void startUpgradeCheck() {
    upgradeExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while checking for upgrades", this::syncCheckForWatcherUpgrade), 10,
        watcherConfiguration.getUpgradeCheckIntervalSeconds(), TimeUnit.SECONDS);
  }

  private void syncCheckForWatcherUpgrade() {
    synchronized (this) {
      if (!working.get()) {
        checkForWatcherUpgrade();
      }
    }
  }

  @SuppressWarnings({"unchecked"})
  private void startWatching() {
    runningDelegates.addAll(
        Optional.ofNullable(messageService.getData(WATCHER_DATA, RUNNING_DELEGATES, List.class)).orElse(emptyList()));

    scheduleLocalHeartbeat();

    inputExecutor.scheduleWithFixedDelay(
        new Schedulable("Error fetching delegate version from manager", this::watchDelegateVersions),
        new Random().nextInt(20), // Random initial delay, so that not all watcher have fixed 20 coinciding window.
        DELEGATE_VERSION_CHECK_FREQ_MINS, MINUTES);
    heartbeatExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while logging-performance", this::logPerformance), 0, 60, TimeUnit.SECONDS);
    watchExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while watching delegate", this::syncWatchDelegate), 0, 10, TimeUnit.SECONDS);
    upgradeExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while cleaning up garbage", this::cleanupOlderVersions), 1, 5, DAYS);
  }

  private void logPerformance() {
    try (AutoLogContext ignore = new AutoLogContext(obtainPerformance(), OVERRIDE_NESTS)) {
      log.info("Current performance");
    }
  }

  private void scheduleLocalHeartbeat() {
    heartbeatExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while heart-beating", this::heartbeat), 0, 10, TimeUnit.SECONDS);
  }

  public Map<String, String> obtainPerformance() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    memoryUsage(builder, "heap-", memoryMXBean.getHeapMemoryUsage());
    memoryUsage(builder, "non-heap-", memoryMXBean.getNonHeapMemoryUsage());
    return builder.build();
  }

  private void heartbeat() {
    if (isDiskFull()) {
      log.error("Skipping local heartbeat because disk is full");
      return;
    }
    try {
      Map<String, Object> heartbeatData = new HashMap<>();
      heartbeatData.put(WATCHER_HEARTBEAT, clock.millis());
      heartbeatData.put(WATCHER_PROCESS, getProcessId());
      heartbeatData.put(WATCHER_VERSION, getVersion());
      messageService.putAllData(WATCHER_DATA, heartbeatData);
    } catch (VersionInfoException e) {
      log.error("Exception while sending local heartbeat ", e);
    } catch (Exception e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE_ERROR)) {
        lastAvailableDiskSpace.set(getDiskFreeSpace());
        log.error("Disk space is full. Free space: {}", lastAvailableDiskSpace.get());
      } else {
        log.error("Error putting all watcher data", e);
        throw e;
      }
    }
  }

  private void syncWatchDelegate() {
    synchronized (this) {
      if (!working.get()) {
        watchDelegate();
      }
    }
  }

  private void watchDelegate() {
    Map<String, Object> delegateData = null;
    try {
      // Cleanup obsolete
      messageService.listDataNames(DELEGATE_DASH)
          .stream()
          .map(dataName -> dataName.substring(DELEGATE_DASH.length()))
          .filter(process -> !runningDelegates.contains(process))
          .forEach(process -> {
            if (!Optional.ofNullable(messageService.getData(DELEGATE_DASH + process, DELEGATE_IS_NEW, Boolean.class))
                     .orElse(false)) {
              log.warn("Data found for untracked delegate process {}. Shutting it down", process);
              shutdownDelegate(process);
            }
          });

      messageService.listChannels(DELEGATE)
          .stream()
          .filter(process -> !runningDelegates.contains(process))
          .forEach(process -> {
            log.warn("Message channel found for untracked delegate process {}. Shutting it down", process);
            shutdownDelegate(process);
          });

      String extraWatcher = messageService.getData(WATCHER_DATA, EXTRA_WATCHER, String.class);
      if (isNotEmpty(extraWatcher)) {
        shutdownWatcher(extraWatcher);
      } else {
        messageService.listChannels(WATCHER)
            .stream()
            .filter(process
                -> !StringUtils.equals(process, getProcessId())
                    && !StringUtils.equals(process, messageService.getData(WATCHER_DATA, NEXT_WATCHER, String.class)))
            .forEach(process -> {
              log.warn(
                  "Message channel found for another watcher process that isn't the next watcher. {} will be shut down",
                  process);
              messageService.putData(WATCHER_DATA, EXTRA_WATCHER, process);
            });
      }

      List<String> expectedVersions = findExpectedDelegateVersions();
      if (isEmpty(expectedVersions)) {
        // Something went wrong with obtaining the list with expected delegates.
        // Postpone this for better times.
        log.error("Unable to fetch expected version, skip watching delegate");
        return;
      }
      Multimap<String, String> runningVersions = LinkedHashMultimap.create();
      List<String> shutdownPendingList = new ArrayList<>();
      List<String> drainingNeededList = emptyList();

      if (isNotEmpty(runningDelegates)) {
        List<String> obsolete = new ArrayList<>();
        List<String> restartNeededList = new ArrayList<>();
        List<String> drainingRestartNeededList = new ArrayList<>();
        List<String> shutdownNeededList = new ArrayList<>();
        drainingNeededList = new ArrayList<>();
        List<String> stopGrpcServerList = new ArrayList<>();
        List<String> startGrpcServerList = new ArrayList<>();

        boolean newDelegateTimedOut = false;
        long now = clock.millis();

        synchronized (runningDelegates) {
          Set<String> notRunning = delegateVersionMatchedAt.keySet()
                                       .stream()
                                       .filter(delegateProcess -> !runningDelegates.contains(delegateProcess))
                                       .collect(toSet());
          notRunning.forEach(delegateVersionMatchedAt::remove);

          for (String delegateProcess : runningDelegates) {
            delegateData = messageService.getAllData(DELEGATE_DASH + delegateProcess);
            if (delegateData == null) {
              continue;
            }
            if (delegateData.isEmpty()) {
              obsolete.add(delegateProcess);
            } else if (delegateData.containsKey(DELEGATE_SELF_DESTRUCT)
                && (Boolean) delegateData.get(DELEGATE_SELF_DESTRUCT)) {
              // we should self-destruct only for shell delegates and not for containerised delegates since pod will
              // restart itself again based on liveliness probe
              if (IsShellDelegate) {
                selfDestruct();
              } else {
                freeze();
              }
            } else if (delegateData.containsKey(DELEGATE_MIGRATE)) {
              migrate((String) delegateData.get(DELEGATE_MIGRATE));
            } else if (delegateData.containsKey(DELEGATE_SWITCH_STORAGE)
                && (Boolean) delegateData.get(DELEGATE_SWITCH_STORAGE)) {
              switchStorage();
            } else {
              if (delegateData.containsKey(DELEGATE_JRE_VERSION) && delegateData.containsKey(MIGRATE_TO_JRE_VERSION)) {
                String delegateJreVersion = (String) delegateData.get(DELEGATE_JRE_VERSION);
                String migrateToJreVersion = (String) delegateData.get(MIGRATE_TO_JRE_VERSION);
                // upgradeJre(delegateJreVersion, migrateToJreVersion);
              }

              if (UNREGISTERED.equals(DelegateAgentCommonVariables.getDelegateId())) {
                String delegateIdFromAgent = (String) delegateData.get(DELEGATE_ID);
                DelegateAgentCommonVariables.setDelegateId(delegateIdFromAgent);
              }

              if (UNREGISTERED.equals(DelegateAgentCommonVariables.getDelegateTokenName())) {
                String delegateTokenName = (String) delegateData.get(DELEGATE_TOKEN_NAME);
                DelegateAgentCommonVariables.setDelegateTokenName(delegateTokenName);
              }

              String delegateVersion = (String) delegateData.get(DELEGATE_VERSION);
              runningVersions.put(delegateVersion, delegateProcess);
              int delegateMinorVersion = getMinorVersion(delegateVersion);
              boolean delegateMinorVersionMismatch =
                  delegateMinorVersion < minMinorVersion.get() || illegalVersions.contains(delegateMinorVersion);
              if (!delegateVersionMatchedAt.containsKey(delegateProcess)
                  || expectedVersions.contains(delegateVersion)) {
                delegateVersionMatchedAt.put(delegateProcess, now);
              }
              boolean versionMatchTimedOut =
                  now - delegateVersionMatchedAt.get(delegateProcess) > DELEGATE_VERSION_MATCH_TIMEOUT;
              long heartbeat = Optional.ofNullable((Long) delegateData.get(DELEGATE_HEARTBEAT)).orElse(0L);
              boolean heartbeatTimedOut = now - heartbeat > DELEGATE_HEARTBEAT_TIMEOUT;
              boolean newDelegate = Optional.ofNullable((Boolean) delegateData.get(DELEGATE_IS_NEW)).orElse(false);
              boolean restartNeeded =
                  Optional.ofNullable((Boolean) delegateData.get(DELEGATE_RESTART_NEEDED)).orElse(false);
              boolean shutdownPending =
                  Optional.ofNullable((Boolean) delegateData.get(DELEGATE_SHUTDOWN_PENDING)).orElse(false);

              long shutdownStarted = 0L;
              try {
                Object shutDownTime = delegateData.get(DELEGATE_SHUTDOWN_STARTED);
                if (shutDownTime != null) {
                  if (!shutDownTime.equals(Integer.valueOf(0))) {
                    shutdownStarted = (Long) shutDownTime;
                  } else {
                    log.warn("ShutDownStarted is 0 in Delegate data for process {}", delegateProcess);
                  }
                }
              } catch (Exception e) {
                log.error("Caught exception while reading {} for Delegate process {} ", DELEGATE_SHUTDOWN_STARTED,
                    delegateProcess, e);
              }

              boolean shutdownTimedOut = now - shutdownStarted > DELEGATE_SHUTDOWN_TIMEOUT;

              if (isEmpty(delegateVersion)) {
                log.error("Unable to read Delegate version for process {}, setting {} as delegate version",
                    delegateProcess, getPrimaryDelegate(expectedVersions));
                delegateVersion = getPrimaryDelegate(expectedVersions);
              }
              if (!expectedVersions.toString().contains(delegateVersion) && !shutdownPending) {
                log.info("Delegate version {} ({}) is not a published version. Future requests will go to primary.",
                    delegateVersion, delegateProcess);
                drainingNeededList.add(delegateProcess);
              }

              if (newDelegate) {
                log.info("New delegate process {} is starting", delegateProcess);
                boolean startupTimedOut = now - heartbeat > DELEGATE_STARTUP_TIMEOUT;
                if (startupTimedOut) {
                  shutdownNeededList.add(delegateProcess);
                }
              } else if (shutdownPending) {
                log.info(
                    "Shutdown is pending for delegate process {} with version {}", delegateProcess, delegateVersion);
                shutdownPendingList.add(delegateProcess);
                if (shutdownTimedOut || heartbeatTimedOut) {
                  shutdownNeededList.add(delegateProcess);
                }
              } else if (heartbeatTimedOut || versionMatchTimedOut || delegateMinorVersionMismatch) {
                log.info("Restarting delegate process {}. Local heartbeat timeout: {}, "
                        + "Version match timeout: {}, Version banned: {} ",
                    delegateProcess, heartbeatTimedOut, versionMatchTimedOut, delegateMinorVersionMismatch);
                restartNeededList.add(delegateProcess);
                minMinorVersion.set(0);
                illegalVersions.clear();
              } else if (restartNeeded) {
                log.info("Draining restart delegate process {} at delegate request", delegateProcess);
                drainingRestartNeededList.add(delegateProcess);
              }
              if (isPrimaryDelegate(delegateVersion, expectedVersions)) {
                startGrpcServerList.add(delegateProcess);
              } else {
                stopGrpcServerList.add(delegateProcess);
              }
            }
          }

          if (isNotEmpty(obsolete)) {
            log.info("Obsolete processes {} no longer tracked", obsolete);
            runningDelegates.removeAll(obsolete);
            messageService.putData(WATCHER_DATA, RUNNING_DELEGATES, runningDelegates);
            obsolete.forEach(this::shutdownDelegate);
          }
        }

        if (isNotEmpty(shutdownNeededList)) {
          log.warn("Delegate processes {} exceeded grace period. Forcing shutdown", shutdownNeededList);
          shutdownNeededList.forEach(this::shutdownDelegate);
        }
        if (isNotEmpty(restartNeededList)) {
          log.warn("Delegate processes {} need restart. Shutting down", restartNeededList);
          restartNeededList.forEach(this::shutdownDelegate);
        }
        if (isNotEmpty(drainingRestartNeededList)) {
          if (multiVersion) {
            log.warn("Delegate processes {} need restart. Will be drained and new process with same version started",
                drainingRestartNeededList);
            drainingRestartNeededList.forEach(this::drainDelegateProcess);
          } else if (drainingRestartNeededList.containsAll(runningDelegates) && working.compareAndSet(false, true)) {
            log.warn(
                "Delegate processes {} need restart. Starting new process and draining old", drainingRestartNeededList);
            startDelegateProcess(getPrimaryDelegate(expectedVersions), getPrimaryDelegate(expectedVersions),
                drainingRestartNeededList, DELEGATE_RESTART_SCRIPT, getProcessId());
          }
        }

        if (isNotEmpty(startGrpcServerList)) {
          startGrpcServerList.forEach(
              delegateProcess -> messageService.writeMessageToChannel(DELEGATE, delegateProcess, DELEGATE_START_GRPC));

          // We don't want to stop older grpc service unless one delegate agent with primary version is running.
          if (isNotEmpty(stopGrpcServerList)) {
            stopGrpcServerList.forEach(
                delegateProcess -> messageService.writeMessageToChannel(DELEGATE, delegateProcess, DELEGATE_STOP_GRPC));
          }
        } else if (isNotEmpty(stopGrpcServerList)) {
          log.warn("Found no running delegate to start grpc server. Skipping stopping of grpc server on {}",
              stopGrpcServerList);
        }
      }

      if (!isDiskFull()) {
        log.info("Watching delegate processes: {}",
            runningVersions.keySet()
                .stream()
                .map(version -> version + " (" + join(", ", runningVersions.get(version)) + ")")
                .collect(toList()));

        // Make sure at least one of each expected version is acquiring
        for (String version : Lists.reverse(expectedVersions)) {
          if (!shutdownPendingList.containsAll(runningVersions.get(version)) || !working.compareAndSet(false, true)) {
            continue;
          }

          try {
            log.info("New delegate process for version {} will be started", version);
            downloadRunScripts(version, version, false);
            downloadDelegateJar(version);
            startDelegateProcess(version, version, drainingNeededList, "DelegateStartScriptVersioned", getProcessId());
            break;
          } catch (IOException ioe) {
            if (ioe.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE_ERROR)) {
              lastAvailableDiskSpace.set(getDiskFreeSpace());
              log.error("Disk space is full. Free space: {}", lastAvailableDiskSpace.get());
            }
            log.error("Error downloading delegate version {}", version, ioe);
            working.set(false);
          } catch (Exception e) {
            log.error("Error downloading or starting delegate version {}", version, e);
            working.set(false);
          }
        }

        // Make sure no more than one of each running version is acquiring
        for (String version : runningVersions.keySet()) {
          List<String> acquiring = runningVersions.get(version)
                                       .stream()
                                       .filter(proc -> !shutdownPendingList.contains(proc))
                                       .collect(toList());
          for (int i = acquiring.size() - 1; i > 0; i--) {
            drainDelegateProcess(acquiring.get(i));
          }
        }
      }
    } catch (Exception e) {
      final String delegateDataAsString = isNotEmpty(delegateData) ? delegateData.toString() : null;
      log.error(
          "Error processing delegate stream, message: {}, delegateData: {}", e.getMessage(), delegateDataAsString, e);
    }
  }

  @VisibleForTesting
  public long getDiskFreeSpace() {
    return new File(".").getFreeSpace();
  }

  @VisibleForTesting
  public boolean isDiskFull() {
    if (lastAvailableDiskSpace.get() == DISK_SPACE_IS_OK) {
      return false;
    }

    long freeSpace = getDiskFreeSpace();
    if (lastAvailableDiskSpace.get() >= freeSpace) {
      return true;
    }

    log.info("Free disk space increased from {} to {}", lastAvailableDiskSpace.get(), freeSpace);
    lastAvailableDiskSpace.set(DISK_SPACE_IS_OK);
    return false;
  }

  @VisibleForTesting
  public void switchStorage() {
    log.info("Switching Storage");
    boolean downloadSuccessful = downloadRunScriptsBeforeRestartingDelegateAndWatcher();
    if (downloadSuccessful) {
      restartDelegate();
      restartWatcher();
    } else {
      log.warn("Download of run scripts was not successful. Skipping restart for switching storage.");
    }
  }

  @VisibleForTesting
  public boolean downloadRunScriptsBeforeRestartingDelegateAndWatcher() {
    if (isDiskFull()) {
      return false;
    }

    try {
      List<String> expectedDelegateVersions = findExpectedDelegateVersions();
      if (expectedDelegateVersions == null) {
        return false;
      }

      for (String expectedVersion : expectedDelegateVersions) {
        downloadRunScripts(expectedVersion, expectedVersion, true);
      }
    } catch (IOException ioe) {
      if (ioe.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE_ERROR)) {
        lastAvailableDiskSpace.set(getDiskFreeSpace());
        log.error("Download run script, Disk space is full. Free space: {}", lastAvailableDiskSpace.get());
      }
      log.error("Error download run script", ioe);
      return false;
    } catch (Exception e) {
      log.error("Error downloading or starting run script", e);
      return false;
    }

    return true;
  }

  private void restartDelegate() {
    log.info("Received request to restart Delegate");
    runningDelegates.forEach(this::drainDelegateProcess);
  }

  private void upgradeJre(String delegateJreVersion, String migrateToJreVersion) {
    restartDelegateToUpgradeJre(delegateJreVersion, migrateToJreVersion);
  }

  /**
   * Restart delegate only when there is a mismatch between delegate's JRE and migrate to JRE version. A timeout of
   * 10mins is kept as a buffer to avoid repetitive restarting of delegates.
   * @param delegateJreVersion JRE version of the delegate
   * @param migrateToJreVersion JRE version to migrate to
   */
  @VisibleForTesting
  public void restartDelegateToUpgradeJre(String delegateJreVersion, String migrateToJreVersion) {
    if (!delegateJreVersion.equals(migrateToJreVersion)
        && clock.millis() - delegateRestartedToUpgradeJreAt > DELEGATE_RESTART_TO_UPGRADE_JRE_TIMEOUT) {
      log.info("Delegate JRE: {} MigrateTo JRE: {} ", delegateJreVersion, migrateToJreVersion);
      boolean downloadSuccessful = downloadRunScriptsBeforeRestartingDelegateAndWatcher();
      if (downloadSuccessful) {
        delegateRestartedToUpgradeJreAt = clock.millis();
        restartDelegate();
      } else {
        log.warn("Download of run scripts was not successful. Skipping restart for delegate JRE upgrade.");
      }
    }
  }

  private void checkAccountStatus() {
    try {
      RestResponse<String> restResponse = callInterruptible21(timeLimiter, ofSeconds(5),
          () -> SafeHttpCall.execute(managerClient.getAccountStatus(watcherConfiguration.getAccountId())));

      if (restResponse == null) {
        return;
      }

      if ("DELETED".equals(restResponse.getResource())) {
        // we should self-destruct only for shell delegates and not for containerised delegates since pod will restart
        // itself again based on liveliness probe
        if (IsShellDelegate) {
          selfDestruct();
        } else {
          freeze();
        }
      }
    } catch (Exception e) {
      // Ignore
    }
  }

  private boolean isPrimaryDelegate(String delegateVersion, List<String> expectedVersions) {
    if (isEmpty(delegateVersion)) {
      if (expectedVersions.size() == 1) {
        // If we do not have valid delegateVersion and there is only one expected version,
        // consider that to be primary.
        log.info("Delegate version is not valid, assuming {} as primary", expectedVersions.get(0));
        return true;
      }
      log.error("Invalid delegate version, expected version {}", expectedVersions.toString());
      return false;
    }
    return delegateVersion.equals(getPrimaryDelegate(expectedVersions));
  }

  private String getPrimaryDelegate(List<String> expectedVersions) {
    if (CollectionUtils.isEmpty(expectedVersions)) {
      return null;
    }
    // Last element in expected version list is primary delegate version
    return expectedVersions.get(expectedVersions.size() - 1);
  }

  @VisibleForTesting
  public List<String> findExpectedDelegateVersions() {
    List<String> delegateVersion = cachedDelegateVersion.get();
    if (isEmpty(delegateVersion)) {
      log.info("No cached delegate version found, trying to fetch latest delegate version");
      watchDelegateVersions();
      delegateVersion = cachedDelegateVersion.get();
    }
    return delegateVersion;
  }

  private void watchDelegateVersions() {
    try {
      if (multiVersion) {
        RestResponse<DelegateConfiguration> restResponse = callInterruptible21(timeLimiter, ofSeconds(30),
            () -> SafeHttpCall.execute(managerClient.getDelegateConfiguration(watcherConfiguration.getAccountId())));

        if (restResponse == null) {
          return;
        }

        DelegateConfiguration config = restResponse.getResource();

        if (config != null && config.getAction() == SELF_DESTRUCT) {
          // we should self-destruct only for shell delegates and not for containerised delegates since pod will restart
          // itself again based on liveliness probe
          if (IsShellDelegate) {
            selfDestruct();
          } else {
            freeze();
          }
        }

        if (config != null) {
          cachedDelegateVersion.set(config.getDelegateVersions());
        }
      } else {
        String delegateMetadata =
            Http.getResponseStringFromUrl(watcherConfiguration.getDelegateCheckLocation(), 10, 10);
        cachedDelegateVersion.set(singletonList(substringBefore(delegateMetadata, " ").trim()));
      }
    } catch (UncheckedTimeoutException e) {
      log.warn("Timed out fetching delegate version information", e);
    } catch (Exception e) {
      log.warn("Unable to fetch delegate version information", e);
    }
  }

  private int getMinorVersion(String delegateVersion) {
    if (isNotBlank(delegateVersion)) {
      try {
        return Integer.parseInt(delegateVersion.substring(delegateVersion.lastIndexOf('.') + 1));
      } catch (NumberFormatException e) {
        // Leave it null
      }
    }
    return 0;
  }

  private String getDelegateVersionWithPatch(String delegateVersion) {
    if (isNotBlank(delegateVersion)) {
      return delegateVersion.substring(delegateVersion.lastIndexOf('.') + 1);
    }
    return EMPTY;
  }

  private void downloadRunScripts(String directory, String version, boolean forceDownload) throws Exception {
    if (!forceDownload && new File(directory + File.separator + DELEGATE_SCRIPT).exists()) {
      return;
    }

    // Get patched version
    final String patchVersion = substringAfter(version, "-");
    final String updatedVersion = version.contains("-") ? substringBefore(version, "-") : version;
    RestResponse<DelegateScripts> restResponse;
    if (!delegateNg) {
      log.info(format("Calling getDelegateScripts with version %s and patch %s", updatedVersion, patchVersion));
      restResponse = callInterruptible21(timeLimiter, ofMinutes(1),
          ()
              -> SafeHttpCall.execute(managerClient.getDelegateScripts(
                  watcherConfiguration.getAccountId(), updatedVersion, patchVersion, DELEGATE_NAME)));
    } else {
      log.info(format("Calling getDelegateScriptsNg with version %s and patch %s", updatedVersion, patchVersion));
      restResponse = callInterruptible21(timeLimiter, ofMinutes(1),
          ()
              -> SafeHttpCall.execute(managerClient.getDelegateScriptsNg(
                  watcherConfiguration.getAccountId(), updatedVersion, patchVersion, DELEGATE_TYPE)));
    }

    if (restResponse == null) {
      log.warn("Received null response from manager.");
      return;
    }

    DelegateScripts delegateScripts = restResponse.getResource();

    Path versionDir = Paths.get(directory);
    if (!versionDir.toFile().exists()) {
      Files.createDirectory(versionDir);
    }

    for (String fileName : asList("start.sh", "stop.sh", DELEGATE_SCRIPT, "setup-proxy.sh")) {
      String filePath = fileName;
      if (DELEGATE_SCRIPT.equals(fileName)) {
        filePath = directory + File.separator + fileName;
      }
      File scriptFile = new File(filePath);
      String script = delegateScripts.getScriptByName(fileName);

      if (isNotEmpty(script)) {
        Files.deleteIfExists(Paths.get(filePath));
        try (BufferedWriter writer = Files.newBufferedWriter(scriptFile.toPath())) {
          writer.write(script, 0, script.length());
          writer.flush();
        }
        log.info("Done replacing file [{}]. Set User and Group permission", scriptFile);
        Files.setPosixFilePermissions(scriptFile.toPath(),
            newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
        log.info("Done setting file permissions");
      } else {
        log.error("Script for file [{}] was not replaced", scriptFile);
      }
    }
  }

  private void downloadDelegateJar(String version) throws Exception {
    String minorVersion = getDelegateVersionWithPatch(version);

    File finalDestination = new File(version + "/delegate.jar");
    if (finalDestination.exists()) {
      log.info("Verified delegate jar version {} already exists. Skipping download...", version);
      return;
    }

    String downloadUrl;
    if (multiVersion) {
      // Fetch delegate download location from manager in case of saas.
      RestResponse<String> restResponse = callInterruptible21(timeLimiter, ofSeconds(30),
          ()
              -> SafeHttpCall.execute(
                  managerClient.getDelegateDownloadUrl(minorVersion, watcherConfiguration.getAccountId())));
      if (restResponse == null) {
        return;
      }
      downloadUrl = restResponse.getResource();
    } else {
      // In case of on-prem, construct download url.
      final String storageUrl = System.getenv().get("DELEGATE_STORAGE_URL");
      final String delegateMetadata =
          Http.getResponseStringFromUrl(watcherConfiguration.getDelegateCheckLocation(), 10, 10);
      downloadUrl = storageUrl + "/" + substringAfter(delegateMetadata, " ").trim();
    }

    log.info("Downloading delegate jar version {} and download url {}", version, substringBefore(downloadUrl, "?"));
    File downloadFolder = new File(version);
    if (!downloadFolder.exists()) {
      downloadFolder.mkdir();
    }

    File downloadDestination = File.createTempFile("delegate", ".jar", downloadFolder);
    downloadDestination.deleteOnExit();
    Stopwatch timer = Stopwatch.createStarted();

    if (downloadUrl.startsWith("file://")) {
      String sourceFile = downloadUrl.substring(7);
      FileUtils.copyFile(new File(sourceFile), downloadDestination);
      log.info("Downloaded delegate jar version {} from file share to the temporary location", version);

      FileUtils.moveFile(downloadDestination, finalDestination);
      log.info("Moved delegate jar version {} to the final location", version);
    } else {
      try (InputStream stream = Http.getResponseStreamFromUrl(downloadUrl, 600, 600)) {
        FileUtils.copyInputStreamToFile(stream, downloadDestination);
        log.info("Downloaded delegate jar version {} to the temporary location", version);

        try (JarFile delegateJar = new JarFile(downloadDestination)) {
          if (SignVerifier.meticulouslyVerify(delegateJar)) {
            FileUtils.moveFile(downloadDestination, finalDestination);
            log.info("Moved delegate jar version {} to the final location", version);
          } else {
            log.warn("Downloaded delegate jar version {} is corrupted. Removing invalid file.", version);
            FileUtils.forceDelete(downloadDestination);
          }
        } catch (Exception ex) {
          log.warn("Unexpected error occurred during jar file verification with message: {}. File will be deleted.",
              ex.getMessage());
        } finally {
          if (downloadDestination.exists()) {
            FileUtils.forceDelete(downloadDestination);
          }
        }
      }
    }

    log.info("Finished downloading delegate jar version {} in {} seconds", version, timer.elapsed(TimeUnit.SECONDS));
  }

  private void drainDelegateProcess(String delegateProcess) {
    log.info("Sending delegate process {} stop-acquiring message to drain", delegateProcess);
    messageService.writeMessageToChannel(DELEGATE, delegateProcess, DELEGATE_STOP_ACQUIRING);
  }

  private void startDelegateProcess(String version, String versionFolder, List<String> oldDelegateProcesses,
      String scriptName, String watcherProcess) {
    if (!new File(versionFolder + File.separator + DELEGATE_SCRIPT).exists()) {
      working.set(false);
      return;
    }

    executorService.submit(() -> {
      StartedProcess newDelegate = null;
      try {
        newDelegate =
            new ProcessExecutor()
                .command("nohup", versionFolder + File.separator + DELEGATE_SCRIPT, watcherProcess, versionFolder)
                .redirectError(Slf4jStream.of(scriptName).asError())
                .setMessageLogger((log, format, arguments) -> log.info(format, arguments))
                .start();

        boolean success = false;
        String newDelegateProcess = null;

        if (newDelegate.getProcess().isAlive()) {
          Message message =
              messageService.waitForMessage(NEW_DELEGATE, MINUTES.toMillis(version == null ? 15 : 4), false);
          if (message != null) {
            newDelegateProcess = message.getParams().get(0);
            log.info("Got process ID from new delegate: {}", newDelegateProcess);
            Map<String, Object> delegateData = new HashMap<>();
            delegateData.put(DELEGATE_IS_NEW, true);
            if (version != null) {
              delegateData.put(DELEGATE_VERSION, version);
            }
            delegateData.put(DELEGATE_HEARTBEAT, clock.millis());
            messageService.putAllData(DELEGATE_DASH + newDelegateProcess, delegateData);
            synchronized (runningDelegates) {
              runningDelegates.add(newDelegateProcess);
              messageService.putData(WATCHER_DATA, RUNNING_DELEGATES, runningDelegates);
            }
            message = messageService.readMessageFromChannel(DELEGATE, newDelegateProcess, MINUTES.toMillis(2));
            if (message != null && message.getMessage().equals(DELEGATE_STARTED)) {
              log.info("Retrieved delegate-started message from new delegate {}", newDelegateProcess);
              log.info("Sending new delegate process {} go-ahead message", newDelegateProcess);
              messageService.writeMessageToChannel(DELEGATE, newDelegateProcess, DELEGATE_GO_AHEAD);

              log.info("Waiting for delegate process {} to be ready", newDelegateProcess);
              message = messageService.readMessageFromChannel(DELEGATE, newDelegateProcess, MINUTES.toMillis(5));
              if (message != null && message.getMessage().equals(DELEGATE_READY)) {
                oldDelegateProcesses.forEach(oldDelegateProcess -> {
                  log.info(
                      "New delegate process ready to acquire task, sending old delegate process {} stop-acquiring message",
                      oldDelegateProcess);
                  messageService.writeMessageToChannel(DELEGATE, oldDelegateProcess, DELEGATE_STOP_ACQUIRING);
                });
              } else {
                log.info("Timed out waiting delegate to come up, proceeding anyway {}", oldDelegateProcesses);
                oldDelegateProcesses.forEach(oldDelegateProcess -> {
                  log.info("Sending old delegate process {} stop-acquiring message", oldDelegateProcess);
                  messageService.writeMessageToChannel(DELEGATE, oldDelegateProcess, DELEGATE_STOP_ACQUIRING);
                });
              }
              success = true;
              log.info("Adding new delegate process {} to process map", newDelegateProcess);
              delegateProcessMap.put(newDelegateProcess, newDelegate.getProcess());
            }
          }
        }
        if (!success) {
          log.error("Failed to start new delegate");
          log.error("Watcher messages:");
          messageService.logAllMessages(WATCHER, watcherProcess);
          messageService.clearChannel(WATCHER, watcherProcess);
          if (isNotBlank(newDelegateProcess)) {
            log.error("Delegate messages:");
            messageService.logAllMessages(DELEGATE, newDelegateProcess);
            messageService.clearChannel(DELEGATE, newDelegateProcess);
          }
          newDelegate.getProcess().destroy();
          newDelegate.getProcess().waitFor(30, TimeUnit.SECONDS);
          oldDelegateProcesses.forEach(oldDelegateProcess -> {
            log.info("Sending old delegate process {} resume message", oldDelegateProcess);
            messageService.writeMessageToChannel(DELEGATE, oldDelegateProcess, DELEGATE_RESUME);
          });
        }
      } catch (Exception e) {
        log.error("Exception while upgrading", e);
        oldDelegateProcesses.forEach(oldDelegateProcess -> {
          log.info("Sending old delegate process {} resume message", oldDelegateProcess);
          messageService.writeMessageToChannel(DELEGATE, oldDelegateProcess, DELEGATE_RESUME);
        });
        if (newDelegate != null) {
          try {
            log.warn("Killing new delegate");
            newDelegate.getProcess().destroy();
            newDelegate.getProcess().waitFor(30, TimeUnit.SECONDS);
          } catch (Exception ex) {
            log.warn("Caught exception while waiting on new delegate process to shutdown", ex);
          }
          try {
            if (newDelegate.getProcess().isAlive()) {
              log.warn("Killing new delegate forcibly");
              newDelegate.getProcess().destroyForcibly();
              if (newDelegate.getProcess() != null) {
                newDelegate.getProcess().waitFor(30, TimeUnit.SECONDS);
              }
            }
          } catch (Exception ex) {
            log.error("ALERT: Couldn't kill forcibly", ex);
          }
        }
      } finally {
        working.set(false);
      }
    });
  }

  private void shutdownDelegate(String delegateProcess) {
    boolean delegateRunning = true;
    try {
      delegateRunning = ProcessControl.isProcessRunning(delegateProcess);
    } catch (Exception ex) {
      log.error("Caught exception while getting process status ", ex);
    }
    if (!delegateRunning) {
      log.info("Delegate with ID:{} not running, closing message channel", delegateProcess);
      delegateProcessMap.remove(delegateProcess);
      messageService.closeData(DELEGATE_DASH + delegateProcess);
      messageService.closeChannel(DELEGATE, delegateProcess);
      synchronized (runningDelegates) {
        runningDelegates.remove(delegateProcess);
        messageService.putData(WATCHER_DATA, RUNNING_DELEGATES, runningDelegates);
      }
      return;
    }

    executorService.submit(() -> {
      messageService.writeMessageToChannel(DELEGATE, delegateProcess, DELEGATE_STOP_ACQUIRING);
      try {
        sleep(ofSeconds(5));
        log.info("Send kill -3 to delegateProcess {}", delegateProcess);
        final String killCmd = "kill -3 " + delegateProcess;
        new ProcessExecutor().command("/bin/bash", "-c", killCmd).start();
        sleep(ofSeconds(15));
        ProcessControl.ensureKilled(delegateProcess, Duration.ofSeconds(120));
      } catch (Exception e) {
        log.error("Error killing delegate {}", delegateProcess, e);
        Process delegate = delegateProcessMap.get(delegateProcess);
        if (delegate != null) {
          delegate.destroyForcibly();
          log.error("Delegate process terminated forcefully: {}", !delegate.isAlive());
        }
      } finally {
        delegateProcessMap.remove(delegateProcess);
      }
      messageService.closeData(DELEGATE_DASH + delegateProcess);
      messageService.closeChannel(DELEGATE, delegateProcess);
      synchronized (runningDelegates) {
        runningDelegates.remove(delegateProcess);
        messageService.putData(WATCHER_DATA, RUNNING_DELEGATES, runningDelegates);
      }
    });
  }

  private void shutdownWatcher(String watcherProcess) {
    if (!StringUtils.equals(watcherProcess, getProcessId())) {
      log.warn("Shutting down extra watcher {}", watcherProcess);
      executorService.submit(() -> {
        try {
          ProcessControl.ensureKilled(watcherProcess, Duration.ofSeconds(120));
        } catch (Exception e) {
          log.error("Error killing watcher {}", watcherProcess, e);
        }
        messageService.closeChannel(WATCHER, watcherProcess);
        messageService.removeData(WATCHER_DATA, EXTRA_WATCHER);
      });
    }
  }

  @VisibleForTesting
  void checkForWatcherUpgrade() {
    log.debug("Checking for watcher upgrade");
    try {
      if (!watcherConfiguration.isDoUpgrade()) {
        log.info("Auto upgrade is disabled in watcher configuration");
        log.info("Watcher stays on version: [{}]", getVersion());
        return;
      }
    } catch (VersionInfoException e) {
      log.error("Exception while reading watcher configuration ", e);
      return;
    }
    try {
      // TODO - if multiVersion use manager endpoint
      boolean upgrade = false;
      String latestWatcherVersion = "";
      if (!watcherConfiguration.getDelegateCheckLocation().startsWith("file://")) {
        latestWatcherVersion = substringBefore(fetchLatestWatcherVersion(), "-").trim();
        if (Pattern.matches("\\d\\.\\d\\.\\d{5,7}(-\\d{3})?", latestWatcherVersion)) {
          upgrade = !StringUtils.equals(getVersion(), latestWatcherVersion);
        }
      }
      if (upgrade) {
        log.info("[Old] Upgrading watcher from version {} to version {}", getVersion(), latestWatcherVersion);
        working.set(true);
        final boolean isDownloaded = downloadRunScriptsBeforeRestartingDelegateAndWatcher();
        if (isDownloaded) {
          upgradeWatcher(getVersion(), latestWatcherVersion);
        } else {
          log.error("Failed to download run scripts before upgrading watcher to {}", latestWatcherVersion);
        }
      } else {
        log.debug("Watcher up to date");
      }
    } catch (Exception e) {
      log.error("Exception while checking for upgrade", e);
      logConfigWatcherYml();
    } finally {
      working.set(false);
    }
  }

  @VisibleForTesting
  String fetchLatestWatcherVersion() throws IOException {
    if (multiVersion) {
      // Fetch watcher verion from manager only for Saas delegates. We can enable it for onprem once ring is enabled.
      try {
        RestResponse<String> restResponse = callInterruptible21(timeLimiter, ofMinutes(1),
            () -> SafeHttpCall.execute(managerClient.getWatcherVersion(watcherConfiguration.getAccountId())));
        if (restResponse != null) {
          return restResponse.getResource();
        }
      } catch (Exception e) {
        log.warn("Failed to fetch watcher version from Manager ", e);
      }
    }
    final String watcherMetadata =
        Http.getResponseStringFromUrl(watcherConfiguration.getUpgradeCheckLocation(), 10, 10);
    return substringBefore(watcherMetadata, " ").trim();
  }

  private static void logConfigWatcherYml() {
    try {
      File configWatcher = new File("config-watcher.yml");
      if (configWatcher.exists()) {
        LineIterator reader = FileUtils.lineIterator(configWatcher);
        while (reader.hasNext()) {
          String line = reader.nextLine();
          log.warn("   " + (StringUtils.containsIgnoreCase(line, "secret") ? "<redacted>" : line));
        }
      }
    } catch (IOException ex) {
      log.error("Couldn't read config-watcher.yml", ex);
    }
  }

  @VisibleForTesting
  void upgradeWatcher(String version, String newVersion) {
    StartedProcess process = null;
    try {
      log.info("[Old] Starting new watcher");
      process = new ProcessExecutor()
                    .command("nohup", "./start.sh", "upgrade", WatcherApplication.getProcessId())
                    .redirectError(Slf4jStream.of("UpgradeScript").asError())
                    .setMessageLogger((log, format, arguments) -> log.info(format, arguments))
                    .start();

      boolean success = false;

      if (process.getProcess().isAlive()) {
        Message message = messageService.waitForMessage(NEW_WATCHER, MINUTES.toMillis(3), true);
        if (message != null) {
          String newWatcherProcess = message.getParams().get(0);
          log.info("[Old] Got process ID from new watcher: " + newWatcherProcess);
          messageService.putData(WATCHER_DATA, NEXT_WATCHER, newWatcherProcess);
          message = messageService.readMessageFromChannel(WATCHER, newWatcherProcess, MINUTES.toMillis(2));
          if (message != null && message.getMessage().equals(WATCHER_STARTED)) {
            log.info(
                "[Old] Retrieved watcher-started message from new watcher {}. Sending go-ahead", newWatcherProcess);
            messageService.writeMessageToChannel(WATCHER, newWatcherProcess, WATCHER_GO_AHEAD);
            log.info("[Old] Watcher upgraded. Cleaning up watcher dirs");
            removeWatcherVersionFromCapsule(version, newVersion);
            cleanupOldWatcherVersionFromBackup(version, newVersion);
            log.info("[Old] Stopping");
            success = true;
            stop();
          }
        }
      }
      if (!success) {
        log.error("[Old] Failed to upgrade watcher");
        process.getProcess().destroy();
        process.getProcess().waitFor();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException e) {
      log.error("[Old] Exception while upgrading", e);
      if (process != null) {
        try {
          process.getProcess().destroy();
          process.getProcess().waitFor();
        } catch (Exception ex) {
          // ignore
        }
        try {
          if (process.getProcess().isAlive()) {
            process.getProcess().destroyForcibly();
            if (process.getProcess() != null) {
              process.getProcess().waitFor();
            }
          }
        } catch (Exception ex) {
          log.error("[Old] ALERT: Couldn't kill forcibly", ex);
        }
      }
    } finally {
      working.set(false);
    }
  }

  private void cleanupOlderVersions() {
    synchronized (this) {
      log.info("Removing older delegate version if any");
      Set<String> allVersions = new HashSet<>(findExpectedDelegateVersions());
      allVersions.addAll(runningDelegates);
      removeDelegateVersionsFromCapsule(allVersions);
      cleanupOldDelegateVersions(allVersions);
    }
  }

  private void cleanupOldWatcherVersionFromBackup(String version, String newVersion) {
    try {
      if (isNotEmpty(System.getProperty(USER_DIR))) {
        cleanup(new File(System.getProperty(USER_DIR)), newHashSet(version, newVersion), "watcherBackup.");
      }
    } catch (Exception ex) {
      log.error("Failed to clean watcher version from Backup", ex);
    }
  }

  private void removeWatcherVersionFromCapsule(String version, String newVersion) {
    try {
      if (isNotEmpty(System.getProperty("capsule.dir"))) {
        cleanup(
            new File(System.getProperty("capsule.dir")).getParentFile(), newHashSet(version, newVersion), "watcher-");
      }
    } catch (Exception ex) {
      log.error("Failed to clean watcher version from Capsule", ex);
    }
  }

  private void cleanupOldDelegateVersions(Set<String> keepVersions) {
    try {
      if (isNotEmpty(System.getProperty(USER_DIR))) {
        cleanupVersionFolders(new File(System.getProperty(USER_DIR)), keepVersions);
        cleanup(new File(System.getProperty(USER_DIR)), keepVersions, "backup.");
      }
    } catch (Exception ex) {
      log.error("Failed to clean delegate version from Backup", ex);
    }
  }

  private void removeDelegateVersionsFromCapsule(Set<String> keepVersions) {
    try {
      if (isNotEmpty(System.getProperty("capsule.dir"))) {
        cleanup(new File(System.getProperty("capsule.dir")).getParentFile(), keepVersions, "delegate-");
      }
    } catch (Exception ex) {
      log.error("Failed to clean delegate version from Capsule", ex);
    }
  }

  private void cleanup(File dir, Set<String> keepVersions, String pattern) {
    FileUtils.listFilesAndDirs(dir, falseFileFilter(), prefixFileFilter(pattern))
        .stream()
        .filter(file -> !dir.equals(file))
        .filter(file -> keepVersions.stream().noneMatch(version -> file.getName().contains(version)))
        .forEach(file -> {
          log.info("Deleting directory matching [^{}.*] = {}", pattern, file.getAbsolutePath());
          FileUtils.deleteQuietly(file);
        });
  }

  private void cleanupVersionFolders(File dir, Set<String> keepVersions) {
    FileUtils.listFilesAndDirs(dir, falseFileFilter(), trueFileFilter())
        .stream()
        .filter(file -> !dir.equals(file))
        .filter(file -> VERSION_PATTERN.matcher(file.getName()).matches())
        .filter(file -> keepVersions.stream().noneMatch(version -> file.getName().equals(version)))
        .forEach(file -> {
          log.info("Deleting version folder matching [{}] = {}", VERSION_PATTERN.pattern(), file.getAbsolutePath());
          FileUtils.deleteQuietly(file);
        });
  }

  @VisibleForTesting
  String getVersion() {
    if (isEmpty(WatcherVersion)) {
      WatcherVersion = (new VersionInfoManager()).getVersionInfo().getVersion();
    }
    return WatcherVersion;
  }

  private void migrate(String newUrl) {
    try {
      updateConfigFilesWithNewUrl(newUrl);
      log.info("Delegate processes {} will be restarted to complete migration.", runningDelegates);
      restartDelegate();
      restartWatcher();
    } catch (Exception e) {
      log.error("Error updating config.", e);
    }
  }

  public boolean updateConfigFileContentsWithNewUrls(
      List<String> originalLines, List<String> outLines, String newUrl, String fileName) {
    boolean changed = false;

    String managerUrlKey = "managerUrl: ";
    String verificationServiceUrlKey = "verificationServiceUrl: ";
    String managerAuthorityKey = "managerAuthority: ";
    String managerTargetKey = "managerTarget: ";
    String publishAuthorityKey = "publishAuthority: ";
    String publishTargetKey = "publishTarget: ";

    String formattedManagerUrl = newUrl.replace("/api/", "");
    formattedManagerUrl = formattedManagerUrl.replace("/api", "");

    String newTargetUrl = DelegateGrpcConfigExtractor.extractTarget(formattedManagerUrl);
    String managerAuthority =
        DelegateGrpcConfigExtractor.extractAndPrepareAuthority(formattedManagerUrl, "manager", false);
    String publishAuthority =
        DelegateGrpcConfigExtractor.extractAndPrepareAuthority(formattedManagerUrl, "events", false);

    boolean foundPublishAuthority = false;
    boolean foundPublishTarget = false;
    boolean foundManagerAuthority = false;
    boolean foundManagerTarget = false;

    for (String line : originalLines) {
      if (startsWith(line, managerUrlKey)) {
        changed = processConfigLine(outLines, line, managerUrlKey, newUrl) || changed;
      } else if (startsWith(line, verificationServiceUrlKey)) {
        String newUrlVerification = replace(newUrl, "api", "verification");
        changed = processConfigLine(outLines, line, verificationServiceUrlKey, newUrlVerification) || changed;
      } else if (startsWith(line, managerTargetKey)) {
        foundManagerTarget = true;
        changed = processConfigLine(outLines, line, managerTargetKey, newTargetUrl) || changed;
      } else if (startsWith(line, publishTargetKey)) {
        foundPublishTarget = true;
        changed = processConfigLine(outLines, line, publishTargetKey, newTargetUrl) || changed;
      } else if (startsWith(line, managerAuthorityKey)) {
        foundManagerAuthority = true;
        changed = processConfigLine(outLines, line, managerAuthorityKey, managerAuthority) || changed;
      } else if (startsWith(line, publishAuthorityKey)) {
        foundPublishAuthority = true;
        changed = processConfigLine(outLines, line, publishAuthorityKey, publishAuthority) || changed;
      } else {
        outLines.add(line);
      }
    }
    if (!foundManagerAuthority && fileName.contains("config-delegate")) {
      outLines.add(managerAuthorityKey + managerAuthority);
    }
    if (!foundManagerTarget && fileName.contains("config-delegate")) {
      outLines.add(managerTargetKey + newTargetUrl);
    }
    if (!foundPublishAuthority && fileName.contains("config-watcher")) {
      outLines.add(publishAuthorityKey + publishAuthority);
    }
    if (!foundPublishTarget && fileName.contains("config-watcher")) {
      outLines.add(publishTargetKey + newTargetUrl);
    }
    return changed;
  }

  private void updateConfigFilesWithNewUrl(String newUrl) throws IOException {
    List<String> configFileNames = ImmutableList.of("config-delegate.yml", "config-watcher.yml");
    for (String configName : configFileNames) {
      File config = new File(configName);

      if (config.exists()) {
        List<String> originalLines = FileUtils.readLines(config, Charsets.UTF_8);
        List<String> outputLines = new ArrayList<>();
        boolean changed = updateConfigFileContentsWithNewUrls(originalLines, outputLines, newUrl, configName);

        if (changed) {
          FileUtils.forceDelete(config);
          FileUtils.touch(config);
          FileUtils.writeLines(config, outputLines);
          Files.setPosixFilePermissions(config.toPath(),
              Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                  PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
        }
      }
    }
  }

  private boolean processConfigLine(List<String> outLines, String line, String configUrlKey, String newUrl) {
    String currentVal = substringAfter(line, configUrlKey);
    if (!StringUtils.equals(currentVal, newUrl)) {
      outLines.add(configUrlKey + newUrl);
      return true;
    } else {
      outLines.add(line);
      return false;
    }
  }

  private void restartWatcher() {
    working.set(true);
    upgradeWatcher(getVersion(), getVersion());
  }

  private void performRecencyCheck() {
    log.info("Performing recency check !!");
    final String storageUrl = System.getenv().get("WATCHER_STORAGE_URL");
    final String checkLocation = System.getenv().get("WATCHER_CHECK_LOCATION");
    if ((isNotEmpty(storageUrl) && storageUrl.contains("storage"))
        || (isNotEmpty(checkLocation) && checkLocation.contains("watcherprod.txt"))) {
      log.warn("Delegate is running with older yaml, please update the delegate.yaml");
    }
  }

  private void selfDestruct() {
    log.info("Self destructing now...");

    working.set(true);
    messageService.shutdown();
    runningDelegates.forEach(delegateProcess -> {
      try {
        ProcessControl.ensureKilled(delegateProcess, Duration.ofSeconds(120));
      } catch (Exception e) {
        // Ignore
      }
    });

    try {
      File workingDir = new File(System.getProperty(USER_DIR));
      log.info("Cleaning {}", workingDir.getCanonicalPath());
      log.info("Goodbye");

      cleanupVersionFolders(workingDir, emptySet());

      IOFileFilter fileFilter = or(prefixFileFilter("delegate"), prefixFileFilter("watcher"),
          prefixFileFilter("config-"), nameFileFilter("README.txt"), nameFileFilter("proxy.config"),
          nameFileFilter("profile"), nameFileFilter("profile.result"), nameFileFilter("mygclogfilename.gc"),
          nameFileFilter("nohup-watcher.out"), suffixFileFilter(".sh"));

      IOFileFilter dirFilter = or(prefixFileFilter("jre"), prefixFileFilter("backup."), nameFileFilter(".cache"),
          nameFileFilter("msg"), nameFileFilter("repository"), nameFileFilter("client-tools"));

      FileUtils.listFilesAndDirs(workingDir, fileFilter, dirFilter)
          .stream()
          .filter(file -> !workingDir.equals(file))
          .forEach(file -> {
            try {
              FileUtils.forceDelete(file);
            } catch (IOException e) {
              // Ignore
            }
          });
    } catch (Exception e) {
      // Ignore
    }

    try {
      FileUtils.touch(new File("__deleted__"));
      Thread.sleep(5000L);
    } catch (Exception e) {
      // Ignore
    }

    System.exit(0);
  }

  private void freeze() {
    log.info("Putting watcher in freeze mode...");
    frozen.set(true);

    // shutdown all the executors
    inputExecutor.shutdown();
    heartbeatExecutor.shutdown();
    executorService.shutdown();
    watchExecutor.shutdown();
    upgradeExecutor.shutdown();

    // schedule heartbeat so that delegate pod liveliness probe doesn't fail
    scheduleLocalHeartbeat();
  }
}
