/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.service;

import static io.harness.concurrent.HTimeLimiter.callInterruptible21;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.message.MessageConstants.NEXT_WATCHER;
import static io.harness.delegate.message.MessageConstants.WATCHER_DATA;
import static io.harness.delegate.message.MessageConstants.WATCHER_GO_AHEAD;
import static io.harness.delegate.message.MessageConstants.WATCHER_HEARTBEAT;
import static io.harness.delegate.message.MessageConstants.WATCHER_PROCESS;
import static io.harness.delegate.message.MessageConstants.WATCHER_STARTED;
import static io.harness.delegate.message.MessageConstants.WATCHER_VERSION;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.utils.MemoryPerformanceUtils.memoryUsage;
import static io.harness.watcher.app.WatcherApplication.getProcessId;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.message.Message;
import io.harness.delegate.message.MessageService;
import io.harness.exception.VersionInfoException;
import io.harness.filesystem.FileIo;
import io.harness.logging.AutoLogContext;
import io.harness.managerclient.ManagerClientV2;
import io.harness.managerclient.SafeHttpCall;
import io.harness.rest.RestResponse;
import io.harness.threading.Schedulable;
import io.harness.watcher.app.WatcherConfiguration;
import io.harness.watcher.app.WatcherConstants;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WatcherServiceImplV2 implements WatcherService {
  private static final String DELEGATE_SEQUENCE_CONFIG_FILE = "./delegate_sequence_config";
  private static final String NO_SPACE_LEFT_ON_DEVICE_ERROR = "No space left on device";

  @Named("watchExecutor") private final ScheduledExecutorService watchExecutor;
  @Named("upgradeExecutor") private final ScheduledExecutorService backgroundExecutor;
  @Named("heartbeatExecutor") private final ScheduledExecutorService heartbeatExecutor;
  private final MessageService messageService;
  private final WatcherConstants watcherConstants;
  private final Object waiter = new Object();

  private TimeLimiter timeLimiter;

  private final WatcherConfiguration watcherConfiguration;
  private final ManagerClientV2 managerClient;
  private Clock clock;

  @Override
  public void run(boolean upgrade) {
    performPreStartChecks();
    messageService.writeMessage(WATCHER_STARTED);
    if (upgrade) {
      log.info("[New] Upgraded watcher process started with config {}", watcherConfiguration.toString());
    } else {
      log.info("Starting watcher with config {}", watcherConfiguration.toString());
    }

    timeLimiter = HTimeLimiter.create(watchExecutor);
    try {
      performInitTasks();

      if (upgrade) {
        Message message = messageService.waitForMessage(WATCHER_GO_AHEAD, TimeUnit.MINUTES.toMillis(5), false);
        log.info(message != null ? "[New] Got go-ahead. Proceeding"
                                 : "[New] Timed out waiting for go-ahead. Proceeding anyway");
      }
      // Setup proxy params.
      // start ChronicleEvenTrailer.
      startScheduledChecks();
      messageService.removeData(WATCHER_DATA, NEXT_WATCHER);
      log.info("Watcher process " + (upgrade ? " upgraded " : " started ") + " successfully");

      synchronized (waiter) {
        waiter.wait();
      }
    } catch (InterruptedException e) {
      log.error("Interrupted while running watcher", e);
    }
  }

  private void performPreStartChecks() {
    // Perform recency check for yaml.
    log.info("Performing recency check !!");
    final String storageUrl = System.getenv().get("WATCHER_STORAGE_URL");
    final String checkLocation = System.getenv().get("WATCHER_CHECK_LOCATION");
    if ((isNotEmpty(storageUrl) && storageUrl.contains("storage"))
        || (isNotEmpty(checkLocation) && checkLocation.contains("watcherprod.txt"))) {
      log.warn("Delegate is running with older yaml, please update the delegate.yaml");
    }

    // Perform account status check.
    try {
      RestResponse<String> restResponse = callInterruptible21(timeLimiter, ofSeconds(5),
          () -> SafeHttpCall.execute(managerClient.getAccountStatus(watcherConfiguration.getAccountId())));

      if (restResponse != null && "DELETED".equals(restResponse.getResource())) {
        selfDestruct();
      }
    } catch (Exception e) {
      log.error("Error fetching account status ", e);
    }
  }

  private void performInitTasks() {
    try {
      if ("ECS".equals(System.getenv().get("DELEGATE_TYPE"))) {
        // Only generate this file in case of ECS delegate
        log.info("Generating delegate_sequence_config file");
        FileUtils.touch(new File(DELEGATE_SEQUENCE_CONFIG_FILE));
        String randomToken = UUIDGenerator.generateUuid();
        FileIo.writeWithExclusiveLockAcrossProcesses(
            "[TOKEN]" + randomToken + "[SEQ]", DELEGATE_SEQUENCE_CONFIG_FILE, StandardOpenOption.TRUNCATE_EXISTING);
      }
    } catch (IOException e) {
      log.warn("Failed to create DelegateSequenceConfigFile");
    }
  }

  private void startScheduledChecks() {
    backgroundExecutor.scheduleWithFixedDelay(
        messageService.getMessageCheckingRunnable(TimeUnit.SECONDS.toMillis(4), null), 0, 1, TimeUnit.SECONDS);

    backgroundExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while checking for upgrades", this::checkForWatcherUpgrade), 0,
        watcherConfiguration.getUpgradeCheckIntervalSeconds(), TimeUnit.SECONDS);

    backgroundExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while logging-performance", this::logPerformance), 0, 30, TimeUnit.SECONDS);

    heartbeatExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while heart-beating", this::sendHeartbeat), 0, 10, TimeUnit.SECONDS);

    watchExecutor.scheduleWithFixedDelay(
        new Schedulable("Error while watching delegate", this::watchDelegate), 0, 10, TimeUnit.SECONDS);
  }

  private void logPerformance() {
    try (AutoLogContext ignore = new AutoLogContext(obtainPerformance(), OVERRIDE_NESTS)) {
      log.info("Current performance");
    }
  }

  public Map<String, String> obtainPerformance() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    memoryUsage(builder, "heap-", memoryMXBean.getHeapMemoryUsage());
    memoryUsage(builder, "non-heap-", memoryMXBean.getNonHeapMemoryUsage());
    return builder.build();
  }

  private void sendHeartbeat() {
    try {
      Map<String, Object> heartbeatData = new HashMap<>();
      heartbeatData.put(WATCHER_HEARTBEAT, clock.millis());
      heartbeatData.put(WATCHER_PROCESS, getProcessId());
      heartbeatData.put(WATCHER_VERSION, watcherConstants.getVersion());
      messageService.putAllData(WATCHER_DATA, heartbeatData);
    } catch (VersionInfoException e) {
      log.error("Exception while sending local heartbeat ", e);
    } catch (Exception e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE_ERROR)) {
        log.error("Disk space is full");
      } else {
        log.error("Error putting all watcher data", e);
        throw e;
      }
    }
  }

  private void watchDelegate() {
    // TODO: acquire lock before going through.
  }

  @VisibleForTesting
  void checkForWatcherUpgrade() {
    // TODO: acquire lock before going through.
  }

  private void selfDestruct() {
    log.info("Self destructing current watcher and delegate");
    log.info("Goodbye");
    System.exit(0);
  }
}
