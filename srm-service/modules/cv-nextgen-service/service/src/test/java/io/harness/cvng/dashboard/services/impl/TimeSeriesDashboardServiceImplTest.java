/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.TimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.dashboard.beans.TimeSeriesMetricDataDTO;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TimeSeriesDashboardServiceImplTest extends CvNextGenTestBase {
  @Inject private TimeSeriesDashboardService timeSeriesDashboardService;

  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;
  private String monitoredServiceIdentifier;
  private ServiceEnvironmentParams serviceEnvironmentParams;
  private MonitoredServiceParams monitoredServiceParams;

  @Mock private CVConfigService cvConfigService;
  @Mock private TimeSeriesRecordService timeSeriesRecordService;
  @Mock private MonitoredServiceService monitoredServiceService;

  @Before
  public void setUp() throws Exception {
    accountId = generateUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    serviceIdentifier = generateUuid();
    envIdentifier = generateUuid();
    monitoredServiceIdentifier = generateUuid();
    serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                   .accountIdentifier(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .serviceIdentifier(serviceIdentifier)
                                   .environmentIdentifier(envIdentifier)
                                   .build();
    monitoredServiceParams = MonitoredServiceParams.builderWithServiceEnvParams(serviceEnvironmentParams)
                                 .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                 .build();

    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(timeSeriesDashboardService, "cvConfigService", cvConfigService, true);
    FieldUtils.writeField(timeSeriesDashboardService, "timeSeriesRecordService", timeSeriesRecordService, true);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetSortedMetricData() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, false, false));
    List<String> cvConfigs = Arrays.asList(cvConfigId);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list(monitoredServiceParams)).thenReturn(Arrays.asList(cvConfig));

    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, TimeRangeParams.builder().startTime(start).endTime(end).build(),
        TimeSeriesAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());

    verify(cvConfigService).list(monitoredServiceParams);
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetSortedMetricData_customerDefinedUnhealthy() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, false, true));
    List<String> cvConfigs = Arrays.asList(cvConfigId);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list(monitoredServiceParams)).thenReturn(Arrays.asList(cvConfig));

    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, TimeRangeParams.builder().startTime(start).endTime(end).build(),
        TimeSeriesAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());

    verify(cvConfigService).list(monitoredServiceParams);
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
    response.getContent().forEach(timeSeriesMetricDataDTO
        -> assertThat(timeSeriesMetricDataDTO.getMetricDataList()
                          .stream()
                          .filter(metricData -> metricData.getRisk().equals(Risk.CUSTOMER_DEFINED_UNHEALTHY))
                          .count())
               .isEqualTo(1));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetSortedMetricErrorData() {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    List<TimeSeriesRecord> timeSeriesRecords = new ArrayList<>();
    timeSeriesRecords.add(TimeSeriesRecord.builder()
                              .verificationTaskId(cvConfigId)
                              .bucketStartTime(start)
                              .metricName("m1")
                              .metricType(TimeSeriesMetricType.THROUGHPUT)
                              .timeSeriesGroupValues(Sets.newHashSet(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                                                         .groupName("g1")
                                                                         .metricValue(1.0)
                                                                         .timeStamp(start)
                                                                         .build(),
                                  TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                      .groupName("g2")
                                      .metricValue(2.0)
                                      .timeStamp(start)
                                      .build()))
                              .build());

    timeSeriesRecords.add(TimeSeriesRecord.builder()
                              .verificationTaskId(cvConfigId)
                              .bucketStartTime(start)
                              .metricName("m2")
                              .metricType(TimeSeriesMetricType.ERROR)
                              .timeSeriesGroupValues(Sets.newHashSet(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                                                         .groupName("g1")
                                                                         .metricValue(1.0)
                                                                         .percentValue(2.0)
                                                                         .timeStamp(start)
                                                                         .build(),
                                  TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                      .groupName("g2")
                                      .metricValue(2.0)
                                      .timeStamp(start)
                                      .build()))
                              .build());
    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(timeSeriesRecords);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list(monitoredServiceParams)).thenReturn(Arrays.asList(cvConfig));

    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, TimeRangeParams.builder().startTime(start).endTime(end).build(),
        TimeSeriesAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    List<TimeSeriesMetricDataDTO> timeSeriesMetricDTOs = response.getContent();
    assertThat(timeSeriesMetricDTOs.size()).isEqualTo(4);
    TimeSeriesMetricDataDTO timeSeriesMetricDataDTO = timeSeriesMetricDTOs.get(0);
    assertThat(timeSeriesMetricDataDTO.getMetricType()).isEqualTo(TimeSeriesMetricType.THROUGHPUT);
    assertThat(timeSeriesMetricDataDTO.getMetricName()).isEqualTo("m1");
    assertThat(timeSeriesMetricDataDTO.getGroupName()).isEqualTo("g1");
    assertThat(timeSeriesMetricDataDTO.getMetricDataList().size()).isEqualTo(6);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetSortedMetricData_withNegativeRiskScoreTotalRiskDoesNotChange() {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    List<TimeSeriesRecord> timeSeriesRecords = new ArrayList<>();
    timeSeriesRecords.add(TimeSeriesRecord.builder()
                              .verificationTaskId(cvConfigId)
                              .bucketStartTime(start)
                              .metricName("m1")
                              .metricType(TimeSeriesMetricType.THROUGHPUT)
                              .timeSeriesGroupValues(Sets.newHashSet(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                                                         .groupName("g1")
                                                                         .metricValue(1.0)
                                                                         .timeStamp(start)
                                                                         .riskScore(-1)
                                                                         .build()))
                              .build());

    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(timeSeriesRecords);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list(monitoredServiceParams)).thenReturn(Arrays.asList(cvConfig));

    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, TimeRangeParams.builder().startTime(start).endTime(end).build(),
        TimeSeriesAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    List<TimeSeriesMetricDataDTO> timeSeriesMetricDTOs = response.getContent();
    assertThat(timeSeriesMetricDTOs.size()).isEqualTo(1);
    assertThat(timeSeriesMetricDTOs.get(0).getTotalRisk()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetTimeSeriesMetricData_filterByHealthSourceIdentifiers() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(start).endTime(end).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    String cvConfigId = generateUuid();
    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, true, false));
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    List<String> healthSourceIdentifiers = Arrays.asList(cvConfig.getIdentifier());
    Map<String, DataSourceType> cvConfigToDataSourceTypeMap = new HashMap<>();
    cvConfigToDataSourceTypeMap.put(cvConfigId, APP_DYNAMICS);
    when(cvConfigService.list(monitoredServiceParams, healthSourceIdentifiers)).thenReturn(Arrays.asList(cvConfig));
    when(cvConfigService.getDataSourceTypeForCVConfigs(monitoredServiceParams)).thenReturn(cvConfigToDataSourceTypeMap);

    TimeSeriesAnalysisFilter timeSeriesAnalysisFilter = TimeSeriesAnalysisFilter.builder()
                                                            .filter(null)
                                                            .anomalousMetricsOnly(true)
                                                            .healthSourceIdentifiers(healthSourceIdentifiers)
                                                            .build();

    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, timeRangeParams, timeSeriesAnalysisFilter, pageParams);

    verify(cvConfigService).list(monitoredServiceParams, healthSourceIdentifiers);
    verify(cvConfigService).getDataSourceTypeForCVConfigs(monitoredServiceParams);
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
    assertThat(response.getContent().size()).isEqualTo(10);

    response.getContent().forEach(timeSeriesMetricDataDTO -> {
      assertThat(timeSeriesMetricDataDTO.getDataSourceType()).isEqualTo(APP_DYNAMICS);
    });

    timeSeriesAnalysisFilter = TimeSeriesAnalysisFilter.builder()
                                   .filter(null)
                                   .anomalousMetricsOnly(true)
                                   .healthSourceIdentifiers(Arrays.asList("some-identifier"))
                                   .build();

    response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, timeRangeParams, timeSeriesAnalysisFilter, pageParams);
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetTimeSeriesMetricData_fillersForNoDataPresent() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(15, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(start).endTime(end).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    String cvConfigId = generateUuid();
    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, true, false));
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    List<String> healthSourceIdentifiers = Arrays.asList(cvConfig.getIdentifier());
    Map<String, DataSourceType> cvConfigToDataSourceTypeMap = new HashMap<>();
    cvConfigToDataSourceTypeMap.put(cvConfigId, APP_DYNAMICS);
    when(cvConfigService.list(monitoredServiceParams, healthSourceIdentifiers)).thenReturn(Arrays.asList(cvConfig));
    when(cvConfigService.getDataSourceTypeForCVConfigs(monitoredServiceParams)).thenReturn(cvConfigToDataSourceTypeMap);

    TimeSeriesAnalysisFilter timeSeriesAnalysisFilter = TimeSeriesAnalysisFilter.builder()
                                                            .filter(null)
                                                            .anomalousMetricsOnly(true)
                                                            .healthSourceIdentifiers(healthSourceIdentifiers)
                                                            .build();

    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, timeRangeParams, timeSeriesAnalysisFilter, pageParams);

    verify(cvConfigService).list(monitoredServiceParams, healthSourceIdentifiers);
    verify(cvConfigService).getDataSourceTypeForCVConfigs(monitoredServiceParams);
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
    assertThat(response.getContent().size()).isEqualTo(10);
    assertThat(response.getTotalPages()).isEqualTo(19);

    response.getContent().forEach(timeSeriesMetricDataDTO -> {
      assertThat(timeSeriesMetricDataDTO.getDataSourceType()).isEqualTo(APP_DYNAMICS);
      assertThat(timeSeriesMetricDataDTO.getMetricDataList()).size().isEqualTo(16);
    });
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void
  testGetTimeSeriesMetricData_fillersForNoDataPresentAlongWithUpdatingTimeStampValuesWithRespectiveValues() {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(10, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(start).endTime(end).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    String cvConfigId = generateUuid();
    List<TimeSeriesRecord> timeSeriesRecords = new ArrayList<>();
    timeSeriesRecords.add(TimeSeriesRecord.builder()
                              .verificationTaskId(cvConfigId)
                              .bucketStartTime(start)
                              .metricName("m1")
                              .metricType(TimeSeriesMetricType.THROUGHPUT)
                              .timeSeriesGroupValues(Sets.newHashSet(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                                                         .groupName("g1")
                                                                         .metricValue(1.0)
                                                                         .timeStamp(start.plus(1, ChronoUnit.MINUTES))
                                                                         .build(),
                                  TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                      .groupName("g2")
                                      .metricValue(2.0)
                                      .timeStamp(start.plus(2, ChronoUnit.MINUTES))
                                      .build()))
                              .build());

    timeSeriesRecords.add(TimeSeriesRecord.builder()
                              .verificationTaskId(cvConfigId)
                              .bucketStartTime(start)
                              .metricName("m2")
                              .metricType(TimeSeriesMetricType.ERROR)
                              .timeSeriesGroupValues(Sets.newHashSet(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                                                         .groupName("g1")
                                                                         .metricValue(1.0)
                                                                         .percentValue(2.0)
                                                                         .timeStamp(start.plus(3, ChronoUnit.MINUTES))
                                                                         .build(),
                                  TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                      .groupName("g2")
                                      .metricValue(2.0)
                                      .timeStamp(start.plus(4, ChronoUnit.MINUTES))
                                      .build()))
                              .build());
    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(timeSeriesRecords);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list((MonitoredServiceParams) any())).thenReturn(Arrays.asList(cvConfig));
    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, timeRangeParams, TimeSeriesAnalysisFilter.builder().build(), pageParams);

    List<TimeSeriesMetricDataDTO> timeSeriesMetricDTOs = response.getContent();
    assertThat(timeSeriesMetricDTOs.size()).isEqualTo(4);
    TimeSeriesMetricDataDTO timeSeriesMetricDataDTO = timeSeriesMetricDTOs.get(0);
    assertThat(timeSeriesMetricDataDTO.getMetricType()).isEqualTo(TimeSeriesMetricType.THROUGHPUT);
    assertThat(timeSeriesMetricDataDTO.getMetricName()).isEqualTo("m1");
    assertThat(timeSeriesMetricDataDTO.getGroupName()).isEqualTo("g1");
    assertThat(timeSeriesMetricDataDTO.getMetricDataList().size()).isEqualTo(11);
    int noDataRecordCount = 0;
    int index = 0;
    for (TimeSeriesMetricDataDTO.MetricData metricData : timeSeriesMetricDataDTO.getMetricDataList()) {
      if (metricData.getRisk().compareTo(Risk.NO_DATA) == 0) {
        noDataRecordCount++;
      } else {
        assertThat(metricData.getValue()).isEqualTo(1.0, offset(0.00001));
        assertThat(index).isEqualTo(1);
      }
      index++;
    }
    assertThat(noDataRecordCount).isEqualTo(10);

    timeSeriesMetricDataDTO = timeSeriesMetricDTOs.get(1);
    assertThat(timeSeriesMetricDataDTO.getMetricType()).isEqualTo(TimeSeriesMetricType.ERROR);
    assertThat(timeSeriesMetricDataDTO.getMetricName()).isEqualTo("m2");
    assertThat(timeSeriesMetricDataDTO.getGroupName()).isEqualTo("g1");
    assertThat(timeSeriesMetricDataDTO.getMetricDataList().size()).isEqualTo(11);
    noDataRecordCount = 0;
    index = 0;
    for (TimeSeriesMetricDataDTO.MetricData metricData : timeSeriesMetricDataDTO.getMetricDataList()) {
      if (metricData.getRisk().compareTo(Risk.NO_DATA) == 0) {
        noDataRecordCount++;
      } else {
        assertThat(metricData.getValue()).isEqualTo(2.0, offset(0.00001));
        assertThat(index).isEqualTo(3);
      }
      index++;
    }
    assertThat(noDataRecordCount).isEqualTo(10);

    timeSeriesMetricDataDTO = timeSeriesMetricDTOs.get(2);
    assertThat(timeSeriesMetricDataDTO.getMetricType()).isEqualTo(TimeSeriesMetricType.THROUGHPUT);
    assertThat(timeSeriesMetricDataDTO.getMetricName()).isEqualTo("m1");
    assertThat(timeSeriesMetricDataDTO.getGroupName()).isEqualTo("g2");
    assertThat(timeSeriesMetricDataDTO.getMetricDataList().size()).isEqualTo(11);
    noDataRecordCount = 0;
    index = 0;
    for (TimeSeriesMetricDataDTO.MetricData metricData : timeSeriesMetricDataDTO.getMetricDataList()) {
      if (metricData.getRisk().compareTo(Risk.NO_DATA) == 0) {
        noDataRecordCount++;
      } else {
        assertThat(metricData.getValue()).isEqualTo(2.0, offset(0.00001));
        assertThat(index).isEqualTo(2);
      }
      index++;
    }
    assertThat(noDataRecordCount).isEqualTo(10);

    timeSeriesMetricDataDTO = timeSeriesMetricDTOs.get(3);
    assertThat(timeSeriesMetricDataDTO.getMetricType()).isEqualTo(TimeSeriesMetricType.ERROR);
    assertThat(timeSeriesMetricDataDTO.getMetricName()).isEqualTo("m2");
    assertThat(timeSeriesMetricDataDTO.getGroupName()).isEqualTo("g2");
    assertThat(timeSeriesMetricDataDTO.getMetricDataList().size()).isEqualTo(11);
    noDataRecordCount = 0;
    index = 0;
    for (TimeSeriesMetricDataDTO.MetricData metricData : timeSeriesMetricDataDTO.getMetricDataList()) {
      if (metricData.getRisk().compareTo(Risk.NO_DATA) == 0) {
        noDataRecordCount++;
      } else {
        assertThat(metricData.getValue()).isEqualTo(0.0, offset(0.00001));
        assertThat(index).isEqualTo(4);
      }
      index++;
    }
    assertThat(noDataRecordCount).isEqualTo(10);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetTimeSeriesMetricDataTOCheckSortednessBetweenHealthyAndNoAnalysisState() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);

    List<TimeSeriesRecord> timeSeriesRecords = new ArrayList<>();
    timeSeriesRecords.add(TimeSeriesRecord.builder()
                              .verificationTaskId(cvConfigId)
                              .bucketStartTime(start)
                              .metricName("m1")
                              .metricType(TimeSeriesMetricType.THROUGHPUT)
                              .timeSeriesGroupValues(Sets.newHashSet(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                                                         .groupName("g1")
                                                                         .metricValue(1.0)
                                                                         .timeStamp(start)
                                                                         .build()))
                              .build());

    timeSeriesRecords.add(TimeSeriesRecord.builder()
                              .verificationTaskId(cvConfigId)
                              .bucketStartTime(start)
                              .metricName("m2")
                              .metricType(TimeSeriesMetricType.ERROR)
                              .timeSeriesGroupValues(Sets.newHashSet(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                                                         .groupName("g1")
                                                                         .metricValue(1.0)
                                                                         .percentValue(2.0)
                                                                         .riskScore(0)
                                                                         .timeStamp(start)
                                                                         .build()))
                              .build());

    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(timeSeriesRecords);

    when(cvConfigService.list(monitoredServiceParams)).thenReturn(Arrays.asList(cvConfig));

    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, TimeRangeParams.builder().startTime(start).endTime(end).build(),
        TimeSeriesAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());
    assertThat(response).isNotNull();
    assertThat(response.getContent().get(0).getMetricDataList().first().getRisk().equals(Risk.HEALTHY));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetTimeSeriesMetricData_withMonitoredServiceIdentifierAsNULL() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    TimeRangeParams timeRangeParams = TimeRangeParams.builder().startTime(start).endTime(end).build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    String cvConfigId = generateUuid();
    when(timeSeriesRecordService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, true, false));
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    List<String> healthSourceIdentifiers = Arrays.asList(cvConfig.getIdentifier());
    Map<String, DataSourceType> cvConfigToDataSourceTypeMap = new HashMap<>();
    cvConfigToDataSourceTypeMap.put(cvConfigId, APP_DYNAMICS);
    monitoredServiceParams.setMonitoredServiceIdentifier(null);
    when(cvConfigService.list(monitoredServiceParams, healthSourceIdentifiers)).thenReturn(Arrays.asList(cvConfig));
    when(cvConfigService.getDataSourceTypeForCVConfigs(monitoredServiceParams)).thenReturn(cvConfigToDataSourceTypeMap);
    when(monitoredServiceService.list(any(), any(), any()))
        .thenReturn(Arrays.asList(MonitoredService.builder().identifier(monitoredServiceIdentifier).build()));

    TimeSeriesAnalysisFilter timeSeriesAnalysisFilter = TimeSeriesAnalysisFilter.builder()
                                                            .filter(null)
                                                            .anomalousMetricsOnly(true)
                                                            .healthSourceIdentifiers(healthSourceIdentifiers)
                                                            .build();
    PageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, timeRangeParams, timeSeriesAnalysisFilter, pageParams);

    verify(cvConfigService).list(monitoredServiceParams, healthSourceIdentifiers);
    verify(cvConfigService).getDataSourceTypeForCVConfigs(monitoredServiceParams);
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
    assertThat(response.getContent().size()).isEqualTo(10);
    response.getContent().forEach(timeSeriesMetricDataDTO -> {
      assertThat(timeSeriesMetricDataDTO.getDataSourceType()).isEqualTo(APP_DYNAMICS);
    });
  }

  private List<TimeSeriesRecord> getTimeSeriesRecords(
      String cvConfigId, boolean anomalousOnly, boolean customerDefinedUnhealthy) throws Exception {
    File file = new File(getResourceFilePath("timeseries/timeseriesRecords.json"));
    final Gson gson = new Gson();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<List<TimeSeriesRecord>>() {}.getType();
      List<TimeSeriesRecord> timeSeriesMLAnalysisRecords = gson.fromJson(br, type);
      timeSeriesMLAnalysisRecords.forEach(timeSeriesMLAnalysisRecord -> {
        // timeSeriesMLAnalysisRecord.setCvConfigId(cvConfigId);
        timeSeriesMLAnalysisRecord.setVerificationTaskId(cvConfigId);
        timeSeriesMLAnalysisRecord.setBucketStartTime(Instant.parse("2020-07-07T02:40:00.000Z"));
        if (timeSeriesMLAnalysisRecord.getMetricName().equals("Calls per Minute")) {
          Random r = new Random();
          timeSeriesMLAnalysisRecord.getTimeSeriesGroupValues().forEach(
              timeSeriesGroupValue -> timeSeriesGroupValue.setPercentValue(100 * Math.abs(r.nextDouble())));
        }
        timeSeriesMLAnalysisRecord.getTimeSeriesGroupValues().forEach(groupVal -> {
          Instant baseTime = Instant.parse("2020-07-07T02:40:00.000Z");
          Random random = new Random();
          groupVal.setTimeStamp(baseTime.plus(random.nextInt(4), ChronoUnit.MINUTES));
          if (anomalousOnly) {
            groupVal.setRiskScore(2);
          } else if (customerDefinedUnhealthy) {
            groupVal.setRiskScore(4);
          }
        });
      });
      return timeSeriesMLAnalysisRecords;
    }
  }
}
