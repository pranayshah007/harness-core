/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.managerConfiguration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.ManagerConfiguration.Builder.aManagerConfiguration;
import static software.wings.beans.ManagerConfiguration.MATCH_ALL_VERSION;
import static software.wings.core.managerConfiguration.ConfigChangeEvent.PrimaryChanged;

import static java.util.Collections.singletonList;
import static org.apache.commons.collections.MapUtils.synchronizedMap;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.mongo.MongoPersistence;
import io.harness.queue.QueueController;
import io.harness.version.VersionInfoManager;

import software.wings.beans.ManagerConfiguration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.StringUtils;

@Singleton
@Slf4j
@OwnedBy(PL)
@TargetModule(HarnessModule._960_PERSISTENCE)
@BreakDependencyOn("software.wings.beans.ManagerConfiguration")
public class ConfigurationController implements Managed, QueueController {
  @Inject private MongoPersistence persistence;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private ExecutorService executorService;

  private static final long DEFAULT_POLL_INTERVAL_MILLIS = 5000;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Map<ConfigChangeListener, List<ConfigChangeEvent>> configChangeListeners =
      synchronizedMap(new HashMap<>());
  private final AtomicBoolean primary = new AtomicBoolean(true);
  private final AtomicReference<String> primaryVersion = new AtomicReference<>(MATCH_ALL_VERSION);
  private long pollIntervalInMillis;

  public ConfigurationController() {
    this.pollIntervalInMillis = DEFAULT_POLL_INTERVAL_MILLIS;
  }

  public ConfigurationController(long pollIntervalInMillis) {
    this.pollIntervalInMillis = pollIntervalInMillis;
  }

  public void register(ConfigChangeListener listener, List<ConfigChangeEvent> configChangeEvents) {
    configChangeListeners.put(listener, configChangeEvents);
  }

  public void deRegister(ConfigChangeListener listener) {
    if (listener != null) {
      configChangeListeners.remove(listener);
    }
  }

  @Override
  public void start() {
    executorService.submit(this::run);
  }

  @Override
  public void stop() {
    running.set(false);
  }

  @Override
  public boolean isPrimary() {
    return primary.get();
  }

  @Override
  public boolean isNotPrimary() {
    return !primary.get();
  }

  public String getPrimaryVersion() {
    return primaryVersion.get();
  }

  private void run() {
    while (running.get()) {
      ManagerConfiguration managerConfiguration = persistence.createQuery(ManagerConfiguration.class).get();
      if (managerConfiguration == null) {
        managerConfiguration = aManagerConfiguration().withPrimaryVersion(MATCH_ALL_VERSION).build();
        persistence.save(managerConfiguration);
      }

      if (!StringUtils.equals(primaryVersion.get(), managerConfiguration.getPrimaryVersion())) {
        primaryVersion.set(managerConfiguration.getPrimaryVersion());
      }

      // With introduction of the patch version feature, we need to incorporate the patch version to calculate the
      // current primary version of manager. If the `primaryVersion` from DB doesn't have patch then we fall back to
      // using getVersion() like earlier. We always build the full version from buildNo and patch like below.
      String currPrimaryVersion = managerConfiguration.getPrimaryVersion().contains("-")
          ? versionInfoManager.getVersionInfo().getVersion() + "-" + versionInfoManager.getVersionInfo().getPatch()
          : versionInfoManager.getVersionInfo().getVersion();

      boolean isPrimary = StringUtils.equals(MATCH_ALL_VERSION, managerConfiguration.getPrimaryVersion())
          || StringUtils.equals(currPrimaryVersion, managerConfiguration.getPrimaryVersion());

      if (primary.getAndSet(isPrimary) != isPrimary) {
        log.info("{} primary mode", isPrimary ? "Entering" : "Leaving");
        synchronized (configChangeListeners) {
          configChangeListeners.forEach((k, v) -> executorService.submit(() -> {
            if (configChangeListeners.get(k).contains(PrimaryChanged)) {
              k.onConfigChange(singletonList(PrimaryChanged));
            }
          }));
        }
      }
      waitForSometime();
    }
  }

  private void waitForSometime() {
    sleep(Duration.ofMillis(pollIntervalInMillis));
  }
}
