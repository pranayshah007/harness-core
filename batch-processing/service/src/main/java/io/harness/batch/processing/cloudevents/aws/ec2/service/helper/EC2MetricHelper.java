/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ec2.service.helper;

import static com.amazonaws.services.cloudwatch.model.Statistic.Average;
import static com.amazonaws.services.cloudwatch.model.Statistic.Maximum;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

import io.harness.batch.processing.cloudevents.aws.ec2.service.response.Ec2UtilzationData;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.Ec2UtilzationData.Ec2UtilzationDataBuilder;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.MetricValue;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsCloudWatchHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.request.AwsCloudWatchMetricDataRequest;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.MetricDataQuery;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import com.amazonaws.services.cloudwatch.model.MetricStat;
import com.amazonaws.services.cloudwatch.model.Statistic;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ParametersAreNonnullByDefault
public class EC2MetricHelper {
  private static final String EC2_NAMESPACE = "AWS/EC2";
  private static final String CPU_UTILIZATION = "CPUUtilization";
  private static final String MEMORY_UTILIZATION = "MemoryUtilization";
  private static final String INSTANCE_ID = "InstanceId";

  private static final int PERIOD = (int) Duration.ofDays(1).getSeconds();

  private final AwsCloudWatchHelperService awsCloudWatchHelperService;

  @Autowired
  public EC2MetricHelper(AwsCloudWatchHelperService awsCloudWatchHelperService) {
    this.awsCloudWatchHelperService = awsCloudWatchHelperService;
  }

  private Metric metricFor(String metricName, String instanceId) {
    return new Metric()
        .withNamespace(EC2_NAMESPACE)
        .withMetricName(metricName)
        .withDimensions(new Dimension().withName(INSTANCE_ID).withValue(instanceId));
  }

  // Generate a unique-per-request id to individual query in request with individual result in response.
  private String generateId(String metricName, String stat, String instanceId) {
    return "id_" + md5Hex(("m_" + metricName + "s_" + stat + "i_" + instanceId).getBytes(UTF_8));
  }

  public List<Ec2UtilzationData> getUtilizationMetrics(AwsCrossAccountAttributes awsCrossAccountAttributes,
      Date startTime, Date endTime, List<AWSEC2Details> instanceDetails) {
    final Map<String, MetricDataResult> metricDataResultMap = new HashMap<>();
    final Map<String, List<AWSEC2Details>> regionBasedInstanceMap = new HashMap<>();
    Set<AWSEC2Details> uniqueInstances = new HashSet<>();
    for (AWSEC2Details instance : instanceDetails) {
      uniqueInstances.add(instance);
    }

    uniqueInstances.forEach(instance -> {
      regionBasedInstanceMap.putIfAbsent(instance.getRegion(), new ArrayList<>());
      regionBasedInstanceMap.get(instance.getRegion()).add(instance);
    });

    if (!regionBasedInstanceMap.isEmpty()) {
      regionBasedInstanceMap.entrySet().forEach(entry -> {
        List<AWSEC2Details> instanceList = entry.getValue();
        List<MetricDataQuery> aggregatedQuery = new ArrayList<>();
        instanceList.forEach(instance -> {
          for (Statistic stat : Arrays.asList(Average, Maximum)) {
            for (String metricName : Arrays.asList(CPU_UTILIZATION, MEMORY_UTILIZATION)) {
              // instance level metrics

              Metric clusterMetric = metricFor(metricName, instance.getInstanceId());
              aggregatedQuery.add(
                  new MetricDataQuery()
                      .withId(generateId(metricName, stat.toString(), instance.getInstanceId()))
                      .withMetricStat(
                          new MetricStat().withPeriod(PERIOD).withStat(stat.toString()).withMetric(clusterMetric)));
            }
          }
        });
        metricDataResultMap.putAll(awsCloudWatchHelperService
                                       .getMetricData(AwsCloudWatchMetricDataRequest.builder()
                                                          .region(entry.getKey())
                                                          .awsCrossAccountAttributes(awsCrossAccountAttributes)
                                                          .startTime(startTime)
                                                          .endTime(endTime)
                                                          .metricDataQueries(aggregatedQuery)
                                                          .build())
                                       .getMetricDataResults()
                                       .stream()
                                       .collect(Collectors.toMap(MetricDataResult::getId, Function.identity())));
      });
    }

    log.debug("metricDataResultMap = {}", metricDataResultMap);
    List<Ec2UtilzationData> utilizationMetrics = new ArrayList<>();
    for (AWSEC2Details instance : uniqueInstances) {
      utilizationMetrics.add(extractMetricResult(metricDataResultMap, instance.getInstanceId()));
    }
    return utilizationMetrics;
  }

  private Ec2UtilzationData extractMetricResult(Map<String, MetricDataResult> metricDataResultMap, String instanceId) {
    Ec2UtilzationDataBuilder metrics = Ec2UtilzationData.builder().instanceId(instanceId);

    List<io.harness.batch.processing.cloudevents.aws.ec2.service.response.MetricValue> metricValues = new ArrayList<>();
    for (Statistic stat : Arrays.asList(Average, Maximum)) {
      for (String metricName : Arrays.asList(CPU_UTILIZATION, MEMORY_UTILIZATION)) {
        MetricDataResult mdr = metricDataResultMap.get(generateId(metricName, stat.toString(), instanceId));
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
