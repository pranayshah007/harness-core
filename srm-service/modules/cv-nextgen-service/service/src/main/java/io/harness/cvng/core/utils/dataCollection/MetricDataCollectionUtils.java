/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.dataCollection;

import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MetricDataCollectionUtils {
  public static boolean isMetricApplicableForDataCollection(AnalysisInfo analysisInfo, TaskType taskType) {
    switch (taskType) {
      case SLI:
        return analysisInfo.getSli().isEnabled();
      case DEPLOYMENT:
        return analysisInfo.getDeploymentVerification().isEnabled();
      case LIVE_MONITORING:
        return analysisInfo.getLiveMonitoring().isEnabled();
      default:
        throw new IllegalStateException("TaskType:" + taskType + " not supported for metric dataCollection");
    }
  }
}
