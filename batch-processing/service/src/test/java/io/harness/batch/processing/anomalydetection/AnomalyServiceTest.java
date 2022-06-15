/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.rule.OwnerRule.SANDESH;

import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.AnomalyDataStub;
import io.harness.ccm.anomaly.dao.AnomalyEntityDao;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.service.impl.AnomalyServiceImpl;
import io.harness.rule.Owner;

import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AnomalyServiceTest extends CategoryTest {
  private static final String LIST_QUERY =
      "SELECT t0.* FROM anomalies t0 WHERE ((t0.accountid = 'ACCOUNT_ID') AND (t0.anomalytime = '1970-01-01T00:00:00Z'))";
  private static final String LIST_K8S_QUERY =
      "SELECT t0.*,actualcost - expectedcost AS difference FROM anomalies t0 WHERE ((t0.accountid = 'ACCOUNT_ID') AND (t0.anomalytime <= '1970-01-01T00:00:00Z') AND (t0.anomalytime >= '1969-12-17T00:00:00Z') AND (t0.clusterid IS NOT NULL)) ORDER BY t0.anomalytime ASC,difference DESC";
  private static final String OVERVIEW_QUERY =
      "SELECT t0.*,actualcost - expectedcost AS difference FROM anomalies t0 WHERE ((t0.accountid = 'ACCOUNT_ID') AND (t0.anomalytime <= '1970-01-01T00:00:00Z') AND (t0.anomalytime >= '1969-12-17T00:00:00Z')) ORDER BY t0.anomalytime ASC,difference DESC";
  @Mock private AnomalyEntityDao anomalyEntityDao;
  @InjectMocks private AnomalyServiceImpl anomalyService;

  private Instant date;
  private String accountId;

  @Before
  public void setup() {
    date = AnomalyDataStub.anomalyTime;
    accountId = AnomalyDataStub.accountId;
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldDelete() {
    List<String> ids = Arrays.asList("ID1", "ID2");
    anomalyService.delete(ids, date);
    verify(anomalyEntityDao).delete(ids, date);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    AnomalyEntity anomaly = AnomalyDataStub.getClusterAnomaly();
    anomalyService.update(anomaly);
    verify(anomalyEntityDao).update(anomaly);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldInsert() {
    List<AnomalyEntity> anomalyList = Arrays.asList(AnomalyDataStub.getClusterAnomaly());
    anomalyService.insert(anomalyList);
    verify(anomalyEntityDao).insert(anomalyList);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldList() {
    anomalyService.list(accountId, date);
    verify(anomalyEntityDao).list(LIST_QUERY);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldK8sList() {
    List<QLBillingDataFilter> filters =
        Arrays.asList(AnomalyDataStub.getBeforeTimeFilter(), AnomalyDataStub.getAfterTimeFilter());
    List<QLCCMGroupBy> groupBy = Arrays.asList(AnomalyDataStub.getClusterGroupBy());
    anomalyService.listK8s(accountId, filters, groupBy);
    verify(anomalyEntityDao).list(LIST_K8S_QUERY);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldOverviewList() {
    List<QLBillingDataFilter> filters =
        Arrays.asList(AnomalyDataStub.getBeforeTimeFilter(), AnomalyDataStub.getAfterTimeFilter());
    anomalyService.listOverview(accountId, filters);
    verify(anomalyEntityDao).list(OVERVIEW_QUERY);
  }
}
