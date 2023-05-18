/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.query.Sort;
import java.time.Clock;
import java.util.ConcurrentModificationException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLIRecordTest extends CvNextGenTestBase {
  @Inject HPersistence hPersistence;
  @Inject Clock clock;
  private static final String sliId = generateUuid();

  @Test(expected = ConcurrentModificationException.class)
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate() {
    SLIRecord sliRecord = SLIRecord.builder()
                              .verificationTaskId(generateUuid())
                              .sliId(sliId)
                              .sliVersion(0)
                              .timestamp(clock.instant())
                              .runningGoodCount(10)
                              .runningBadCount(5)
                              .sliState(SLIState.GOOD)
                              .build();
    hPersistence.save(sliRecord);
    SLIRecord latestSLIRecordFirst = hPersistence.createQuery(SLIRecord.class, excludeAuthority)
                                         .filter(SLIRecordKeys.sliId, sliId)
                                         .order(Sort.descending(SLIRecordKeys.timestamp))
                                         .get();
    SLIRecord latestSLIRecordSecond = hPersistence.createQuery(SLIRecord.class, excludeAuthority)
                                          .filter(SLIRecordKeys.sliId, sliId)
                                          .order(Sort.descending(SLIRecordKeys.timestamp))
                                          .get();
    latestSLIRecordFirst.setSliVersion(1);
    latestSLIRecordFirst.setSliState(SLIState.BAD);
    latestSLIRecordFirst.setRunningBadCount(6);
    latestSLIRecordFirst.setRunningGoodCount(9);
    hPersistence.save(latestSLIRecordFirst);
    latestSLIRecordSecond.setSliVersion(1);
    latestSLIRecordSecond.setRunningGoodCount(9);
    latestSLIRecordSecond.setRunningBadCount(5);
    latestSLIRecordSecond.setSliState(SLIState.NO_DATA);
    hPersistence.save(latestSLIRecordSecond);
  }
}
