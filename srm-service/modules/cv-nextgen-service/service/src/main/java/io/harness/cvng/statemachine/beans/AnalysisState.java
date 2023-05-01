/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.beans;

import static io.harness.cvng.analysis.CVAnalysisConstants.MAX_RETRIES;

import lombok.Data;

@Data
public abstract class AnalysisState {
  private AnalysisInput inputs;
  private AnalysisStatus status;
  private int retryCount;
  public abstract StateType getType();

  protected int getMaxRetry() {
    return MAX_RETRIES;
  }

  public enum StateType {
    HOST_SAMPLING_STATE,
    CANARY_TIME_SERIES,
    DEPLOYMENT_LOG_ANALYSIS,
    SERVICE_GUARD_LOG_ANALYSIS,
    SERVICE_GUARD_TIME_SERIES,
    TEST_TIME_SERIES,
    DEPLOYMENT_LOG_CLUSTER,
    PRE_DEPLOYMENT_LOG_CLUSTER,
    SERVICE_GUARD_LOG_CLUSTER,
    SERVICE_GUARD_TREND_ANALYSIS,
    SLI_METRIC_ANALYSIS,
    DEPLOYMENT_TIME_SERIES_ANALYSIS_STATE,
    COMPOSOITE_SLO_METRIC_ANALYSIS,
    DEPLOYMENT_LOG_HOST_SAMPLING_STATE,
    DEPLOYMENT_METRIC_HOST_SAMPLING_STATE,
    DEPLOYMENT_LOG_FEEDBACK_STATE,

    COMPOSOITE_SLO_RESTORE_METRIC_ANALYSIS

  }
}
