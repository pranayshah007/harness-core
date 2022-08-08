/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.stackdriver;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GcpConfig;
import software.wings.service.impl.analysis.DataCollectionInfo;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by Pranjal on 11/27/2018
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class StackDriverDataCollectionInfo
    extends DataCollectionInfo implements TaskParameters, ExecutionCapabilityDemander {
  private GcpConfig gcpConfig;

  private long startTime;
  private long endTime;

  private int startMinute;
  private int collectionTime;

  private int dataCollectionMinute;
  private int initialDelayMinutes;
  private TimeSeriesMlAnalysisType timeSeriesMlAnalysisType;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private Map<String, String> hosts;
  private Map<String, List<StackDriverMetric>> loadBalancerMetrics;
  private List<StackDriverMetric> podMetrics;
  List<StackDriverMetricDefinition> timeSeriesToCollect;
  private String projectId;

  @Builder
  public StackDriverDataCollectionInfo(String accountId, String applicationId, String stateExecutionId,
      String cvConfigId, String workflowId, String workflowExecutionId, String serviceId, GcpConfig gcpConfig,
      long startTime, long endTime, int startMinute, int collectionTime, int dataCollectionMinute,
      TimeSeriesMlAnalysisType timeSeriesMlAnalysisType, List<EncryptedDataDetail> encryptedDataDetails,
      Map<String, String> hosts, Map<String, List<StackDriverMetric>> loadBalancerMetrics,
      List<StackDriverMetric> podMetrics, int initialDelayMinutes,
      List<StackDriverMetricDefinition> timeSeriesToCollect, String projectId) {
    super(accountId, applicationId, stateExecutionId, cvConfigId, workflowId, workflowExecutionId, serviceId);
    this.gcpConfig = gcpConfig;
    this.startTime = startTime;
    this.endTime = endTime;
    this.startMinute = startMinute;
    this.collectionTime = collectionTime;
    this.dataCollectionMinute = dataCollectionMinute;
    this.timeSeriesMlAnalysisType = timeSeriesMlAnalysisType;
    this.encryptedDataDetails = encryptedDataDetails;
    this.initialDelayMinutes = initialDelayMinutes;
    this.hosts = hosts;
    this.loadBalancerMetrics = loadBalancerMetrics;
    this.podMetrics = podMetrics;
    this.timeSeriesToCollect = timeSeriesToCollect;
    this.projectId = projectId;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return StackdriverUtils.fetchRequiredExecutionCapabilitiesForMetrics(getEncryptedDataDetails(), maskingEvaluator);
  }
}
