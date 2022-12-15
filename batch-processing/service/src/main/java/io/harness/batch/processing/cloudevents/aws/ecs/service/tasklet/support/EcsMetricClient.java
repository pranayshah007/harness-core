/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support;

import static com.amazonaws.services.cloudwatch.model.Statistic.Average;
import static com.amazonaws.services.cloudwatch.model.Statistic.Maximum;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsCloudWatchHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.request.AwsCloudWatchMetricDataRequest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.EcsUtilizationData;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.EcsUtilizationData.EcsUtilizationDataBuilder;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.MetricValue;
import io.harness.ccm.commons.entities.billing.CECluster;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.MetricDataQuery;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import com.amazonaws.services.cloudwatch.model.MetricStat;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.Service;
import com.google.common.collect.Iterables;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ParametersAreNonnullByDefault
public class EcsMetricClient {
  private static final String CPU_UTILIZATION = "CPUUtilization";
  private static final String MEMORY_UTILIZATION = "MemoryUtilization";
  private static final String SERVICE = "ServiceName";
  private static final String CLUSTER = "ClusterName";

  private static final int PERIOD = (int) Duration.ofHours(1).getSeconds();

  private final AwsCloudWatchHelperService awsCloudWatchHelperService;

  @Autowired
  public EcsMetricClient(AwsCloudWatchHelperService awsCloudWatchHelperService) {
    this.awsCloudWatchHelperService = awsCloudWatchHelperService;
  }

  private Metric metricFor(String metricName, Cluster cluster, @Nullable Service service) {
    Metric metric = new Metric()
                        .withNamespace("AWS/ECS")
                        .withMetricName(metricName)
                        .withDimensions(new Dimension().withName(CLUSTER).withValue(cluster.getClusterName()));
    if (service != null) {
      metric = metric.withDimensions(new Dimension().withName(SERVICE).withValue(service.getServiceName()));
    }
    return metric;
  }

  // Generate a unique-per-request id to individual query in request with individual result in response.
  private String generateId(String metricName, String stat, Cluster cluster, @Nullable Service service) {
    String serviceName = service == null ? "" : service.getServiceName();
    String clusterName = cluster.getClusterName();
    return "id_" + md5Hex(("m_" + metricName + "s_" + stat + "c_" + clusterName + "s_" + serviceName).getBytes(UTF_8));
  }

  public List<EcsUtilizationData> getUtilizationMetrics(AwsCrossAccountAttributes awsCrossAccountAttributes,
      Date startTime, Date endTime, Cluster cluster, List<Service> services, CECluster ceCluster) {
    // Aggregate all the individual metric queries we need into a single query.
    List<MetricDataQuery> aggregatedQuery = new ArrayList<>();
    for (Statistic stat : Arrays.asList(Average, Maximum)) {
      for (String metricName : Arrays.asList(CPU_UTILIZATION, MEMORY_UTILIZATION)) {
        // Cluster level metrics
        Metric clusterMetric = metricFor(metricName, cluster, null);
        aggregatedQuery.add(
            new MetricDataQuery()
                .withId(generateId(metricName, stat.toString(), cluster, null))
                .withMetricStat(
                    new MetricStat().withPeriod(PERIOD).withStat(stat.toString()).withMetric(clusterMetric)));
        for (Service service : services) {
          Metric serviceMetric = metricFor(metricName, cluster, service);
          aggregatedQuery.add(
              new MetricDataQuery()
                  .withId(generateId(metricName, stat.toString(), cluster, service))
                  .withMetricStat(
                      new MetricStat().withPeriod(PERIOD).withStat(stat.toString()).withMetric(serviceMetric)));
        }
      }
    }
    final Map<String, MetricDataResult> metricDataResultMap = new HashMap<>();
    Iterables.partition(aggregatedQuery, AwsCloudWatchHelperService.MAX_QUERIES_PER_CALL).forEach(part -> {
      metricDataResultMap.putAll(awsCloudWatchHelperService
                                     .getMetricData(AwsCloudWatchMetricDataRequest.builder()
                                                        .region(ceCluster.getRegion())
                                                        .awsCrossAccountAttributes(awsCrossAccountAttributes)
                                                        .startTime(startTime)
                                                        .endTime(endTime)
                                                        .metricDataQueries(part)
                                                        .build())
                                     .getMetricDataResults()
                                     .stream()
                                     .collect(Collectors.toMap(MetricDataResult::getId, Function.identity())));
    });

    List<EcsUtilizationData> utilizationMetrics = new ArrayList<>();
    // Add service level metrics
    for (Service service : services) {
      utilizationMetrics.add(extractMetricResult(
          metricDataResultMap, cluster, service, ceCluster.getUuid(), ceCluster.getParentAccountSettingId()));
    }

    // Add cluster level metrics
    utilizationMetrics.add(extractMetricResult(
        metricDataResultMap, cluster, null, ceCluster.getUuid(), ceCluster.getParentAccountSettingId()));
    return utilizationMetrics;
  }

  private EcsUtilizationData extractMetricResult(Map<String, MetricDataResult> metricDataResultMap, Cluster cluster,
      @Nullable Service service, String clusterId, String settingId) {
    EcsUtilizationDataBuilder metrics = EcsUtilizationData.builder()
                                            .clusterArn(cluster.getClusterArn())
                                            .clusterName(cluster.getClusterName())
                                            .clusterId(clusterId)
                                            .settingId(settingId);
    if (service != null) {
      metrics.serviceArn(service.getServiceArn()).serviceName(service.getServiceName());
    }
    List<MetricValue> metricValues = new ArrayList<>();
    for (Statistic stat : Arrays.asList(Average, Maximum)) {
      for (String metricName : Arrays.asList(CPU_UTILIZATION, MEMORY_UTILIZATION)) {
        MetricDataResult mdr = metricDataResultMap.get(generateId(metricName, stat.toString(), cluster, service));
        if (mdr != null) {
          metricValues.add(MetricValue.builder()
                               .metricName(metricName)
                               .statistic(stat.toString())
                               .timestamps(mdr.getTimestamps())
                               .values(mdr.getValues())
                               .build());
        }
      }
    }
    metrics.metricValues(metricValues);
    return metrics.build();
  }
}
