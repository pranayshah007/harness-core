/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.search.framework.PerpetualSearchLocker.LockTimeoutCallback;
import software.wings.search.framework.SearchDistributedLock.SearchDistributedLockKeys;

import dev.morphia.query.Query;
import java.time.Instant;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Update search lock heartbeat task
 *
 * @author utkarsh
 */
@OwnedBy(PL)
@AllArgsConstructor
@Slf4j
public class SearchHeartbeatMonitor implements Runnable {
  private WingsPersistence wingsPersistence;
  private LockTimeoutCallback lockTimeoutCallback;
  private String lockName;
  private String uuid;
  private ConfigurationController configurationController;

  @Override
  public void run() {
    Query<SearchDistributedLock> query = wingsPersistence.createQuery(SearchDistributedLock.class)
                                             .field(SearchDistributedLockKeys.name)
                                             .equal(lockName)
                                             .field(SearchDistributedLockKeys.uuid)
                                             .equal(uuid);

    SearchDistributedLock searchDistributedLock = query.get();
    Instant instant = Instant.now();

    if (searchDistributedLock != null && configurationController.isPrimary()) {
      wingsPersistence.updateField(
          SearchDistributedLock.class, lockName, SearchDistributedLockKeys.heartbeat, Date.from(instant));
    } else {
      log.info("Search lock is deleted");
      lockTimeoutCallback.stop();
    }
  }
}
