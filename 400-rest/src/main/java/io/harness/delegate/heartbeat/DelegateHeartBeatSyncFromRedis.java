/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.time.Duration.ofMinutes;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateHeartBeatSyncFromRedis implements Runnable {
  @Inject private DelegateCache delegateCache;
  @Inject private HPersistence persistence;
  @Inject PersistentLocker persistentLocker;
  private final String lockName = "DelegateHeartBeatSyncFromRedis";
  Duration MAX_HB_TIMEOUT = ofMinutes(5);

  @Override
  public void run() {
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(15), Duration.ofMinutes(1))) {
      if (lock == null) {
        log.error("Unable to acquire lock while syncing delegate heartbeat from redis to mongo");
        return;
      }
      List<Delegate> delegateList = delegateCache.getAllDelegatesFromRedisCache();
      List<String> delegates =
          delegateList.stream()
              .filter(delegate -> delegate.getLastHeartBeat() > System.currentTimeMillis() - MAX_HB_TIMEOUT.toMillis())
              .map(Delegate::getUuid)
              .collect(Collectors.toList());
      // update DB with current time stamp
      Query<Delegate> query =
          persistence.createQuery(Delegate.class, excludeAuthority).field(DelegateKeys.uuid).in(delegates);
      final UpdateOperations<Delegate> updateOperations = persistence.createUpdateOperations(Delegate.class);
      setUnset(updateOperations, DelegateKeys.lastHeartBeat, System.currentTimeMillis());
      persistence.update(query, updateOperations);
      log.info("Heartbeat updated for {},", delegates);
      lock.release();
    } catch (Exception e) {
      log.error("Unable to sync delegate heartbeat from redis to mongo.", e);
    }
  }
}
