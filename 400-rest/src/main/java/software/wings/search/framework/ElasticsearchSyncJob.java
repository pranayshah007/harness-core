/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;

import software.wings.dl.WingsPersistence;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * The job responsible to maintain sync
 * between application's persistent layer and
 * elasticsearch db at all times.
 *
 * @author utkarsh
 */

@OwnedBy(PL)
@Slf4j
public class ElasticsearchSyncJob implements Runnable {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private Provider<ElasticsearchBulkSyncTask> elasticsearchBulkSyncTaskProvider;
  @Inject private Provider<ElasticsearchRealtimeSyncTask> elasticsearchRealtimeSyncTaskProvider;
  @Inject private Provider<PerpetualSearchLocker> perpetualSearchLockerProvider;
  private PerpetualSearchLocker perpetualSearchLocker;
  private ElasticsearchRealtimeSyncTask elasticsearchRealtimeSyncTask;
  private ScheduledExecutorService scheduledExecutorService;
  private ScheduledFuture searchLock;

  @Override
  public void run() {
    try {
      while (getMaintenanceFlag()) {
        sleep(ofSeconds(30));
      }
      ElasticsearchBulkSyncTask elasticsearchBulkSyncTask = elasticsearchBulkSyncTaskProvider.get();
      perpetualSearchLocker = perpetualSearchLockerProvider.get();
      scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder().setNameFormat("search-heartbeat").build());
      elasticsearchRealtimeSyncTask = elasticsearchRealtimeSyncTaskProvider.get();
      String uuid = UUID.randomUUID().toString();

      searchLock = perpetualSearchLocker.acquireLock(ElasticsearchSyncJob.class.getName(), uuid, this::stop);
      log.info("Starting search synchronization now");

      SearchSyncHeartbeat searchSyncHeartbeat =
          new SearchSyncHeartbeat(wingsPersistence, ElasticsearchSyncJob.class.getName(), uuid);
      scheduledExecutorService.scheduleAtFixedRate(searchSyncHeartbeat, 30, 30, TimeUnit.MINUTES);

      ElasticsearchBulkSyncTaskResult elasticsearchBulkSyncTaskResult = elasticsearchBulkSyncTask.run();
      if (elasticsearchBulkSyncTaskResult.isSuccessful()) {
        elasticsearchRealtimeSyncTask.run(elasticsearchBulkSyncTaskResult.getChangeEventsDuringBulkSync());
      }
    } catch (RuntimeException e) {
      log.error("Search Sync Job unexpectedly stopped", e);
    } catch (InterruptedException e) {
      log.error("Search Sync job interrupted", e);
      Thread.currentThread().interrupt();
    } finally {
      log.info("Search sync job has stopped");
      stop();
    }
  }

  public void stop() {
    log.info("Cancelling search monitor future");
    if (searchLock != null) {
      searchLock.cancel(true);
    }
    log.info("Stopping realtime synchronization");
    elasticsearchRealtimeSyncTask.stop();
    scheduledExecutorService.shutdownNow();
    perpetualSearchLocker.shutdown();
  }
}
