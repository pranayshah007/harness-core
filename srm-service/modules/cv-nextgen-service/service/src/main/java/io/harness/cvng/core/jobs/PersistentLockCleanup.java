/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.lock.mongo.MongoPersistentLocker.LOCKS_STORE;

import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import groovy.lang.Singleton;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PersistentLockCleanup implements Runnable {
  @Inject HPersistence hPersistence;

  public void run() {
    try {
      OffsetDateTime deleteBefore = OffsetDateTime.now().minusMinutes(15);
      final BasicDBObject filter =
          new BasicDBObject().append("lastUpdated", new BasicDBObject("$lt", Date.from(deleteBefore.toInstant())));
      hPersistence.getCollection(LOCKS_STORE, "locks").remove(filter);
    } catch (Exception ex) {
      log.error("Error while cleaning up Persistent lock : ", ex);
    }
  }
}
