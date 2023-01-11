/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance.stats;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot.InstanceStatsSnapshotKeys;
import software.wings.integration.IntegrationTestBase;
import software.wings.integration.IntegrationTestUtils;
import software.wings.service.impl.instance.stats.InstanceStatServiceImpl;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InstanceStatServiceIntegrationTest extends IntegrationTestBase {
  @Inject private InstanceStatServiceImpl statService;
  @Inject private HPersistence persistence;

  // namespacing accountId so that other tests are not impacted by this
  private static final String SOME_ACCOUNT_ID =
      "some-account-id-" + InstanceStatServiceIntegrationTest.class.getSimpleName();

  private boolean indexesEnsured;

  @Before
  public void ensureIndices() throws URISyntaxException {
    if (!indexesEnsured && !IntegrationTestUtils.isManagerRunning(client)) {
      persistence.getDatastore(InstanceStatsSnapshot.class).ensureIndexes(InstanceStatsSnapshot.class);
      indexesEnsured = true;
    }
  }

  @After
  public void clearCollection() {
    val ds = persistence.getDatastore(InstanceStatsSnapshot.class);
    ds.delete(fetchQuery());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testSave() {
    val stats = sampleSnapshot();
    val ds = persistence.getDatastore(InstanceStatsSnapshot.class);
    val initialCount = ds.getCount(fetchQuery());

    val saved = statService.save(stats);
    assertThat(saved).isTrue();

    val finalCount = ds.getCount(fetchQuery());
    assertThat(finalCount).isEqualTo(initialCount + 1);
  }

  private Query<InstanceStatsSnapshot> fetchQuery() {
    return persistence.createQuery(InstanceStatsSnapshot.class)
        .filter(InstanceStatsSnapshotKeys.accountId, SOME_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testAggregateOverPeriod() {
    val from = Instant.now();

    val times = 10;
    val statsToSave = list(times, this::sampleSnapshot);

    statsToSave.forEach(it -> {
      val saved = statService.save(it);
      assertThat(saved).isTrue();
    });

    val to = Instant.now();

    val accountId = statsToSave.get(0).getAccountId();
    val timelineFromDb = statService.aggregate(accountId, from, to);

    assertThat(timelineFromDb).hasSize(statsToSave.size());

    for (int i = 0; i < timelineFromDb.size(); i++) {
      val savedValue = statsToSave.get(i);
      val snapshotFromDb = timelineFromDb.get(i);
      assertThat(snapshotFromDb.getAccountId()).isEqualTo(accountId);
      assertThat(snapshotFromDb).isEqualTo(savedValue);
    }
  }

  private <T> List<T> list(int times, Supplier<T> supplier) {
    List<T> list = new ArrayList<>();
    for (int i = 0; i < times; i++) {
      list.add(supplier.get());
    }

    return list;
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testPercentile() {
    val from = Instant.now();

    val times = 100;
    val statsToSave = list(times, this::sampleSnapshot);

    statsToSave.forEach(it -> {
      val saved = statService.save(it);
      assertThat(saved).isTrue();
    });

    val to = Instant.now();
    val accountId = statsToSave.get(0).getAccountId();
    double percentile = statService.percentile(accountId, from, to, 95.0);
    double expected =
        statsToSave.stream().map(InstanceStatsSnapshot::getTotal).sorted().collect(Collectors.toList()).get(95);

    assertThat(expected).isCloseTo(percentile, offset(0.01));
  }

  private InstanceStatsSnapshot sampleSnapshot() {
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    val instant = Instant.now();

    int total = ThreadLocalRandom.current().nextInt(101, 150);
    int count = ThreadLocalRandom.current().nextInt(10, 100);

    val appAggregates = Arrays.asList(
        new InstanceStatsSnapshot.AggregateCount(EntityType.APPLICATION, "some-app", "some-app-id", count),
        new InstanceStatsSnapshot.AggregateCount(EntityType.APPLICATION, "other-app", "other-app-id", total - count));

    return new InstanceStatsSnapshot(instant, SOME_ACCOUNT_ID, appAggregates);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testGetLastSnapshotTime() {
    val before = Instant.now();
    val stat = sampleSnapshot();

    Instant lastTs = statService.getLastSnapshotTime(stat.getAccountId());
    assertThat(lastTs).isNull();

    val saved = statService.save(stat);
    val after = Instant.now();
    assertThat(saved).isTrue();

    lastTs = statService.getLastSnapshotTime(stat.getAccountId());
    assertThat(lastTs).isNotNull();

    assertThat(lastTs.isAfter(before)).isTrue();
    assertThat(lastTs.isBefore(after)).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testGetCurrentCount() {
    val stat = sampleSnapshot();

    double count = statService.currentCount(stat.getAccountId());
    assertThat(count).isZero();

    val saved = statService.save(stat);
    assertThat(saved).isTrue();

    count = statService.currentCount(stat.getAccountId());
    assertThat(count).isNotZero();
  }
}
