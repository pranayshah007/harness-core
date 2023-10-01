/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateHeartBeatSyncFromRedis implements Runnable {
  @Inject private DelegateCache delegateCache;
  @Inject private HPersistence persistence;
  private final int BULK_OPERATION_MAX = 5000;

  @Override
  public void run() {
    startSync();
  }

  @VisibleForTesting
  protected void startSync() {
    log.info("Start syncing delegate heartbeat from redis cache to DB");
    try {
      final DBCollection collection = persistence.getCollection(Delegate.class);
      BulkWriteOperation bulkOperation = collection.initializeUnorderedBulkOperation();
      int delegateRecordsToBulkUpdate = 0;
      try (HIterator<Delegate> delegates = new HIterator<>(persistence.createQuery(Delegate.class)
                                                               .project(Delegate.UUID_KEY, true)
                                                               .project(Delegate.ACCOUNT_ID_KEY, true)
                                                               .fetch())) {
        while (delegates.hasNext()) {
          Delegate delegate = delegates.next();
          Delegate delegateFromCache = delegateCache.get(delegate.getAccountId(), delegate.getUuid());
          if (delegateFromCache != null) {
            bulkOperation.find(new BasicDBObject("_id", delegate.getUuid()))
                .updateOne(new BasicDBObject(
                    "$set", new BasicDBObject(DelegateKeys.lastHeartBeat, delegateFromCache.getLastHeartBeat())));
            delegateRecordsToBulkUpdate++;
            log.info("Updating delegate heartbeat to DB for {}", delegate.getUuid());
          }
        }

        if (delegateRecordsToBulkUpdate > BULK_OPERATION_MAX) {
          bulkOperation.execute();
          delegateRecordsToBulkUpdate = 0;
          bulkOperation = collection.initializeUnorderedBulkOperation();
        }
      }
      if (delegateRecordsToBulkUpdate > 0) {
        bulkOperation.execute();
      }
    } catch (Exception e) {
      log.error("Error while updating heartbeat from redis cache to mongo", e);
    }
  }
}
