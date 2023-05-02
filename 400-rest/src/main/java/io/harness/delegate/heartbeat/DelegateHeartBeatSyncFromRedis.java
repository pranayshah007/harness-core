/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.delegate.utils.DelegateServiceConstants.HEARTBEAT_EXPIRY_TIME_FIVE_MINS;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateHeartBeatSyncFromRedis implements Runnable {
  @Inject private DelegateCache delegateCache;
  @Inject private HPersistence persistence;

  private static final long MAX_HB_TIMEOUT = TimeUnit.MINUTES.toMillis(3);

  @Override
  public void run() {
    List<Delegate> delegateList = delegateCache.getAllDelegatesFromRedisCache();
    List<String> delegates = delegateList.stream()
                                 .filter(delegate
                                     -> delegate.getLastHeartBeat()
                                         > System.currentTimeMillis() - HEARTBEAT_EXPIRY_TIME_FIVE_MINS.toMillis())
                                 .map(Delegate::getUuid)
                                 .collect(Collectors.toList());
    // update DB with current time stamp
    Query<Delegate> query = persistence.createQuery(Delegate.class).field(DelegateKeys.uuid).in(delegates);
    final UpdateOperations<Delegate> updateOperations = persistence.createUpdateOperations(Delegate.class);
    setUnset(updateOperations, DelegateKeys.lastHeartBeat, System.currentTimeMillis());
    persistence.update(query, updateOperations);
    log.info("Heartbeat updated for {},", delegates);
  }
}
