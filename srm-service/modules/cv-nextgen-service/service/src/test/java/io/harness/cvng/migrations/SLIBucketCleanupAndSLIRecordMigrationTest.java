/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migrations;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static junit.framework.TestCase.assertEquals;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.migration.list.SLIBucketCleanupAndSLIRecordMigration;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket.SLIRecordBucketKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLIBucketCleanupAndSLIRecordMigrationTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;

  @Inject HPersistence hPersistence;
  @Inject Clock clock;
  @Inject SLIBucketCleanupAndSLIRecordMigration migration;

  @Before
  @SneakyThrows
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    clock = Clock.fixed(TIME_FOR_TESTS, ZoneOffset.UTC);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testMigrate() {
    List<SLIState> sliStates = List.of(SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD, SLIState.SKIP_DATA,
        SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD, SLIState.SKIP_DATA, SLIState.GOOD,
        SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD, SLIState.SKIP_DATA);
    createSLIRecords("sliId1", sliStates, clock.instant(), clock.instant().plus(5, ChronoUnit.MINUTES));
    createSLIRecords("sliId2", sliStates, clock.instant(), clock.instant().plus(4, ChronoUnit.MINUTES));
    createSLIRecords("sliId3", sliStates, clock.instant(), clock.instant().plus(10, ChronoUnit.MINUTES));
    createSLIRecords("sliId4", sliStates, clock.instant(), clock.instant().plus(15, ChronoUnit.MINUTES));
    createSLIRecords("sliId5", sliStates, clock.instant(), clock.instant().plus(13, ChronoUnit.MINUTES));
    createSLIRecords("sliId6", sliStates, clock.instant(), clock.instant().plus(4, ChronoUnit.MINUTES));
    createSLIRecords(
        "sliId6", sliStates, clock.instant().plus(5, ChronoUnit.MINUTES), clock.instant().plus(12, ChronoUnit.MINUTES));

    migration.migrate();
    List<SLIRecordBucket> sliRecordBuckets = hPersistence.createQuery(SLIRecordBucket.class).asList();
    assertEquals(sliRecordBuckets.size(), 9);

    sliRecordBuckets =
        hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, "sliId1").asList();
    assertEquals(sliRecordBuckets.size(), 1);
    assertEquals(sliRecordBuckets.get(0).getRunningGoodCount(), 2);
    assertEquals(sliRecordBuckets.get(0).getRunningBadCount(), 1);

    sliRecordBuckets =
        hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, "sliId2").asList();
    assertEquals(sliRecordBuckets.size(), 0);

    sliRecordBuckets =
        hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, "sliId3").asList();
    assertEquals(sliRecordBuckets.size(), 2);
    assertEquals(sliRecordBuckets.get(1).getRunningGoodCount(), 4);
    assertEquals(sliRecordBuckets.get(1).getRunningBadCount(), 2);

    sliRecordBuckets =
        hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, "sliId4").asList();
    assertEquals(sliRecordBuckets.size(), 3);
    assertEquals(sliRecordBuckets.get(2).getRunningGoodCount(), 6);
    assertEquals(sliRecordBuckets.get(2).getRunningBadCount(), 3);

    sliRecordBuckets =
        hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, "sliId5").asList();
    assertEquals(sliRecordBuckets.size(), 2);
    assertEquals(sliRecordBuckets.get(1).getRunningGoodCount(), 4);
    assertEquals(sliRecordBuckets.get(1).getRunningBadCount(), 2);

    sliRecordBuckets =
        hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, "sliId6").asList();
    assertEquals(sliRecordBuckets.size(), 1);
    assertEquals(sliRecordBuckets.get(0).getRunningGoodCount(), 2);
    assertEquals(sliRecordBuckets.get(0).getRunningBadCount(), 1);
  }

  private List<SLIRecord> createSLIRecords(String sliId, List<SLIState> states, Instant startTime, Instant endTime) {
    int index = 0;
    int runningGoodCount = 0, runningBadCount = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      if (states.get(index) == SLIState.GOOD) {
        runningGoodCount += 1;
      }
      if (states.get(index) == SLIState.BAD) {
        runningBadCount += 1;
      }
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(sliId)
                                .sliId(sliId)
                                .version(0)
                                .sliState(states.get(index))
                                .runningBadCount(runningBadCount)
                                .runningGoodCount(runningGoodCount)
                                .sliVersion(0)
                                .timestamp(instant)
                                .build();
      sliRecords.add(sliRecord);
      index++;
    }
    hPersistence.save(sliRecords);
    return sliRecords;
  }
}
