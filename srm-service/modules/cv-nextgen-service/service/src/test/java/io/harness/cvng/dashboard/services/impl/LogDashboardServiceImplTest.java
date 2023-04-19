/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.PRAVEEN;

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
import io.harness.cvng.analysis.beans.LiveMonitoringLogAnalysisRadarChartClusterDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.Frequency;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.AnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.analysis.entities.LogAnalysisResult.RadarChartTag;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.LiveMonitoringLogAnalysisFilter;
import io.harness.cvng.core.beans.params.filterParams.MonitoredServiceLogAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.dashboard.beans.AnalyzedLogDataDTO;
import io.harness.cvng.dashboard.beans.AnalyzedRadarChartLogDataDTO;
import io.harness.cvng.dashboard.beans.AnalyzedRadarChartLogDataWithCountDTO;
import io.harness.cvng.dashboard.services.api.LogDashboardService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LogDashboardServiceImplTest extends CvNextGenTestBase {
  private String serviceIdentifier;
  private String accountId;
  private Clock clock;

  @Inject private LogDashboardService logDashboardService;
  @Inject private HPersistence hPersistence;
  @Inject private LogAnalysisService logAnalysisService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Mock private LogAnalysisService mockLogAnalysisService;
  @Mock private CVConfigService mockCvConfigService;
  @Mock private VerificationTaskService mockVerificationTaskService;
  BuilderFactory builderFactory;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().getProjectIdentifier();
    builderFactory.getContext().getOrgIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    accountId = builderFactory.getContext().getAccountId();
    monitoredServiceService.createDefault(builderFactory.getProjectParams(),
        builderFactory.getContext().getServiceIdentifier(), builderFactory.getContext().getEnvIdentifier());

    clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(logDashboardService, "logAnalysisService", mockLogAnalysisService, true);
    FieldUtils.writeField(logDashboardService, "cvConfigService", mockCvConfigService, true);
    FieldUtils.writeField(logDashboardService, "verificationTaskService", mockVerificationTaskService, true);
    when(mockVerificationTaskService.getServiceGuardVerificationTaskId(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1, String.class));

    when(mockVerificationTaskService.createLiveMonitoringVerificationTask(
             anyString(), anyString(), any(DataSourceType.class)))
        .thenAnswer(invocation -> invocation.getArgument(1, String.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
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
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

    // Verify Error Tracking configs are being filtered out
    ArgumentCaptor<String> cvConfigIdCapture = ArgumentCaptor.forClass(String.class);
    verify(mockLogAnalysisService, times(1)).getAnalysisResults(cvConfigIdCapture.capture(), any(), any());
    assertThat(cvConfigIdCapture.getValue()).isNotEqualTo(errorTrackingCvConfigId);

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
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_anomalousLogs() {
    String cvConfigId = generateUuid();
    String errorTrackingCvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    List<Long> labelList = Arrays.asList(1234l, 123455l, 12334l, 12345l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier),
            createErrorTrackingCvConfig(errorTrackingCvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .clusterTypes(RadarChartTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();

    AnalyzedRadarChartLogDataWithCountDTO analyzedRadarChartLogDataWithCountDTO =
        logDashboardService.getAllRadarChartLogsData(
            builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams);
    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        analyzedRadarChartLogDataWithCountDTO.getLogAnalysisRadarCharts();
    ArgumentCaptor<String> cvConfigIdCapture = ArgumentCaptor.forClass(String.class);
    verify(mockLogAnalysisService, times(1)).getAnalysisResults(cvConfigIdCapture.capture(), any(), any());
    assertThat(cvConfigIdCapture.getValue()).isNotEqualTo(errorTrackingCvConfigId);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    List<AnalyzedRadarChartLogDataWithCountDTO.LiveMonitoringEventCount> eventCounts =
        analyzedRadarChartLogDataWithCountDTO.getEventCounts();
    assertThat(eventCounts.get(0).getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
    assertThat(eventCounts.get(0).getCount()).isEqualTo(0);
    assertThat(eventCounts.get(1).getClusterType()).isEqualTo(RadarChartTag.UNEXPECTED_FREQUENCY);
    assertThat(eventCounts.get(1).getCount()).isEqualTo(2);
    assertThat(eventCounts.get(2).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(eventCounts.get(2).getCount()).isEqualTo(2);
    pageResponse.getContent().forEach(analyzedLogDataDTO -> {
      assertThat(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED)
                     .contains(analyzedLogDataDTO.getClusterType()));
      if (analyzedLogDataDTO.getClusterType().equals(RadarChartTag.UNEXPECTED_FREQUENCY)) {
        assertThat(analyzedLogDataDTO.getRadius()).isBetween(1.0, 2.0);
      } else {
        assertThat(analyzedLogDataDTO.getRadius()).isBetween(2.0, 3.0);
      }
    });

    boolean containsKnown = false;
    for (AnalyzedRadarChartLogDataDTO analyzedRadarChartLogDataDTO : pageResponse.getContent()) {
      if (analyzedRadarChartLogDataDTO.getClusterType().equals(LogAnalysisTag.KNOWN)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isFalse();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_sortedOrderAnolomousLogs() {
    String cvConfigId = generateUuid();
    String errorTrackingCvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    List<Long> labelList = Arrays.asList(1234l, 123455l, 12334l, 12345l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier),
            createErrorTrackingCvConfig(errorTrackingCvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .clusterTypes(RadarChartTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();

    AnalyzedRadarChartLogDataWithCountDTO analyzedRadarChartLogDataWithCountDTO =
        logDashboardService.getAllRadarChartLogsData(
            builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams);
    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        analyzedRadarChartLogDataWithCountDTO.getLogAnalysisRadarCharts();
    ArgumentCaptor<String> cvConfigIdCapture = ArgumentCaptor.forClass(String.class);
    verify(mockLogAnalysisService, times(1)).getAnalysisResults(cvConfigIdCapture.capture(), any(), any());
    assertThat(cvConfigIdCapture.getValue()).isNotEqualTo(errorTrackingCvConfigId);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    List<AnalyzedRadarChartLogDataWithCountDTO.LiveMonitoringEventCount> eventCounts =
        analyzedRadarChartLogDataWithCountDTO.getEventCounts();
    assertThat(eventCounts.get(0).getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
    assertThat(eventCounts.get(0).getCount()).isEqualTo(0);
    assertThat(eventCounts.get(1).getClusterType()).isEqualTo(RadarChartTag.UNEXPECTED_FREQUENCY);
    assertThat(eventCounts.get(1).getCount()).isEqualTo(2);
    assertThat(eventCounts.get(2).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(eventCounts.get(2).getCount()).isEqualTo(2);

    List<AnalyzedRadarChartLogDataDTO> analyzedRadarChartLogDataDTOS = pageResponse.getContent();

    assertThat(analyzedRadarChartLogDataDTOS.get(0).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(analyzedRadarChartLogDataDTOS.get(1).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(analyzedRadarChartLogDataDTOS.get(2).getClusterType()).isEqualTo(RadarChartTag.UNEXPECTED_FREQUENCY);
    assertThat(analyzedRadarChartLogDataDTOS.get(3).getClusterType()).isEqualTo(RadarChartTag.UNEXPECTED_FREQUENCY);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_sortedOrderKnowLogs() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    AnalyzedRadarChartLogDataWithCountDTO analyzedRadarChartLogDataWithCountDTO =
        logDashboardService.getAllRadarChartLogsData(
            builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams);

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        analyzedRadarChartLogDataWithCountDTO.getLogAnalysisRadarCharts();

    List<AnalyzedRadarChartLogDataWithCountDTO.LiveMonitoringEventCount> eventCounts =
        analyzedRadarChartLogDataWithCountDTO.getEventCounts();
    assertThat(eventCounts.get(0).getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
    assertThat(eventCounts.get(0).getCount()).isEqualTo(2);
    assertThat(eventCounts.get(1).getClusterType()).isEqualTo(RadarChartTag.UNEXPECTED_FREQUENCY);
    assertThat(eventCounts.get(1).getCount()).isEqualTo(0);
    assertThat(eventCounts.get(2).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(eventCounts.get(2).getCount()).isEqualTo(2);

    List<AnalyzedRadarChartLogDataDTO> analyzedRadarChartLogDataDTOS = pageResponse.getContent();

    assertThat(analyzedRadarChartLogDataDTOS.get(0).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(analyzedRadarChartLogDataDTOS.get(1).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(analyzedRadarChartLogDataDTOS.get(2).getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
    assertThat(analyzedRadarChartLogDataDTOS.get(3).getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAllLogsData_anomalousLogsValidatePagination() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);

    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(1).build();

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

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
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_anomalousLogsValidatePagination() {
    String cvConfigId = generateUuid();
    String errorTrackingCvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);

    PageParams pageParams = PageParams.builder().page(0).size(1).build();

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier),
            createErrorTrackingCvConfig(errorTrackingCvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .clusterTypes(LogAnalysisResult.RadarChartTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();
    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getTotalItems()).isEqualTo(4);
    assertThat(pageResponse.getPageSize()).isEqualTo(1);
    assertThat(pageResponse.getTotalPages()).isGreaterThan(1);
    pageResponse.getContent().forEach(analyzedLogDataDTO -> {
      assertThat(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED)
                     .contains(analyzedLogDataDTO.getClusterType()));
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
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
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);
    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_anomalousLogsNoCvConfigForCategory() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(1).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(new ArrayList<>());

    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .clusterTypes(RadarChartTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAllLogsData() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter = LiveMonitoringLogAnalysisFilter.builder().build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

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

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    boolean containsKnown = false;
    for (AnalyzedRadarChartLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getClusterType().equals(RadarChartTag.KNOWN_EVENT)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isTrue();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_AnomalousLogs() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<String> healthSourceIds = Arrays.asList(cvConfigId, "some-config-id");

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .healthSourceIdentifiers(healthSourceIds)
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    pageResponse.getContent().forEach(analyzedLogDataDTO -> {
      assertThat(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED)
                     .contains(analyzedLogDataDTO.getLogData().getTag()));
      assertThat(analyzedLogDataDTO.getLogData().getRiskStatus()).isEqualTo(Risk.UNHEALTHY);
    });

    boolean containsKnown = false;
    for (AnalyzedLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getLogData().getTag().equals(LogAnalysisTag.KNOWN)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isFalse();

    // assert if sorted based on tags
    int size = pageResponse.getContent().size();
    assertThat(pageResponse.getContent().get(0).getLogData().getTag().equals(LogAnalysisTag.UNKNOWN));
    assertThat(pageResponse.getContent().get(size - 1).getLogData().getTag().equals(LogAnalysisTag.UNEXPECTED));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_AnomalousLogsFilteredWithHealthSource() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<String> healthSourceIds = Arrays.asList(cvConfigId);

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Collections.emptyList());

    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .healthSourceIdentifiers(healthSourceIds)
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_AnomalousLogsValidatePagination() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(1).build();

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .healthSourceIdentifiers(null)
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

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
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_AnomalousLogsNoCvConfigPresent() {
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
            .healthSourceIdentifiers(null)
            .clusterTypes(LogAnalysisTag.getAnomalousTags().stream().collect(Collectors.toList()))
            .build();

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_AllLogs() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .healthSourceIdentifiers(null)
            .clusterTypes(Arrays.asList(LogAnalysisTag.values()))
            .build();
    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    boolean containsKnown = false;
    for (AnalyzedLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getLogData().getTag().equals(LogAnalysisTag.KNOWN)) {
        assertThat(analyzedLogDataDTO.getLogData().getRiskStatus()).isEqualTo(Risk.HEALTHY);
        assertThat(analyzedLogDataDTO.getLogData().getRiskScore()).isEqualTo(0.1);
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isTrue();

    // assert if sorted based on tags
    int size = pageResponse.getContent().size();
    assertThat(pageResponse.getContent().get(0).getLogData().getTag().equals(LogAnalysisTag.UNKNOWN));
    assertThat(pageResponse.getContent().get(size - 1).getLogData().getTag().equals(LogAnalysisTag.KNOWN));
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetLogAnalysisRadarChartClusters_AllClusters() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(response.size()).isEqualTo(labelList.size());
    for (LiveMonitoringLogAnalysisRadarChartClusterDTO liveMonitoringLogAnalysisRadarChartClusterDTO : response) {
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getMessage()).isNotEmpty();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getAngle()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType())
          .isIn(RadarChartTag.UNKNOWN_EVENT, RadarChartTag.KNOWN_EVENT);
      if (liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType().equals(RadarChartTag.UNKNOWN_EVENT)) {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.UNHEALTHY);
      } else {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.HEALTHY);
      }
    }
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetLogAnalysisRadarChartClusters_sortingOrder() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(response.size()).isEqualTo(labelList.size());

    assertThat(response.get(0).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(response.get(1).getClusterType()).isEqualTo(RadarChartTag.UNKNOWN_EVENT);
    assertThat(response.get(2).getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
    assertThat(response.get(3).getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetLogAnalysisRadarChartClusters_radius() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(response.size()).isEqualTo(labelList.size());
    for (LiveMonitoringLogAnalysisRadarChartClusterDTO liveMonitoringLogAnalysisRadarChartClusterDTO : response) {
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getMessage()).isNotEmpty();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getAngle()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType())
          .isIn(RadarChartTag.UNKNOWN_EVENT, RadarChartTag.KNOWN_EVENT);
      if (liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType().equals(RadarChartTag.UNKNOWN_EVENT)) {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.UNHEALTHY);
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isBetween(2.0, 3.0);
      } else {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.HEALTHY);
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isBetween(1.0, 2.0);
      }
    }
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetLogAnalysisRadarChartClusters_filterByCLusterTypes() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);

    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);

    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .clusterTypes(Arrays.asList(RadarChartTag.KNOWN_EVENT))
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();

    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(response.size()).isEqualTo(2);
    for (LiveMonitoringLogAnalysisRadarChartClusterDTO liveMonitoringLogAnalysisRadarChartClusterDTO : response) {
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getMessage()).isNotEmpty();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getAngle()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType()).isEqualTo(RadarChartTag.KNOWN_EVENT);
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.HEALTHY);
    }
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetLogAnalysisRadarChartClusters_filterByHealthSources() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    List<String> healthSourceIds = Arrays.asList(cvConfigId);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .healthSourceIdentifiers(healthSourceIds)
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();
    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds);
    assertThat(response.size()).isEqualTo(labelList.size());
    for (LiveMonitoringLogAnalysisRadarChartClusterDTO liveMonitoringLogAnalysisRadarChartClusterDTO : response) {
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getMessage()).isNotEmpty();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getAngle()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType())
          .isIn(RadarChartTag.UNKNOWN_EVENT, RadarChartTag.KNOWN_EVENT);
      if (liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType().equals(RadarChartTag.UNKNOWN_EVENT)) {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.UNHEALTHY);
      } else {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.HEALTHY);
      }
    }

    monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                            .healthSourceIdentifiers(Arrays.asList("some-identifier"))
                                            .startTimeMillis(startTime.toEpochMilli())
                                            .endTimeMillis(endTime.toEpochMilli())
                                            .build();
    response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);
    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds);
    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_AllLogsFilteredUsingHealthSources() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    List<String> healthSourceIds = Arrays.asList(cvConfigId);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));
    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .healthSourceIdentifiers(healthSourceIds)
            .clusterTypes(Arrays.asList(LogAnalysisTag.values()))
            .build();
    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds);
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

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_AllLogsFilteredUsingHealthSources() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    List<String> healthSourceIds = Arrays.asList(cvConfigId);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .healthSourceIdentifiers(healthSourceIds)
            .clusterTypes(Arrays.asList(RadarChartTag.values()))
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    boolean containsKnown = false;
    for (AnalyzedRadarChartLogDataDTO analyzedRadarChartLogDataDTO : pageResponse.getContent()) {
      if (analyzedRadarChartLogDataDTO.getClusterType().equals(RadarChartTag.KNOWN_EVENT)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isTrue();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_withNoClusterInLogAnalysisResult() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant();
    List<Long> labelList = Arrays.asList(0l, 1l, 2l);

    Instant time = roundDownTo5MinBoundary(clock.instant());
    List<LogAnalysisResult> logAnalysisResults =
        Arrays.asList(LogAnalysisResult.builder()
                          .analysisStartTime(time.minus(10, ChronoUnit.MINUTES))
                          .analysisEndTime(time.minus(5, ChronoUnit.MINUTES))
                          .verificationTaskId(cvConfigId)
                          .accountId(accountId)
                          .build(),
            LogAnalysisResult.builder()
                .analysisStartTime(time.minus(5, ChronoUnit.MINUTES))
                .analysisEndTime(time)
                .verificationTaskId(cvConfigId)
                .accountId(accountId)
                .build());

    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(logAnalysisResults);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter = LiveMonitoringLogAnalysisFilter.builder().build();
    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);
    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_withNoClusterInLogAnalysisResult() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant();
    List<Long> labelList = Arrays.asList(0l, 1l, 2l);

    Instant time = roundDownTo5MinBoundary(clock.instant());
    List<LogAnalysisResult> logAnalysisResults =
        Arrays.asList(LogAnalysisResult.builder()
                          .analysisStartTime(time.minus(10, ChronoUnit.MINUTES))
                          .analysisEndTime(time.minus(5, ChronoUnit.MINUTES))
                          .verificationTaskId(cvConfigId)
                          .accountId(accountId)
                          .build(),
            LogAnalysisResult.builder()
                .analysisStartTime(time.minus(5, ChronoUnit.MINUTES))
                .analysisEndTime(time)
                .verificationTaskId(cvConfigId)
                .accountId(accountId)
                .build());

    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(logAnalysisResults);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetAllLogsData_withFilteringAndTheSameClustersHavingKNOWNAndUNKNOWNTag() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant();
    List<Long> labelList = Arrays.asList(0l, 1l, 2l);

    Instant time = roundDownTo5MinBoundary(clock.instant());
    List<LogAnalysisResult> logAnalysisResults = Arrays.asList(
        LogAnalysisResult.builder()
            .analysisStartTime(time.minus(10, ChronoUnit.MINUTES))
            .analysisEndTime(time.minus(5, ChronoUnit.MINUTES))
            .verificationTaskId(cvConfigId)
            .accountId(accountId)
            .logAnalysisResults(Arrays.asList(
                AnalysisResult.builder().label(labelList.get(0)).tag(LogAnalysisTag.UNKNOWN).count(10).build(),
                AnalysisResult.builder().label(labelList.get(1)).tag(LogAnalysisTag.KNOWN).count(20).build(),
                AnalysisResult.builder().label(labelList.get(2)).tag(LogAnalysisTag.KNOWN).count(30).build()))
            .build(),
        LogAnalysisResult.builder()
            .analysisStartTime(time.minus(5, ChronoUnit.MINUTES))
            .analysisEndTime(time)
            .verificationTaskId(cvConfigId)
            .accountId(accountId)
            .logAnalysisResults(Arrays.asList(
                AnalysisResult.builder().label(labelList.get(0)).tag(LogAnalysisTag.KNOWN).count(30).build(),
                AnalysisResult.builder().label(labelList.get(1)).tag(LogAnalysisTag.KNOWN).count(40).build(),
                AnalysisResult.builder().label(labelList.get(2)).tag(LogAnalysisTag.UNEXPECTED).count(300).build()))
            .build());

    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(logAnalysisResults);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter = LiveMonitoringLogAnalysisFilter.builder().build();
    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(), timeRangeParams,
            liveMonitoringLogAnalysisFilter, pageParams);
    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(3);

    liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder().clusterTypes(Arrays.asList(LogAnalysisTag.KNOWN)).build();
    pageResponse = logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(),
        timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);

    liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder().clusterTypes(Arrays.asList(LogAnalysisTag.UNKNOWN)).build();
    pageResponse = logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(),
        timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);

    liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder().clusterTypes(Arrays.asList(LogAnalysisTag.UNEXPECTED)).build();
    pageResponse = logDashboardService.getAllLogsData(builderFactory.getContext().getMonitoredServiceParams(),
        timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_withFilteringAndTheSameClustersHavingKNOWNAndUNKNOWNTag() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant();
    List<Long> labelList = Arrays.asList(0l, 1l, 2l);

    Instant time = roundDownTo5MinBoundary(clock.instant());
    List<LogAnalysisResult> logAnalysisResults = Arrays.asList(
        LogAnalysisResult.builder()
            .analysisStartTime(time.minus(10, ChronoUnit.MINUTES))
            .analysisEndTime(time.minus(5, ChronoUnit.MINUTES))
            .verificationTaskId(cvConfigId)
            .accountId(accountId)
            .logAnalysisResults(Arrays.asList(
                AnalysisResult.builder().label(labelList.get(0)).tag(LogAnalysisTag.UNKNOWN).count(10).build(),
                AnalysisResult.builder().label(labelList.get(1)).tag(LogAnalysisTag.KNOWN).count(20).build(),
                AnalysisResult.builder().label(labelList.get(2)).tag(LogAnalysisTag.KNOWN).count(30).build()))
            .build(),
        LogAnalysisResult.builder()
            .analysisStartTime(time.minus(5, ChronoUnit.MINUTES))
            .analysisEndTime(time)
            .verificationTaskId(cvConfigId)
            .accountId(accountId)
            .logAnalysisResults(Arrays.asList(
                AnalysisResult.builder().label(labelList.get(0)).tag(LogAnalysisTag.KNOWN).count(30).build(),
                AnalysisResult.builder().label(labelList.get(1)).tag(LogAnalysisTag.KNOWN).count(40).build(),
                AnalysisResult.builder().label(labelList.get(2)).tag(LogAnalysisTag.UNEXPECTED).count(300).build()))
            .build());

    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(logAnalysisResults);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();

    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(3);

    monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                            .clusterTypes(Arrays.asList(RadarChartTag.KNOWN_EVENT))
                                            .startTimeMillis(startTime.toEpochMilli())
                                            .endTimeMillis(endTime.toEpochMilli())
                                            .build();

    pageResponse = logDashboardService
                       .getAllRadarChartLogsData(builderFactory.getContext().getMonitoredServiceParams(),
                           monitoredServiceLogAnalysisFilter, pageParams)
                       .getLogAnalysisRadarCharts();
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);

    monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                            .clusterTypes(Arrays.asList(RadarChartTag.UNKNOWN_EVENT))
                                            .startTimeMillis(startTime.toEpochMilli())
                                            .endTimeMillis(endTime.toEpochMilli())
                                            .build();

    pageResponse = logDashboardService
                       .getAllRadarChartLogsData(builderFactory.getContext().getMonitoredServiceParams(),
                           monitoredServiceLogAnalysisFilter, pageParams)
                       .getLogAnalysisRadarCharts();
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);

    monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                            .clusterTypes(Arrays.asList(RadarChartTag.UNEXPECTED_FREQUENCY))
                                            .startTimeMillis(startTime.toEpochMilli())
                                            .endTimeMillis(endTime.toEpochMilli())
                                            .build();

    pageResponse = logDashboardService
                       .getAllRadarChartLogsData(builderFactory.getContext().getMonitoredServiceParams(),
                           monitoredServiceLogAnalysisFilter, pageParams)
                       .getLogAnalysisRadarCharts();
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartLogCluster_withFilteringAndTheSameClustersHavingKNOWNAndUNKNOWNTag()
      throws IllegalAccessException {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(20, ChronoUnit.MINUTES);
    Instant endTime = clock.instant();
    List<Long> labelList = Arrays.asList(0l, 1l, 2l);

    Instant time = roundDownTo5MinBoundary(clock.instant());
    List<LogAnalysisResult> logAnalysisResults = Arrays.asList(
        LogAnalysisResult.builder()
            .analysisStartTime(time.minus(10, ChronoUnit.MINUTES))
            .analysisEndTime(time.minus(5, ChronoUnit.MINUTES))
            .verificationTaskId(cvConfigId)
            .accountId(accountId)
            .logAnalysisResults(Arrays.asList(
                AnalysisResult.builder().label(labelList.get(0)).tag(LogAnalysisTag.UNKNOWN).count(10).build(),
                AnalysisResult.builder().label(labelList.get(1)).tag(LogAnalysisTag.KNOWN).count(20).build(),
                AnalysisResult.builder().label(labelList.get(2)).tag(LogAnalysisTag.KNOWN).count(30).build()))
            .build(),
        LogAnalysisResult.builder()
            .analysisStartTime(time.minus(5, ChronoUnit.MINUTES))
            .analysisEndTime(time)
            .verificationTaskId(cvConfigId)
            .accountId(accountId)
            .logAnalysisResults(Arrays.asList(
                AnalysisResult.builder().label(labelList.get(0)).tag(LogAnalysisTag.KNOWN).count(30).build(),
                AnalysisResult.builder().label(labelList.get(1)).tag(LogAnalysisTag.KNOWN).count(40).build(),
                AnalysisResult.builder().label(labelList.get(2)).tag(LogAnalysisTag.UNEXPECTED).count(300).build()))
            .build());
    List<LogAnalysisCluster> clusters = buildLogAnalysisClusters(labelList);
    clusters.forEach(cluster -> cluster.setVerificationTaskId(cvConfigId));

    hPersistence.save(logAnalysisResults);
    hPersistence.save(clusters);

    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams()))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    FieldUtils.writeField(logDashboardService, "logAnalysisService", logAnalysisService, true);

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();
    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);
    verify(mockCvConfigService).list(builderFactory.getContext().getMonitoredServiceParams());
    assertThat(response).isNotNull();
    assertThat(response).isNotEmpty();
    assertThat(response.size()).isEqualTo(3);

    monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                            .clusterTypes(Arrays.asList(RadarChartTag.KNOWN_EVENT))
                                            .startTimeMillis(startTime.toEpochMilli())
                                            .endTimeMillis(endTime.toEpochMilli())
                                            .build();
    response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);
    assertThat(response).isNotNull();
    assertThat(response).isNotEmpty();
    assertThat(response.size()).isEqualTo(1);

    monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                            .clusterTypes(Arrays.asList(RadarChartTag.UNKNOWN_EVENT))
                                            .startTimeMillis(startTime.toEpochMilli())
                                            .endTimeMillis(endTime.toEpochMilli())
                                            .build();
    response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);
    assertThat(response).isNotNull();
    assertThat(response).isNotEmpty();
    assertThat(response.size()).isEqualTo(1);

    monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                            .clusterTypes(Arrays.asList(RadarChartTag.UNEXPECTED_FREQUENCY))
                                            .startTimeMillis(startTime.toEpochMilli())
                                            .endTimeMillis(endTime.toEpochMilli())
                                            .build();
    response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);
    assertThat(response).isNotNull();
    assertThat(response).isNotEmpty();
    assertThat(response.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartLogCluster_filterWithClusterId() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    List<String> healthSourceIds = Arrays.asList(cvConfigId);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .clusterId(UUID.nameUUIDFromBytes(
                               (mockVerificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId)
                                   + ":" + labelList.get(2))
                                   .getBytes(Charsets.UTF_8))
                           .toString())
            .healthSourceIdentifiers(healthSourceIds)
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();
    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);

    assertThat(response.size()).isEqualTo(1);
    for (LiveMonitoringLogAnalysisRadarChartClusterDTO liveMonitoringLogAnalysisRadarChartClusterDTO : response) {
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getMessage()).isNotEmpty();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getAngle()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType())
          .isIn(RadarChartTag.UNKNOWN_EVENT, RadarChartTag.KNOWN_EVENT);
      if (liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType().equals(RadarChartTag.UNKNOWN_EVENT)) {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.UNHEALTHY);
      } else {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.HEALTHY);
      }
    }
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartLogCluster_filterWithAngle() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    List<String> healthSourceIds = Arrays.asList(cvConfigId);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .minAngle(230.0)
                                                                              .maxAngle(290.0)
                                                                              .healthSourceIdentifiers(healthSourceIds)
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();
    List<LiveMonitoringLogAnalysisRadarChartClusterDTO> response = logDashboardService.getLogAnalysisRadarChartClusters(
        builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter);

    assertThat(response.size()).isEqualTo(1);
    for (LiveMonitoringLogAnalysisRadarChartClusterDTO liveMonitoringLogAnalysisRadarChartClusterDTO : response) {
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getMessage()).isNotEmpty();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRadius()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getAngle()).isNotNull();
      assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType())
          .isIn(RadarChartTag.UNKNOWN_EVENT, RadarChartTag.KNOWN_EVENT);
      if (liveMonitoringLogAnalysisRadarChartClusterDTO.getClusterType().equals(RadarChartTag.UNKNOWN_EVENT)) {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.UNHEALTHY);
      } else {
        assertThat(liveMonitoringLogAnalysisRadarChartClusterDTO.getRisk()).isEqualTo(Risk.HEALTHY);
      }
    }
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_filterWithClusterId() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    List<String> healthSourceIds = Arrays.asList(cvConfigId);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList, cvConfigId));

    String temp = mockVerificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId);

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter =
        MonitoredServiceLogAnalysisFilter.builder()
            .clusterId(UUID.nameUUIDFromBytes(
                               (mockVerificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId)
                                   + ":" + labelList.get(2))
                                   .getBytes(Charsets.UTF_8))
                           .toString())
            .healthSourceIdentifiers(healthSourceIds)
            .startTimeMillis(startTime.toEpochMilli())
            .endTimeMillis(endTime.toEpochMilli())
            .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllRadarChartLogsData_filterWithAngle() {
    String cvConfigId = generateUuid();
    Instant startTime = clock.instant().minus(10, ChronoUnit.MINUTES);
    Instant endTime = clock.instant().minus(5, ChronoUnit.MINUTES);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    List<String> healthSourceIds = Arrays.asList(cvConfigId);
    when(mockCvConfigService.list(builderFactory.getContext().getMonitoredServiceParams(), healthSourceIds))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter = MonitoredServiceLogAnalysisFilter.builder()
                                                                              .minAngle(230.0)
                                                                              .maxAngle(290.0)
                                                                              .healthSourceIdentifiers(healthSourceIds)
                                                                              .startTimeMillis(startTime.toEpochMilli())
                                                                              .endTimeMillis(endTime.toEpochMilli())
                                                                              .build();

    PageResponse<AnalyzedRadarChartLogDataDTO> pageResponse =
        logDashboardService
            .getAllRadarChartLogsData(
                builderFactory.getContext().getMonitoredServiceParams(), monitoredServiceLogAnalysisFilter, pageParams)
            .getLogAnalysisRadarCharts();

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
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
    return buildLogAnalysisClusters(labels, null);
  }

  private List<LogAnalysisCluster> buildLogAnalysisClusters(List<Long> labels, String cvConfig) {
    List<LogAnalysisCluster> clusterList = new ArrayList<>();
    int[] coordinates = {0};
    labels.forEach(label -> {
      clusterList.add(LogAnalysisCluster.builder()
                          .verificationTaskId(cvConfig)
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
