/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.AnomalyDataStub;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.url.HarnessUrl;
import io.harness.ccm.anomaly.utility.AnomalyUtility;
import io.harness.rule.Owner;

import java.sql.SQLException;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HarnessUrlTest extends CategoryTest {
  private static final String ACCOUNT_ID = AnomalyDataStub.accountId;
  private static final String CeSlackCommunicationURL =
      "https://app.harness.io/#/account/ACCOUNT_ID/continuous-efficiency/settings?currentTab=slackReport&filter=all&selectedView=COMMUNICATION";
  private static final String k8sClusterUrl =
      "https://app.harness.io/#/account/ACCOUNT_ID/continuous-efficiency/cluster/insights?aggregationType=DAY&chartType=column&utilizationAggregationType=Average&showOthers=false&from=1969-12-28&to=1970-01-05&currentView=TOTAL_COST&isFilterOn=true&showUnallocated=false&includeGCPDiscounts=false&clusterList=CLUSTER_ID&groupBy=Cluster";
  private static final String gcpProjectUrl =
      "https://app.harness.io/#/account/ACCOUNT_ID/continuous-efficiency/gcp/insights?aggregationType=DAY&chartType=column&utilizationAggregationType=Average&showOthers=false&from=1969-12-28&to=1970-01-05&currentView=GCP_COST&isFilterOn=true&showUnallocated=false&includeGCPDiscounts=false&gcpProjectList=GCP_PROJECT&groupBy=projectId";
  private static final String awsAccountUrl =
      "https://app.harness.io/#/account/ACCOUNT_ID/continuous-efficiency/aws/insights?aggregationType=DAY&chartType=column&utilizationAggregationType=Average&showOthers=false&from=1969-12-28&to=1970-01-05&currentView=UNBLENDED_COST&isFilterOn=true&showUnallocated=false&includeGCPDiscounts=false&awsAccountList=AWS_ACCOUNT&groupBy=awsLinkedAccount";
  AnomalyEntity clusterAnomaly;
  AnomalyEntity namespaceAnomaly;
  AnomalyEntity gcpProjectAnomaly;
  AnomalyEntity awsAccountAnomaly;

  String baseUrl;

  @Before
  public void setUp() throws SQLException {
    baseUrl = "https://app.harness.io";
    clusterAnomaly = AnomalyDataStub.getClusterAnomaly();
    namespaceAnomaly = AnomalyDataStub.getNamespaceAnomaly();
    gcpProjectAnomaly = AnomalyDataStub.getGcpProjectAnomaly();
    awsAccountAnomaly = AnomalyDataStub.getAwsAccountAnomaly();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldCreateCeCommunicationUrl() {
    assertThat(HarnessUrl.getCeSlackCommunicationSettings(ACCOUNT_ID, baseUrl)).isEqualTo(CeSlackCommunicationURL);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldCreateClusterUrl() {
    assertThat(HarnessUrl.getK8sUrl(clusterAnomaly, baseUrl)).isEqualTo(k8sClusterUrl);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldCreateGcpProjectUrl() {
    assertThat(HarnessUrl.getK8sUrl(gcpProjectAnomaly, baseUrl)).isEqualTo(gcpProjectUrl);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldCreateAwsAccountUrl() {
    assertThat(HarnessUrl.getK8sUrl(awsAccountAnomaly, baseUrl)).isEqualTo(awsAccountUrl);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldConvertDate() {
    assertThat(AnomalyUtility.convertInstantToDate(Instant.ofEpochMilli(0))).isEqualTo("1970-01-01");
  }
}
