/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import java.util.List;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
public class AnalysedDeploymentTestDataNode {
  String nodeIdentifier;
  AnalysisResult analysisResult;
  AnalysisReason analysisReason;
  List<MetricValue> metricValues;
  ControlDataType controlDataType;
  String controlNodeIdentifier;
  List<String> appliedThresholds;
  List<MetricValue> controlData;
  List<MetricValue> testData;
}