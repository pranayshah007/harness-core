/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.analysisinfo;

import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class AnalysisInfoUtility {
  public static boolean anySLIEnabled(Collection<? extends AnalysisInfo> analysisInfos) {
    return CollectionUtils.emptyIfNull(analysisInfos)
        .stream()
        .anyMatch(analysisInfo -> analysisInfo.getSli().isEnabled());
  }

  public static boolean anyDeploymentVerificationEnabled(Collection<? extends AnalysisInfo> analysisInfos) {
    return CollectionUtils.emptyIfNull(analysisInfos)
        .stream()
        .anyMatch(analysisInfo -> analysisInfo.getDeploymentVerification().isEnabled());
  }

  public static boolean anyLiveMonitoringEnabled(Collection<? extends AnalysisInfo> analysisInfos) {
    return CollectionUtils.emptyIfNull(analysisInfos)
        .stream()
        .anyMatch(analysisInfo -> analysisInfo.getDeploymentVerification().isEnabled());
  }

  public static <I extends AnalysisInfo> List<I> filterApplicableForDataCollection(
      Collection<I> analysisInfos, TaskType taskType) {
    return CollectionUtils.emptyIfNull(analysisInfos)
        .stream()
        .filter(analysisInfo -> analysisInfo.isMetricApplicableForDataCollection(taskType))
        .collect(Collectors.toList());
  }
}
