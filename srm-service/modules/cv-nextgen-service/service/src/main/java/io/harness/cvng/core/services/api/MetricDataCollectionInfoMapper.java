/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.TimeSeriesDataCollectionInfo;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
public abstract class MetricDataCollectionInfoMapper<R extends TimeSeriesDataCollectionInfo, T extends MetricCVConfig>
    implements DataCollectionInfoMapper<R, T>, DataCollectionSLIInfoMapper<R, T> {
  protected abstract R toDataCollectionInfo(T cvConfigWithFilteredMetricInfo);

  public R toDataCollectionInfo(T cvConfig, TaskType taskType) {
    cvConfig.setMetricInfos(AnalysisInfoUtility.filterApplicableForDataCollection(cvConfig.getMetricInfos(), taskType));
    return toDataCollectionInfo(cvConfig);
  }

  public R toDataCollectionInfo(List<T> cvConfigs, ServiceLevelIndicator serviceLevelIndicator) {
    Optional<T> baseCvConfig = cvConfigs.stream().filter(CVConfig::isSLIEnabled).findAny();
    List<AnalysisInfo> analysisInfos = new ArrayList<>();
    cvConfigs.stream()
        .flatMap(cvConfig -> CollectionUtils.emptyIfNull(cvConfig.getMetricInfos()).stream())
        .filter(analysisInfo
            -> serviceLevelIndicator.getMetricNames().contains(((AnalysisInfo) analysisInfo).getIdentifier()))
        .forEach(analysisInfo -> analysisInfos.add((AnalysisInfo) analysisInfo));
    if (baseCvConfig.isPresent()) {
      baseCvConfig.get().setMetricInfos(analysisInfos);
      return toDataCollectionInfo(baseCvConfig.get());
    } else {
      log.warn("No SLI Enabled CV Configs found. SLI uuid {}", serviceLevelIndicator.getUuid());
      return null;
    }
  }

  public R toDataCollectionInfo(List<T> cvConfigs, List<String> metricIdentifiers) {
    T baseCvConfig = cvConfigs.stream()
                         .filter(cvConfig -> cvConfig.isSLIEnabled())
                         .findAny()
                         .orElseThrow(() -> new IllegalStateException("No SLI Enabled CV Configs found"));
    List<AnalysisInfo> analysisInfos = new ArrayList<>();
    cvConfigs.stream()
        .flatMap(cvConfig -> CollectionUtils.emptyIfNull(cvConfig.getMetricInfos()).stream())
        .filter(analysisInfo -> metricIdentifiers.contains(((AnalysisInfo) analysisInfo).getIdentifier()))
        .forEach(analysisInfo -> analysisInfos.add((AnalysisInfo) analysisInfo));
    Preconditions.checkState(analysisInfos.size() == metricIdentifiers.size(), "Some of the metrics are not present");

    baseCvConfig.setMetricInfos(analysisInfos);
    return toDataCollectionInfo(baseCvConfig);
  }
}
