/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLIMetricAnalysisTransformer;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLIMetricAnalysisTransformerTest extends CvNextGenTestBase {
  @Inject SLIMetricAnalysisTransformer sliMetricAnalysisTransformer;
  private String metric1Name;
  private String metric2Name;
  private String metric1Identifier;
  private String metric2Identifier;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;
  private Double metricValue;
  private int totalMinutes;

  @Before
  public void setUp() {
    metric1Name = "metric1";
    metric2Name = "metric2Name";
    metric1Identifier = "metric1identifier";
    metric2Identifier = "metric2identifier";
    startTime = Instant.now();
    totalMinutes = 20;
    endTime = startTime.plus(totalMinutes, ChronoUnit.MINUTES);
    verificationTaskId = generateUuid();
    metricValue = 10.0;
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetSLIAnalyseRequest() {
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOList = getTimeSeriesDTOList();
    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequestMap =
        sliMetricAnalysisTransformer.getSLIAnalyseRequest(timeSeriesRecordDTOList);
    assertThat(sliAnalyseRequestMap.size()).isEqualTo(2);
    assertThat(sliAnalyseRequestMap.get(metric1Identifier).size()).isEqualTo(totalMinutes);
    assertThat(sliAnalyseRequestMap.get(metric2Identifier).size()).isEqualTo(totalMinutes);
  }

  private List<TimeSeriesRecordDTO> getTimeSeriesDTOList() {
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOList = new ArrayList<>();
    for (Instant i = startTime; i.isBefore(endTime); i = i.plus(1, ChronoUnit.MINUTES)) {
      TimeSeriesRecordDTO timeSeriesRecordDTO1 = TimeSeriesRecordDTO.builder()
                                                     .verificationTaskId(verificationTaskId)
                                                     .groupName(generateUuid())
                                                     .metricIdentifier(metric1Identifier)
                                                     .metricName(metric1Name)
                                                     .epochMinute(TimeUnit.MILLISECONDS.toMinutes(i.toEpochMilli()))
                                                     .metricValue(metricValue)
                                                     .build();
      timeSeriesRecordDTOList.add(timeSeriesRecordDTO1);
      TimeSeriesRecordDTO timeSeriesRecordDTO2 = TimeSeriesRecordDTO.builder()
                                                     .verificationTaskId(verificationTaskId)
                                                     .groupName(generateUuid())
                                                     .metricIdentifier(metric2Identifier)
                                                     .metricName(metric2Name)
                                                     .epochMinute(TimeUnit.MILLISECONDS.toMinutes(i.toEpochMilli()))
                                                     .metricValue(metricValue)
                                                     .build();
      timeSeriesRecordDTOList.add(timeSeriesRecordDTO2);
    }
    return timeSeriesRecordDTOList;
  }
}
