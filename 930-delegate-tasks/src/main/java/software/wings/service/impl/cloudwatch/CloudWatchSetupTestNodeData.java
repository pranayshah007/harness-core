/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.cloudwatch;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.SetupTestNodeData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by Pranjal on 09/04/2018
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CloudWatchSetupTestNodeData extends SetupTestNodeData {
  private String region;
  private String hostName;
  private Map<String, List<CloudWatchMetric>> loadBalancerMetricsByLBName;
  private Map<String, List<CloudWatchMetric>> ecsMetrics;
  private Map<String, List<CloudWatchMetric>> lambdaFunctionsMetrics;
  private List<CloudWatchMetric> ec2Metrics;

  @Builder
  public CloudWatchSetupTestNodeData(String appId, String settingId, String instanceName, boolean isServiceLevel,
      Instance instanceElement, String hostExpression, String workflowId, long fromTime, long toTime, String region,
      String hostName, Map<String, List<CloudWatchMetric>> loadBalancerMetricsByLBName,
      List<CloudWatchMetric> ec2Metrics, String guid, Map<String, List<CloudWatchMetric>> ecsMetrics,
      Map<String, List<CloudWatchMetric>> lambdaFunctionsMetrics) {
    super(appId, settingId, instanceName, isServiceLevel, instanceElement, hostExpression, workflowId, guid,
        DelegateStateType.CLOUD_WATCH, fromTime, toTime);
    this.region = region;
    this.hostName = hostName;
    this.loadBalancerMetricsByLBName = loadBalancerMetricsByLBName;
    this.ec2Metrics = ec2Metrics;
    this.ecsMetrics = ecsMetrics;
    this.lambdaFunctionsMetrics = lambdaFunctionsMetrics;
  }
}
