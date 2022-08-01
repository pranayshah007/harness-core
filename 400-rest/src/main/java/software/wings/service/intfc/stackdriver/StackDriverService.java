/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.stackdriver;

import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.stackdriver.StackDriverMetric;
import software.wings.service.impl.stackdriver.StackDriverSetupTestNodeData;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by Pranjal on 11/27/2018
 */
public interface StackDriverService {
  void validateMetricDefinitions(List<StackDriverMetricDefinition> metricDefinitions);

  /**
   * Api to fetch metric data for given node.
   * @param setupTestNodeData
   * @return
   */
  VerificationNodeDataSetupResponse getDataForNode(StackDriverSetupTestNodeData setupTestNodeData) throws IOException;

  /**
   * Api to fetch all the metrics support by Harness for StackDriver
   * @return
   */
  Map<String, List<StackDriverMetric>> getMetrics();

  List<String> listRegions(String settingId) throws IOException;

  Map<String, String> listForwardingRules(String settingId, String region) throws IOException;

  Boolean validateQuery(
      String accountId, String appId, String connectorId, String query, String hostNameField, String logMessageField);

  Object getLogSample(String accountId, String appId, String serverConfigId, String query, String guid);
}
