/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.SLIMetricSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIAnalyserService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public class SLIDataProcessorServiceImpl implements SLIDataProcessorService {
  @Inject private Map<SLIMetricType, SLIAnalyserService> sliAnalyserServiceMapBinder;

  @Override
  public List<SLIAnalyseResponse> process(Map<String, List<SLIAnalyseRequest>> sliAnalyseRequestMap,
      SLIMetricSpec sliSpec, Instant startTime, Instant endTime) {
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
      if (sliProcessRequestMap.containsKey(i)) {
        Map<String, Double> singleSLIAnalysisRequest = sliProcessRequestMap.get(i);
        sliState = sliAnalyserServiceMapBinder.get(sliSpec.getType()).analyse(singleSLIAnalysisRequest, sliSpec);
      } else {
        sliState = SLIState.NO_DATA;
      }
      runningCount = getRunningCount(runningCount, sliState);
      sliAnalyseResponseList.add(SLIAnalyseResponse.builder()
                                     .sliState(sliState)
                                     .timeStamp(i)
                                     .runningGoodCount(runningCount.getKey())
                                     .runningBadCount(runningCount.getValue())
                                     .build());
    }
    return sliAnalyseResponseList;
  }

  Pair<Long, Long> getRunningCount(Pair<Long, Long> runningCount, SLIState sliState) {
    long runningGoodCount = runningCount.getKey();
    long runningBadCount = runningCount.getValue();
    if (SLIState.GOOD.equals(sliState)) {
      runningGoodCount++;
    } else if (SLIState.BAD.equals(sliState)) {
      runningBadCount++;
    }
    return Pair.of(runningGoodCount, runningBadCount);
  }
}
