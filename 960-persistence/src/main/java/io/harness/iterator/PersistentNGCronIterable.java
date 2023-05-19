/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@OwnedBy(PL)
public interface PersistentNGCronIterable extends PersistentIrregularIterable {
  int INVENTORY_MINIMUM = 2;

  CronParser unixParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
  CronParser quartzParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));

  default boolean expandNextIterations(
      boolean skipMissing, long throttled, String cronExpression, List<Long> times, String cronType) {
    final ZonedDateTime now = ZonedDateTime.now();

    // Take this item here, before we cleanup the list and potentially make it empty. We would like to align the items
    // based on the last previous one, instead of now, if they depend on each other.
    ZonedDateTime time =
        isNotEmpty(times) ? Instant.ofEpochMilli(times.get(times.size() - 1)).atZone(ZoneOffset.UTC) : now;

    final long epochMilli = now.toInstant().toEpochMilli();

    boolean removed = skipMissing && removeMissed(epochMilli, times);

    if (times.size() > INVENTORY_MINIMUM) {
      return removed;
    }

    final Cron cron;
    if (cronType.equalsIgnoreCase("UNIX")) {
      cron = unixParser.parse(cronExpression);
    } else {
      cron = quartzParser.parse(cronExpression);
    }
    ExecutionTime executionTime = ExecutionTime.forCron(cron);

    while (times.size() < 10) {
      final Optional<ZonedDateTime> nextTime = executionTime.nextExecution(time);
      if (!nextTime.isPresent()) {
        break;
      }

      time = nextTime.get();
      if (skipMissing && time.isBefore(now)) {
        continue;
      }

      times.add(time.toInstant().toEpochMilli());
    }

    return true;
  }
}
