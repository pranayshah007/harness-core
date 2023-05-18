/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsCloudWatchHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.request.AwsCloudWatchMetricDataRequest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.AwsCloudWatchMetricDataResponse;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.EcsUtilizationData;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.MetricValue;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EcsMetricClientTest extends CategoryTest {
  @Mock private AwsCloudWatchHelperService awsCloudWatchHelperService;
  @Captor private ArgumentCaptor<AwsCloudWatchMetricDataRequest> captor;
  private EcsMetricClient ecsMetricClient;

  @Before
  public void setUp() throws Exception {
    ecsMetricClient = new EcsMetricClient(awsCloudWatchHelperService);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldMakeAggregatedQuery() throws Exception {
    AwsCrossAccountAttributes awsCrossAccountAttributes = AwsCrossAccountAttributes.builder().build();
    String region = "region";
    String settingId = "settingId";
    CECluster ceCluster = CECluster.builder()
                              .accountId("accountId")
                              .clusterName("clusterName")
                              .region(region)
                              .infraAccountId("infraAccountId")
                              .parentAccountSettingId(settingId)
                              .build();
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
    Date startTime = Date.from(now.minus(1, ChronoUnit.HOURS));
    Date endTime = Date.from(now);
    Cluster cluster = new Cluster().withClusterName("clusterName").withClusterArn("cluster1-arn");
    Service svc1 = new Service().withServiceName("svc1").withServiceArn("svc1-arn");
    Service svc2 = new Service().withServiceName("svc2").withServiceArn("svc2-arn");
    List<Service> services = Arrays.asList(svc1, svc2);

    // stubbing limited results only, enough to cover the paths
    given(awsCloudWatchHelperService.getMetricData(any(AwsCloudWatchMetricDataRequest.class)))
        .willReturn(AwsCloudWatchMetricDataResponse.builder()
                        .metricDataResults(Arrays.asList(new MetricDataResult()
                                                             .withId("id_991077f7b6438acc8de1f36eb6402639")
                                                             .withTimestamps(singletonList(startTime))
                                                             .withValues(singletonList(33.4)),
                            new MetricDataResult()
                                .withId("id_9e0ddf19cd12fd802808d7955369c349")
                                .withTimestamps(singletonList(startTime))
                                .withValues(singletonList(78.3)),
                            new MetricDataResult()
                                .withId("id_a7c850bcd51e3f39571ef793bb641201")
                                .withTimestamps(singletonList(endTime))
                                .withValues(singletonList(66.32))))
                        .build());

    final List<EcsUtilizationData> utilizationMetrics = ecsMetricClient.getUtilizationMetrics(
        awsCrossAccountAttributes, startTime, endTime, cluster, services, ceCluster);
    assertThat(utilizationMetrics)
        .containsExactlyInAnyOrder(EcsUtilizationData.builder()
                                       .clusterArn("cluster1-arn")
                                       .clusterName("clusterName")
                                       .clusterId(ceCluster.getUuid())
                                       .settingId(settingId)
                                       .metricValues(singletonList(MetricValue.builder()
                                                                       .metricName("CPUUtilization")
                                                                       .statistic("Average")
                                                                       .timestamps(singletonList(startTime))
                                                                       .values(singletonList(78.3))
                                                                       .build()))
                                       .build(),

            EcsUtilizationData.builder()
                .clusterArn("cluster1-arn")
                .clusterName("clusterName")
                .serviceArn("svc2-arn")
                .serviceName("svc2")
                .clusterId(ceCluster.getUuid())
                .settingId(settingId)
                .metricValues(singletonList(MetricValue.builder()
                                                .metricName("CPUUtilization")
                                                .statistic("Maximum")
                                                .timestamps(singletonList(endTime))
                                                .values(singletonList(66.32))
                                                .build()))
                .build(),

            EcsUtilizationData.builder()
                .clusterArn("cluster1-arn")
                .clusterName("clusterName")
                .serviceArn("svc1-arn")
                .serviceName("svc1")
                .clusterId(ceCluster.getUuid())
                .settingId(settingId)
                .metricValues(singletonList(MetricValue.builder()
                                                .metricName("MemoryUtilization")
                                                .statistic("Average")
                                                .timestamps(singletonList(startTime))
                                                .values(singletonList(33.4))
                                                .build()))
                .build());

    then(awsCloudWatchHelperService).should().getMetricData(captor.capture());
    AwsCloudWatchMetricDataRequest request = captor.getValue();
    assertThat(request.getStartTime()).isEqualTo(startTime);
    assertThat(request.getEndTime()).isEqualTo(endTime);
    assertThat(request.getRegion()).isEqualTo(region);
    // #(metrics)*#(stats)*#(clusters)*(1+#(services))
    assertThat(request.getMetricDataQueries()).hasSize(12);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldMakePartitionedAggregatedQuery() throws Exception {
    AwsCrossAccountAttributes awsCrossAccountAttributes = AwsCrossAccountAttributes.builder().build();
    String region = "region";
    String settingId = "settingId";
    CECluster ceCluster = CECluster.builder()
                              .accountId("accountId")
                              .clusterName("clusterName")
                              .region(region)
                              .infraAccountId("infraAccountId")
                              .parentAccountSettingId(settingId)
                              .build();
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
    Date startTime = Date.from(now.minus(1, ChronoUnit.HOURS));
    Date endTime = Date.from(now);
    Cluster cluster = new Cluster().withClusterName("cluster1").withClusterArn("cluster1-arn");

    List<Service> services =
        IntStream.rangeClosed(1, 200)
            .mapToObj(i -> new Service().withServiceName("svc" + i).withServiceArn("svc" + i + "-arn"))
            .collect(Collectors.toList());

    given(awsCloudWatchHelperService.getMetricData(any(AwsCloudWatchMetricDataRequest.class)))
        .willReturn(AwsCloudWatchMetricDataResponse.builder().metricDataResults(emptyList()).build());

    ecsMetricClient.getUtilizationMetrics(awsCrossAccountAttributes, startTime, endTime, cluster, services, ceCluster);

    then(awsCloudWatchHelperService).should(times(2)).getMetricData(captor.capture());
    List<AwsCloudWatchMetricDataRequest> requests = captor.getAllValues();

    // Total = 4*(200+1) = 804
    // 500 max per call
    // So 500 in 1st call, 304 in 2nd call
    assertThat(requests.get(0).getMetricDataQueries()).hasSize(500);
    assertThat(requests.get(1).getMetricDataQueries()).hasSize(304);
  }
}
