/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BGROVES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.Frequency;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.AnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.LiveMonitoringLogAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.dashboard.beans.AnalyzedLogDataDTO;
import io.harness.cvng.dashboard.services.api.ErrorTrackingDashboardService;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ErrorTrackingDashboardServiceImplTest extends CvNextGenTestBase {
  private String serviceIdentifier;

  @Inject private ErrorTrackingDashboardService errorTrackingDashboardService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Mock private LogAnalysisService mockLogAnalysisService;
  @Mock private CVConfigService mockCvConfigService;
  @Mock private VerificationTaskService mockVerificationTaskService;
  BuilderFactory builderFactory;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    monitoredServiceService.createDefault(builderFactory.getProjectParams(),
        builderFactory.getContext().getServiceIdentifier(), builderFactory.getContext().getEnvIdentifier());

    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(errorTrackingDashboardService, "logAnalysisService", mockLogAnalysisService, true);
    FieldUtils.writeField(errorTrackingDashboardService, "cvConfigService", mockCvConfigService, true);
    FieldUtils.writeField(errorTrackingDashboardService, "verificationTaskService", mockVerificationTaskService, true);
    when(mockVerificationTaskService.getServiceGuardVerificationTaskId(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1, String.class));

    when(mockVerificationTaskService.createLiveMonitoringVerificationTask(
             anyString(), anyString(), any(DataSourceType.class)))
        .thenAnswer(invocation -> invocation.getArgument(1, String.class));
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGetAllLogsData_anomalousLogs() {
    String cvConfigId = generateUuid();
    String errorTrackingCvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);

    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier),
            createErrorTrackingCvConfig(errorTrackingCvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(errorTrackingCvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        errorTrackingDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(),
            timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams);

    // Verify all configs other than Error Tracking are being filtered out
    ArgumentCaptor<String> cvConfigIdCapture = ArgumentCaptor.forClass(String.class);
    verify(mockLogAnalysisService, times(1)).getAnalysisResults(cvConfigIdCapture.capture(), any(), any());
    assertThat(cvConfigIdCapture.getValue()).isNotEqualTo(cvConfigId);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    pageResponse.getContent().forEach(analyzedLogDataDTO -> {
      assertThat(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED)
                     .contains(analyzedLogDataDTO.getLogData().getTag()));
    });

    boolean containsKnown = false;
    for (AnalyzedLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getLogData().getTag().equals(LogAnalysisTag.KNOWN)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isFalse();
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGetAllLogsData_anomalousLogsValidatePagination() {
    String cvConfigId = generateUuid();
    String errorTrackingCvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);

    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(1).build();

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier),
            createErrorTrackingCvConfig(errorTrackingCvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(errorTrackingCvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        errorTrackingDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(),
            timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams);

    // Verify all configs other than Error Tracking are being filtered out
    ArgumentCaptor<String> cvConfigIdCapture = ArgumentCaptor.forClass(String.class);
    verify(mockLogAnalysisService, times(1)).getAnalysisResults(cvConfigIdCapture.capture(), any(), any());
    assertThat(cvConfigIdCapture.getValue()).isNotEqualTo(cvConfigId);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getTotalItems()).isEqualTo(4);
    assertThat(pageResponse.getPageSize()).isEqualTo(1);
    assertThat(pageResponse.getTotalPages()).isGreaterThan(1);
    pageResponse.getContent().forEach(analyzedLogDataDTO -> {
      assertThat(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED)
                     .contains(analyzedLogDataDTO.getLogData().getTag()));
    });
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGotAllLogsData_anomalousLogsNoCvConfigForCategory() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(1).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(new ArrayList<>());

    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        errorTrackingDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(),
            timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams);
    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGetAllLogsData() {
    String cvConfigId = generateUuid();
    String errorTrackingCvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier),
            createErrorTrackingCvConfig(errorTrackingCvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(errorTrackingCvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter = LiveMonitoringLogAnalysisFilter.builder().build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        errorTrackingDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(),
            timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams);

    // Verify all configs other than Error Tracking are being filtered out
    ArgumentCaptor<String> cvConfigIdCapture = ArgumentCaptor.forClass(String.class);
    verify(mockLogAnalysisService, times(1)).getAnalysisResults(cvConfigIdCapture.capture(), any(), any());
    assertThat(cvConfigIdCapture.getValue()).isNotEqualTo(cvConfigId);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    boolean containsKnown = false;
    for (AnalyzedLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getLogData().getTag().equals(LogAnalysisTag.KNOWN)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isTrue();
  }

  private List<LogAnalysisResult> buildLogAnalysisResults(
      String cvConfigId, boolean anomalousOnly, Instant startTime, Instant endTime, List<Long> labels) {
    List<LogAnalysisResult> returnList = new ArrayList<>();
    Instant analysisTime = startTime;
    while (analysisTime.isBefore(endTime)) {
      List<AnalysisResult> resultList = new ArrayList<>();
      labels.forEach(label -> {
        AnalysisResult result =
            AnalysisResult.builder()
                .count(4)
                .label(label)
                .tag(anomalousOnly       ? label % 2 == 0 ? LogAnalysisTag.UNKNOWN : LogAnalysisTag.UNEXPECTED
                        : label % 2 == 0 ? LogAnalysisTag.UNKNOWN
                                         : LogAnalysisTag.KNOWN)
                .riskScore(anomalousOnly ? 0.9 : 0.1)
                .build();
        resultList.add(result);
      });

      returnList.add(LogAnalysisResult.builder()
                         .verificationTaskId(cvConfigId)
                         .analysisStartTime(startTime)
                         .analysisEndTime(endTime.minus(1, ChronoUnit.MINUTES))
                         .logAnalysisResults(resultList)
                         .build());
      analysisTime = analysisTime.plus(1, ChronoUnit.MINUTES);
    }
    return returnList;
  }

  private List<LogAnalysisCluster> buildLogAnalysisClusters(List<Long> labels) {
    List<LogAnalysisCluster> clusterList = new ArrayList<>();
    int[] coordinates = {0};
    labels.forEach(label -> {
      clusterList.add(LogAnalysisCluster.builder()
                          .isEvicted(false)
                          .label(label)
                          .text("This is a dummy text for label " + label)
                          .frequencyTrend(Lists.newArrayList(Frequency.builder().timestamp(12353453L).count(1).build(),
                              Frequency.builder().timestamp(132312L).count(2).build(),
                              Frequency.builder().timestamp(132213L).count(3).build()))
                          .x(coordinates[0]++)
                          .y(coordinates[0]++)
                          .build());
    });
    return clusterList;
  }

  private CVConfig createCvConfig(String cvConfigId, String serviceIdentifier) {
    SplunkCVConfig splunkCVConfig = new SplunkCVConfig();
    splunkCVConfig.setUuid(cvConfigId);
    return splunkCVConfig;
  }

  private CVConfig createErrorTrackingCvConfig(String cvConfigId, String serviceIdentifier) {
    ErrorTrackingCVConfig cvConfig = new ErrorTrackingCVConfig();
    cvConfig.setUuid(cvConfigId);
    cvConfig.setServiceIdentifier(serviceIdentifier);
    return cvConfig;
  }
}
