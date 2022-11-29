/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.NonFinal;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceGuardTimeSeriesAnalysisDTO {
  @NonFinal @Setter private String verificationTaskId;
  @NonFinal @Setter private Instant analysisStartTime;
  @NonFinal @Setter private Instant analysisEndTime;
  private Map<String, Double> overallMetricScores;
  private Map<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricAnalysisData;
}
