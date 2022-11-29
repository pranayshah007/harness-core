/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.marketplace.gcp;

import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.HITESH;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot.InstanceStatsSnapshotKeys;
import software.wings.beans.marketplace.gcp.GCPUsageReport;
import software.wings.beans.marketplace.gcp.GCPUsageReport.GCPUsageReportKeys;
import software.wings.integration.IntegrationTestBase;
import software.wings.service.impl.instance.stats.InstanceStatServiceImpl;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mongodb.morphia.query.Query;

public class GCPMarketPlaceServiceIntegrationTest extends IntegrationTestBase {
  @Inject private GCPUsageReportServiceImpl gcpUsageReportService;
  @Inject private GCPMarketPlaceServiceImpl gcpMarketPlaceService;
  @Inject private InstanceStatServiceImpl statService;
  @Inject private HPersistence persistence;
  private boolean indexesEnsured;

  // namespacing accountId so that other tests are not impacted by this
  private static final String SOME_ACCOUNT_ID =
      "gcp-mkt-account-id-" + GCPMarketPlaceServiceIntegrationTest.class.getSimpleName();

  private static final String SOME_CONSUMER_ID =
      "gcp-mkt-consumer-id-" + GCPMarketPlaceServiceIntegrationTest.class.getSimpleName();

  private static final String SOME_OPERATION_ID =
      "gcp-mkt-operation-id-" + GCPMarketPlaceServiceIntegrationTest.class.getSimpleName();

  private static final String SOME_ENTITLEMENT_NAME =
      "gcp-mkt-entitlement-" + GCPMarketPlaceServiceIntegrationTest.class.getSimpleName();

  @Before
  public void ensureIndices() {
    if (!indexesEnsured) {
      persistence.getDatastore(GCPUsageReport.class).ensureIndexes(GCPUsageReport.class);
      persistence.getDatastore(InstanceStatsSnapshot.class).ensureIndexes(InstanceStatsSnapshot.class);
      indexesEnsured = true;
    }
  }

  @Test
  @Owner(developers = HITESH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testGCPUsageReport() {
    val currentTime = Instant.now();
    val startTime = currentTime.truncatedTo(ChronoUnit.MINUTES);
    val endTime = currentTime.plus(3, ChronoUnit.DAYS);
    val usageReportStartTime = startTime.minus(1, ChronoUnit.MINUTES);
    val usageReportEndTime = startTime.plusSeconds(1);
    val gcpUsageReport = getSampleGCPUsageReport(usageReportStartTime, usageReportEndTime);

    gcpUsageReportService.create(gcpUsageReport);

    val statsToSave = generateInstanceStatsSnapshot(startTime, endTime);

    statsToSave.forEach(it -> {
      val saved = statService.save(it);
      assertThat(saved).isTrue();
    });

    /*gcpMarketPlaceService.createUsageReport(SOME_ACCOUNT_ID);

    val reportEndTime = startTime.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

    val gcpUsageReports = gcpUsageReportService.fetchGCPUsageReport(SOME_ACCOUNT_ID, startTime, reportEndTime);
    val savedGCPUsageReport = gcpUsageReports.get(0);

    assertThat( usageReportEndTime).isEqualTo(savedGCPUsageReport.getStartTimestamp());
    assertThat( reportEndTime).isEqualTo(savedGCPUsageReport.getEndTimestamp());

    val nextReportStartTime = reportEndTime;
    val nextReportEndTime = nextReportStartTime.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
    val nextGCPUsageReports =
        gcpUsageReportService.fetchGCPUsageReport(SOME_ACCOUNT_ID, nextReportStartTime, nextReportEndTime);
    val nextSavedGCPUsageReport = nextGCPUsageReports.get(0);

    assertThat( nextReportStartTime).isEqualTo(nextSavedGCPUsageReport.getStartTimestamp());
    assertThat( nextReportEndTime).isEqualTo(nextSavedGCPUsageReport.getEndTimestamp());*/
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGCPUsageReportDeleteByAccountId() {
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    Query<PersistentEntity> mockedQueryEntity = mock(Query.class);
    when(persistence.createQuery(any())).thenReturn(mockedQueryEntity);
    when(mockedQueryEntity.filter(anyString(), any())).thenReturn(mockedQueryEntity);
    gcpMarketPlaceService.deleteByAccountId(ACCOUNT_ID);
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
  }
  private GCPUsageReport getSampleGCPUsageReport(Instant startTime, Instant endTime) {
    return new GCPUsageReport(
        SOME_ACCOUNT_ID, SOME_CONSUMER_ID, SOME_OPERATION_ID, SOME_ENTITLEMENT_NAME, startTime, endTime, 5);
  }

  private List<InstanceStatsSnapshot> generateInstanceStatsSnapshot(Instant startTime, Instant endTime) {
    List<InstanceStatsSnapshot> instanceStatsSnapshots = new ArrayList<>();
    while (startTime.toEpochMilli() < endTime.toEpochMilli()) {
      instanceStatsSnapshots.add(sampleSnapshot(startTime));
      startTime = startTime.plus(60, ChronoUnit.MINUTES);
    }
    return instanceStatsSnapshots;
  }

  private InstanceStatsSnapshot sampleSnapshot(Instant instant) {
    int total = ThreadLocalRandom.current().nextInt(101, 150);
    int count = ThreadLocalRandom.current().nextInt(10, 100);

    val appAggregates = Arrays.asList(
        new InstanceStatsSnapshot.AggregateCount(EntityType.APPLICATION, "some-app", "some-app-id", count),
        new InstanceStatsSnapshot.AggregateCount(EntityType.APPLICATION, "other-app", "other-app-id", total - count));

    return new InstanceStatsSnapshot(instant, SOME_ACCOUNT_ID, appAggregates);
  }

  @After
  public void clearCollection() {
    val ds = persistence.getDatastore(GCPUsageReport.class);
    ds.delete(ds.createQuery(GCPUsageReport.class).filter(GCPUsageReportKeys.accountId, SOME_ACCOUNT_ID));

    val dsInstanceStatsSnapshot = persistence.getDatastore(InstanceStatsSnapshot.class);
    dsInstanceStatsSnapshot.delete(fetchQuery());
  }

  private Query<InstanceStatsSnapshot> fetchQuery() {
    return persistence.createQuery(InstanceStatsSnapshot.class)
        .filter(InstanceStatsSnapshotKeys.accountId, SOME_ACCOUNT_ID);
  }
}
