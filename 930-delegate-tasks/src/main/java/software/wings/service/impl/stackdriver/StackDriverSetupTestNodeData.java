/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.stackdriver;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.SetupTestNodeData;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by Pranjal on 11/27/2018
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StackDriverSetupTestNodeData extends SetupTestNodeData {
  private boolean isLogConfiguration;

  private Map<String, List<StackDriverMetric>> loadBalancerMetrics = new HashMap<>();

  private Set<StackDriverMetric> podMetrics = new HashSet<>();

  private String query;

  private String hostnameField;

  private String messageField;

  private List<StackDriverMetricDefinition> metricDefinitions;

  @Builder
  public StackDriverSetupTestNodeData(String appId, String settingId, String instanceName, boolean isServiceLevel,
      Instance instanceElement, String hostExpression, String workflowId, long fromTime, long toTime,
      Map<String, List<StackDriverMetric>> loadBalancerMetrics, Set<StackDriverMetric> podMetrics, String guid,
      String query, String hostnameField, String messageField, boolean isLogConfiguration,
      List<StackDriverMetricDefinition> metricDefinitions) {
    super(appId, settingId, instanceName, isServiceLevel, instanceElement, hostExpression, workflowId, guid,
        DelegateStateType.STACK_DRIVER, fromTime, toTime);
    this.loadBalancerMetrics = loadBalancerMetrics;
    this.podMetrics = podMetrics;
    this.query = query;
    this.hostnameField = hostnameField;
    this.messageField = messageField;
    this.isLogConfiguration = isLogConfiguration;
    this.metricDefinitions = metricDefinitions;
  }

  public void setMetricDefinitions(List<StackDriverMetricDefinition> metricDefinitions) {
    this.metricDefinitions = metricDefinitions;
    if (isNotEmpty(metricDefinitions)) {
      metricDefinitions.forEach(StackDriverMetricDefinition::extractJson);
    }
  }
}
