/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RequestBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIAnalyserService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;

public class SLIDataProcessorServiceImpl implements SLIDataProcessorService {
  @Inject private Map<SLIMetricType, SLIAnalyserService> sliAnalyserServiceMapBinder;

  @Override
  public List<SLIAnalyseResponse> process(Map<String, List<SLIAnalyseRequest>> sliAnalyseRequestMap,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, Instant startTime, Instant endTime) {
    List<SLIAnalyseResponse> sliAnalyseResponseList = new ArrayList<>();
    Pair<Long, Long> runningCount = Pair.of(0L, 0L);
    Map<Instant, Map<String, Double>> sliProcessRequestMap = new HashMap<>();
    for (Map.Entry<String, List<SLIAnalyseRequest>> entry : sliAnalyseRequestMap.entrySet()) {
      for (SLIAnalyseRequest sliAnalyseRequest : entry.getValue()) {
        Map<String, Double> analysisRequest =
            sliProcessRequestMap.getOrDefault(sliAnalyseRequest.getTimeStamp(), new HashMap<>());
        analysisRequest.put(entry.getKey(), sliAnalyseRequest.getMetricValue());
        sliProcessRequestMap.put(sliAnalyseRequest.getTimeStamp(), analysisRequest);
      }
    }
    SLIState sliState;
    for (Instant i = startTime; i.isBefore(endTime); i = i.plusSeconds(60)) {
      Map<String, Double> singleSLIAnalysisRequest = sliProcessRequestMap.get(i);
      Pair<Long, Long> countValues = Pair.of(0L, 0L);
      if (sliProcessRequestMap.containsKey(i)) {
        sliState = getState(serviceLevelIndicatorDTO, singleSLIAnalysisRequest);
      } else {
        sliState = SLIState.NO_DATA;
      }
      countValues = getCountValues(serviceLevelIndicatorDTO, singleSLIAnalysisRequest, sliState);
      runningCount = getRunningCount(runningCount, countValues.getKey(), countValues.getValue());
      sliAnalyseResponseList.add(SLIAnalyseResponse.builder()
                                     .sliState(sliState)
                                     .timeStamp(i)
                                     .runningGoodCount(runningCount.getKey())
                                     .runningBadCount(runningCount.getValue())
                                     .goodEventCount(countValues.getKey())
                                     .badEventCount(countValues.getValue())
                                     .build());
    }
    return sliAnalyseResponseList;
  }

  private SLIState getState(
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, Map<String, Double> singleSLIAnalysisRequest) {
    if (serviceLevelIndicatorDTO.getType() == SLIEvaluationType.WINDOW) {
      WindowBasedServiceLevelIndicatorSpec windowBasedServiceLevelIndicatorSpec =
          (WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec();
      return sliAnalyserServiceMapBinder.get(windowBasedServiceLevelIndicatorSpec.getSpec().getType())
          .analyse(singleSLIAnalysisRequest, windowBasedServiceLevelIndicatorSpec.getSpec());
    } else if (serviceLevelIndicatorDTO.getType() == SLIEvaluationType.REQUEST) {
      return SLIState.GOOD;
    } else {
      throw new InvalidArgumentsException(
          String.format("ServiceLevelIndicator type %s does not exist", serviceLevelIndicatorDTO.getType()));
    }
  }

  Pair<Long, Long> getCountValues(
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, Map<String, Double> sliAnalysisRequest, SLIState sliState) {
    long goodCountValue = 0;
    long badCountValue = 0;
    if (serviceLevelIndicatorDTO.getType() == SLIEvaluationType.WINDOW) {
      if (sliState == SLIState.GOOD) {
        goodCountValue = 1;
      } else if (sliState == SLIState.BAD) {
        badCountValue = 1;
      }
    } else if (serviceLevelIndicatorDTO.getType() == SLIEvaluationType.REQUEST && sliState != SLIState.NO_DATA) {
      RequestBasedServiceLevelIndicatorSpec sliSpec =
          (RequestBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec();
      Double metricValue1 = sliAnalysisRequest.get(sliSpec.getMetric1());
      Double metricValue2 = sliAnalysisRequest.get(sliSpec.getMetric2());

      if (Objects.isNull(metricValue1) || Objects.isNull(metricValue2) || metricValue2 == 0
          || metricValue2 < metricValue1) {
        return Pair.of(goodCountValue, badCountValue);
      }

      goodCountValue = sliSpec.getEventType() == RatioSLIMetricEventType.GOOD
          ? metricValue1.longValue()
          : metricValue2.longValue() - metricValue1.longValue();
      badCountValue = metricValue2.longValue() - goodCountValue;
    }
    return Pair.of(goodCountValue, badCountValue);
  }

  Pair<Long, Long> getRunningCount(Pair<Long, Long> runningCount, Long goodCountValue, Long badCountValue) {
    long runningGoodCount = runningCount.getKey();
    long runningBadCount = runningCount.getValue();
    runningGoodCount += goodCountValue;
    runningBadCount += badCountValue;
    return Pair.of(runningGoodCount, runningBadCount);
  }
}
