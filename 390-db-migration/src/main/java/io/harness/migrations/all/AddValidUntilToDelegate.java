/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddValidUntilToDelegate implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    final DBCollection collection = wingsPersistence.getCollection(Delegate.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    int i = 1;
    try (HIterator<Delegate> delegateInstances = new HIterator<>(wingsPersistence.createQuery(Delegate.class)
                                                                     .field(DelegateKeys.validUntil)
                                                                     .doesNotExist()
                                                                     .project(DelegateKeys.lastHeartBeat, true)
                                                                     .fetch())) {
      while (delegateInstances.hasNext()) {
        final Delegate delegateInstance = delegateInstances.next();
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(
            OffsetDateTime.now().plus(delegateInstance.ttlMillis(), ChronoUnit.MILLIS).toInstant(), ZoneOffset.UTC);

        if (i % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
        }
        ++i;

        bulkWriteOperation
            .find(wingsPersistence.createQuery(Delegate.class)
                      .filter(DelegateKeys.uuid, delegateInstance.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject(
                "$set", new BasicDBObject("validUntil", java.util.Date.from(zonedDateTime.toInstant()))));
      }
    }
    if (i % 1000 != 1) {
      bulkWriteOperation.execute();
    }
  }
}
