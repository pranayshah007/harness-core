/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.cloudwatch;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Created by rsingh on 5/18/17.
 */
@Data
@Builder
public class CloudWatchDataCollectionInfo implements TaskParameters, ExecutionCapabilityDemander {
  private AwsConfig awsConfig;
  private String applicationId;
  private String stateExecutionId;
  private String workflowId;
  private String workflowExecutionId;
  private String serviceId;
  private String cvConfigId;
  private long startTime;
  private int collectionTime;
  private int dataCollectionMinute;
  private AnalysisComparisonStrategy analysisComparisonStrategy;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private String region;

  @Builder.Default private Map<String, String> hosts = new HashMap<>();
  @Builder.Default private Map<String, List<CloudWatchMetric>> lambdaFunctionNames = new HashMap<>();
  @Builder.Default private Map<String, List<CloudWatchMetric>> loadBalancerMetrics = new HashMap<>();
  @Builder.Default private List<CloudWatchMetric> ec2Metrics = new ArrayList<>();
  @Builder.Default private Map<String, List<CloudWatchMetric>> metricsByECSClusterName = new HashMap<>();

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    // TODO: CAPABILITY
    return CapabilityHelper.generateDelegateCapabilities(awsConfig, encryptedDataDetails, maskingEvaluator);
  }
}
