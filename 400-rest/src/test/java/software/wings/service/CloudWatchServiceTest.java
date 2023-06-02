/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.METRIC_DIMENSION;
import static software.wings.utils.WingsTestConstants.METRIC_NAME;
import static software.wings.utils.WingsTestConstants.NAMESPACE;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.metrics.MetricType;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.AwsInfrastructureProvider;
import software.wings.service.impl.CloudWatchServiceImpl;
import software.wings.service.impl.cloudwatch.AwsNameSpace;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.cloudwatch.CloudWatchSetupTestNodeData;
import software.wings.service.intfc.CloudWatchService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.cloudwatch.CloudWatchDelegateService;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.costandusagereport.model.AWSRegion;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by anubhaw on 12/15/16.
 */
public class CloudWatchServiceTest extends WingsBaseTest {
  @Mock private SettingsService settingsService;
  @Mock private AwsHelperService awsHelperService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private AwsInfrastructureProvider awsInfrastructureProvider;

  @Inject private CloudWatchDelegateService cloudWatchDelegateService;

  @Inject @InjectMocks private CloudWatchService cloudWatchService;

  @Before
  public void setUp() throws Exception {
    when(settingsService.get(SETTING_ID))
        .thenReturn(
            aSettingAttribute()
                .withValue(AwsConfig.builder().accessKey(ACCESS_KEY.toCharArray()).secretKey(SECRET_KEY).build())
                .build());
    ListMetricsResult listMetricsResult = new ListMetricsResult().withMetrics(
        asList(new Metric()
                   .withNamespace(NAMESPACE)
                   .withMetricName(METRIC_NAME)
                   .withDimensions(asList(new Dimension().withName(METRIC_DIMENSION)))));
    when(awsHelperService.getCloudWatchMetrics(any(AwsConfig.class), any(), anyString()))
        .thenReturn(listMetricsResult.getMetrics());
    when(awsHelperService.getCloudWatchMetrics(any(AwsConfig.class), any(), anyString(), any(ListMetricsRequest.class)))
        .thenReturn(listMetricsResult.getMetrics());
    when(awsInfrastructureProvider.listClassicLoadBalancers(any(), anyString(), any()))
        .thenReturn(Arrays.asList("LB1", "LB2"));
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListNamespaces() {
    List<String> namespaces = cloudWatchService.listNamespaces(SETTING_ID, "us-east-1");
    assertThat(namespaces).hasSize(1).containsExactly(NAMESPACE);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListMetrics() {
    List<String> namespaces = cloudWatchService.listMetrics(SETTING_ID, "us-east-1", NAMESPACE);
    assertThat(namespaces).hasSize(1).containsExactly(METRIC_NAME);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListDimensions() {
    List<String> namespaces = cloudWatchService.listDimensions(SETTING_ID, "us-east-1", NAMESPACE, METRIC_NAME);
    assertThat(namespaces).hasSize(1).containsExactly(METRIC_DIMENSION);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testFetchMetricsAllMetrics() {
    Map<AwsNameSpace, List<CloudWatchMetric>> cloudwatchMetrics = CloudWatchServiceImpl.fetchMetrics();
    assertThat(cloudwatchMetrics.keySet()).hasSize(4);
    assertThat(cloudwatchMetrics.get(AwsNameSpace.LAMBDA)).hasSize(4);
    assertThat(cloudwatchMetrics.get(AwsNameSpace.ECS)).hasSize(4);
    assertThat(cloudwatchMetrics.get(AwsNameSpace.EC2)).hasSize(9);
    assertThat(cloudwatchMetrics.get(AwsNameSpace.ELB)).hasSize(13);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testListLoadBalancers() {
    Set<String> loadBalancerNames = cloudWatchService.getLoadBalancerNames(SETTING_ID, "us-east-1");
    verify(awsInfrastructureProvider, times(1)).listClassicLoadBalancers(any(), anyString(), any());
    verify(awsInfrastructureProvider, times(1)).listElasticBalancers(any(), anyString(), any());
    verify(awsInfrastructureProvider, never()).listLoadBalancers(any(), any(), any());
    assertThat(loadBalancerNames.size()).isEqualTo(2);
    assertThat(loadBalancerNames).containsExactlyInAnyOrder("LB1", "LB2");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchSpecificMetrics() {
    CloudWatchCVServiceConfiguration cvServiceConfiguration = CloudWatchCVServiceConfiguration.builder().build();
    Map<String, List<CloudWatchMetric>> ecsList = new HashMap<>();
    ecsList.put("testCluster", Arrays.asList(CloudWatchMetric.builder().metricName("CPUUtilization").build()));
    cvServiceConfiguration.setEcsMetrics(ecsList);
    Map<String, List<CloudWatchMetric>> elbList = new HashMap<>();
    elbList.put("testLB", Arrays.asList(CloudWatchMetric.builder().metricName("HTTPCode_Backend_2XX").build()));
    cvServiceConfiguration.setLoadBalancerMetrics(elbList);

    Map<AwsNameSpace, List<CloudWatchMetric>> cloudwatchMetrics =
        CloudWatchServiceImpl.fetchMetrics(cvServiceConfiguration);
    assertThat(cloudwatchMetrics.keySet()).hasSize(2);
    assertThat(cloudwatchMetrics.containsKey(AwsNameSpace.LAMBDA)).isFalse();
    assertThat(cloudwatchMetrics.containsKey(AwsNameSpace.EC2)).isFalse();
    assertThat(cloudwatchMetrics.get(AwsNameSpace.ECS)).hasSize(1);
    assertThat("CPUUtilization").isEqualTo(cloudwatchMetrics.get(AwsNameSpace.ECS).get(0).getMetricName());
    assertThat(cloudwatchMetrics.get(AwsNameSpace.ELB)).hasSize(1);
    assertThat("HTTPCode_Backend_2XX").isEqualTo(cloudwatchMetrics.get(AwsNameSpace.ELB).get(0).getMetricName());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchSpecificMetricsNone() {
    CloudWatchCVServiceConfiguration cvServiceConfiguration = CloudWatchCVServiceConfiguration.builder().build();

    Map<AwsNameSpace, List<CloudWatchMetric>> cloudwatchMetrics =
        CloudWatchServiceImpl.fetchMetrics(cvServiceConfiguration);
    assertThat(cloudwatchMetrics.keySet()).hasSize(0);
    assertThat(cloudwatchMetrics.containsKey(AwsNameSpace.LAMBDA)).isFalse();
    assertThat(cloudwatchMetrics.containsKey(AwsNameSpace.EC2)).isFalse();
    assertThat(cloudwatchMetrics.containsKey(AwsNameSpace.ECS)).isFalse();
    assertThat(cloudwatchMetrics.containsKey(AwsNameSpace.ELB)).isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSetStatisticsAndUnit() {
    List<CloudWatchMetric> cloudWatchMetrics = Lists.newArrayList(CloudWatchMetric.builder()
                                                                      .metricName("Latency")
                                                                      .metricType(generateUuid())
                                                                      .unit(StandardUnit.Milliseconds)
                                                                      .build(),
        CloudWatchMetric.builder().metricName("RequestCount").metricType(generateUuid()).statistics("custom").build());

    cloudWatchService.setStatisticsAndUnit(AwsNameSpace.ELB, cloudWatchMetrics);

    assertThat(cloudWatchMetrics.get(0).getStatistics()).isEqualTo("Average");
    assertThat(cloudWatchMetrics.get(0).getUnit()).isEqualTo(StandardUnit.Milliseconds);
    assertThat(cloudWatchMetrics.get(1).getStatistics()).isEqualTo("Sum");
    assertThat(cloudWatchMetrics.get(1).getUnit()).isEqualTo(StandardUnit.Count);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchCloudWatchLambdaMetricType() {
    Map<AwsNameSpace, List<CloudWatchMetric>> cloudwatchMetrics = CloudWatchServiceImpl.fetchMetrics();
    assertThat(cloudwatchMetrics.keySet()).hasSize(4);
    assertThat(cloudwatchMetrics.get(AwsNameSpace.LAMBDA)).hasSize(4);
    List<CloudWatchMetric> lambdaMetrics = cloudwatchMetrics.get(AwsNameSpace.LAMBDA);
    boolean hasThroughput = false, hasRespTime = false, hasErrors = false;
    int errorsCount = 0;

    for (CloudWatchMetric metric : lambdaMetrics) {
      if (MetricType.RESP_TIME.name().equals(metric.getMetricType())) {
        hasRespTime = true;
      }
      if (MetricType.ERROR.name().equals(metric.getMetricType())) {
        hasErrors = true;
        errorsCount++;
      }
      if (MetricType.THROUGHPUT.name().equals(metric.getMetricType())) {
        hasThroughput = true;
      }
    }
    assertThat(hasThroughput).isTrue();
    assertThat(hasRespTime).isTrue();
    assertThat(hasErrors).isTrue();
    assertThat(errorsCount).isEqualTo(2);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMetricsWithDataForNode_timeInMs() {
    final CloudWatchSetupTestNodeData cloudWatchSetupTestNodeData = new CloudWatchSetupTestNodeData();
    cloudWatchSetupTestNodeData.setSettingId(SETTING_ID);
    cloudWatchSetupTestNodeData.setRegion(AWSRegion.UsEast1.name());
    final CloudWatchSetupTestNodeData spy = spy(cloudWatchSetupTestNodeData);
    cloudWatchDelegateService.getMetricsWithDataForNode(
        AwsConfig.builder().accessKey(ACCESS_KEY.toCharArray()).secretKey(SECRET_KEY).build(), Lists.newArrayList(),
        spy, ThirdPartyApiCallLog.createApiCallLog(generateUuid(), generateUuid()), generateUuid());
    verify(spy).setFromTime(TimeUnit.SECONDS.toMillis(cloudWatchSetupTestNodeData.getFromTime()));
    verify(spy).setToTime(TimeUnit.SECONDS.toMillis(cloudWatchSetupTestNodeData.getToTime()));
  }
}
