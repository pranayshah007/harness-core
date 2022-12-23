/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import io.harness.cvng.beans.activity.ActivityVerificationStatus;

import java.util.List;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
public class VerificationOverview {
  VerificationSpec spec;
  AppliedDeploymentAnalysisType appliedDeploymentAnalysisType;
  ActivityVerificationStatus verificationStatus;
  int verificationProgressPercentage;
  long verificationStartTimestamp;
  long verificationEndTimestamp;
  List<AnalysedNodeOverview> testNodes;
  List<AnalysedNodeOverview> controlNodes;
  MetricsAnalysisOverview metricsAnalysis;
  LogsAnalysisOverview logClusters;
  ErrorsAnalysisOverview errorClusters;
}
