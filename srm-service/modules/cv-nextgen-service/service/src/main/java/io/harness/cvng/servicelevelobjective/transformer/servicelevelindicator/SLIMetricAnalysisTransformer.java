/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SLIMetricAnalysisTransformer {
  public Map<String, List<SLIAnalyseRequest>> getSLIAnalyseRequest(List<TimeSeriesRecordDTO> timeSeriesRecordDTOList) {
    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequestMap = new HashMap<>();
    for (TimeSeriesRecordDTO timeSeriesRecordDTO : timeSeriesRecordDTOList) {
      List<SLIAnalyseRequest> sliAnalyseRequests =
          sliAnalyseRequestMap.getOrDefault(timeSeriesRecordDTO.getMetricIdentifier(), new ArrayList<>());
      sliAnalyseRequests.add(getSLIAnalyseRequest(timeSeriesRecordDTO));
      sliAnalyseRequestMap.put(timeSeriesRecordDTO.getMetricIdentifier(), sliAnalyseRequests);
    }
    return sliAnalyseRequestMap;
  }

  public List<SLIRecordParam> getSLIAnalyseResponse(List<SLIAnalyseResponse> sliAnalyseResponseList) {
    return sliAnalyseResponseList.stream()
        .map(sliAnalyseResponse
            -> SLIRecordParam.builder()
                   .sliState(sliAnalyseResponse.getSliState())
                   .timeStamp(sliAnalyseResponse.getTimeStamp())
                   .build())
        .collect(Collectors.toList());
  }

  private SLIAnalyseRequest getSLIAnalyseRequest(TimeSeriesRecordDTO timeSeriesRecordDTO) {
    return SLIAnalyseRequest.builder()
        .metricValue(timeSeriesRecordDTO.getMetricValue())
        .timeStamp(Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(timeSeriesRecordDTO.getEpochMinute())))
        .build();
  }
}
