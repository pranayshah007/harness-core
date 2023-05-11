/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import static software.wings.app.ManagerCacheRegistrar.GIT_POLLING_CACHE;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.yaml.gitSync.YamlChangeSet.YamlChangeSetKeys;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitSyncPollingJob implements Runnable {
  private static final AtomicLong lastTimestampForStatusLogPrint = new AtomicLong(0);
  private static final Duration INTERVAL_BETWEEN_SYNCS = Duration.ofMinutes(5);
  @Inject FeatureFlagService featureFlagService;
  @Inject YamlChangeSetService yamlChangeSetService;
  @Inject HPersistence persistence;
  @Inject PersistentLocker persistentLocker;
  @Inject private ConfigurationController configurationController;
  @Inject @Named(GIT_POLLING_CACHE) private Cache<String, Long> gitPollingCache;

  @Override
  public void run() {
    try {
      if (!shouldRun()) {
        if (shouldPrintStatusLogs()) {
          lastTimestampForStatusLogPrint.set(System.currentTimeMillis());
          log.info("Not continuing with GitSyncPollingJob");
          TimeUnit.SECONDS.sleep(1);
        }
        return;
      }
      try (AcquiredLock ignore1 =
               persistentLocker.waitToAcquireLock("GitPolling", Duration.ofMinutes(3), Duration.ofMinutes(1))) {
        Set<String> accountIds = featureFlagService.getAccountIds(FeatureName.CG_GIT_POLLING);
        processGitPolling(accountIds);
      }
    } catch (Exception e) {
      log.error("Got issue in git sync polling job", e);
    }
  }

  private void processGitPolling(Set<String> accountIds) {
    accountIds.forEach(accountId -> {
      Long lastRunTime = gitPollingCache.get(accountId);
      if (lastRunTime == null || System.currentTimeMillis() >= lastRunTime + INTERVAL_BETWEEN_SYNCS.toMillis()) {
        Query<YamlGitConfig> yamlGitConfigQuery =
            persistence.createQuery(YamlGitConfig.class).filter(YamlChangeSetKeys.accountId, accountId);
        try (HIterator<YamlGitConfig> hIterator = new HIterator<>(yamlGitConfigQuery.fetch())) {
          while (hIterator.hasNext()) {
            YamlGitConfig yamlGitConfig = hIterator.next();
            try {
              handle(yamlGitConfig);
            } catch (Exception e) {
              log.error("Error in queuing git polling", e);
            }
          }
        }
        gitPollingCache.put(accountId, System.currentTimeMillis());
      }
    });
  }

  private void handle(YamlGitConfig entity) {
    yamlChangeSetService.pushYamlChangeSetForGitToHarness(entity.getAccountId(), entity.getBranchName(),
        entity.getGitConnectorId(), entity.getRepositoryName(), entity.getAppId());
  }

  private boolean shouldRun() {
    return !getMaintenanceFlag() && configurationController.isPrimary();
  }

  private boolean shouldPrintStatusLogs() {
    return lastTimestampForStatusLogPrint.get() == 0
        || (System.currentTimeMillis() - lastTimestampForStatusLogPrint.get() > TimeUnit.MINUTES.toMillis(5));
  }
}
