/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Sort;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class EntityDisabledTimeServiceImpl implements EntityDisabledTimeService {
  @Inject private HPersistence hPersistence;

  @Override
  public void save(EntityDisableTime entityDisableTime) {
    hPersistence.save(entityDisableTime);
  }

  @Override
  public List<EntityDisableTime> get(String entityId, String accountId) {
    return hPersistence.createQuery(EntityDisableTime.class, excludeAuthorityCount)
        .filter(EntityDisableTime.EntityDisabledTimeKeys.entityUUID, entityId)
        .filter(EntityDisableTime.EntityDisabledTimeKeys.accountId, accountId)
        .order(Sort.ascending(EntityDisableTime.EntityDisabledTimeKeys.startTime))
        .asList();
  }

  @Override
  public Pair<Long, Long> getDisabledMinBetweenRecords(
      long startTime, long endTime, int currentRange, List<EntityDisableTime> disableTimes) {
    long extra = 0;
    while (currentRange < disableTimes.size() && disableTimes.get(currentRange).getStartTime() <= endTime) {
      long startTimeCeil = disableTimes.get(currentRange).getStartTime();
      long endTimeFloor = disableTimes.get(currentRange).getEndTime();

      startTimeCeil = (long) (Math.ceil((double) startTimeCeil / 60000) * 60000);
      endTimeFloor = (long) (Math.floor((double) endTimeFloor / 60000) * 60000);

      if (endTimeFloor <= startTime) {
        currentRange++;
      } else if (startTimeCeil > startTime && endTimeFloor <= endTime) {
        extra += endTimeFloor - startTimeCeil + 60000;
        currentRange++;
      } else if (startTimeCeil == startTime && endTimeFloor <= endTime) {
        extra += endTimeFloor - startTimeCeil;
        currentRange++;
      } else if (startTimeCeil < startTime && endTimeFloor <= endTime) {
        extra += endTimeFloor - startTime;
        currentRange++;
      } else if (startTimeCeil > startTime) {
        extra += endTime - startTimeCeil + 60000;
        break;
      } else if (startTimeCeil == startTime) {
        extra += endTime - startTimeCeil;
        break;
      } else {
        extra += endTime - startTime;
        break;
      }
    }
    return Pair.of(extra / 60000, (long) currentRange);
  }

  @Override
  public boolean isMinuteEnabled(
      List<EntityDisableTime> disableTimes, int currentDisabledRangeIndex, SLIRecordBucket sliRecordBucket) {
    // Just check across all ranges if this minute falls in it.
    // We need to a minus 5 for SLI Bucket.
    boolean enabled = true;
    if (currentDisabledRangeIndex < disableTimes.size()) {
      enabled =
          !disableTimes.get(currentDisabledRangeIndex).contains(sliRecordBucket.getBucketStartTime().toEpochMilli());
    }
    // TODO check any min
    if (currentDisabledRangeIndex > 0) {
      enabled = enabled
          && !disableTimes.get(currentDisabledRangeIndex - 1)
                  .contains(sliRecordBucket.getBucketStartTime().toEpochMilli());
    }
    return enabled;
  }
}
