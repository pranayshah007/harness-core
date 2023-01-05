/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.handler.impl.segment;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.dl.WingsPersistence;
import software.wings.scheduler.events.segment.SegmentGroupEventJobContext;
import software.wings.scheduler.events.segment.SegmentGroupEventJobContext.SegmentGroupEventJobContextKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import org.bson.types.ObjectId;

@OwnedBy(PL)
@Singleton
public class SegmentGroupEventJobServiceImpl implements SegmentGroupEventJobService {
  private WingsPersistence persistence;

  @Inject
  public SegmentGroupEventJobServiceImpl(WingsPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  public void scheduleJob(String accountId) {
    schedule(accountId, ACCOUNT_BATCH_SIZE);
  }

  @VisibleForTesting
  void schedule(String accountId, int batchSize) {
    int index = batchSize - 1;

    // find a doc with less than 10 values
    Query<SegmentGroupEventJobContext> query = persistence.createQuery(SegmentGroupEventJobContext.class)
                                                   .field(SegmentGroupEventJobContextKeys.accountIds + "." + index)
                                                   .doesNotExist();

    if (null != query.get()) {
      UpdateOperations<SegmentGroupEventJobContext> updateOps =
          persistence.createUpdateOperations(SegmentGroupEventJobContext.class)
              .addToSet(SegmentGroupEventJobContextKeys.accountIds, accountId);

      persistence.update(query, updateOps);
    } else {
      SegmentGroupEventJobContext ctx = new SegmentGroupEventJobContext(
          Instant.now().plus(30, ChronoUnit.MINUTES).toEpochMilli(), Collections.singletonList(accountId));
      persistence.save(ctx);
    }
  }

  @Override
  public SegmentGroupEventJobContext get(String uuid) {
    return persistence.createQuery(SegmentGroupEventJobContext.class).field("_id").equal(new ObjectId(uuid)).get();
  }
}
