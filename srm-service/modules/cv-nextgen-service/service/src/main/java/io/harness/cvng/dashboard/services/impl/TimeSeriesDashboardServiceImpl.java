/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SERVICE_GUARD_WINDOW_SIZE;
import static io.harness.cvng.core.utils.DateTimeUtils.roundDownToMinBoundary;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.TimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.services.DeeplinkURLService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.dashboard.beans.TimeSeriesMetricDataDTO;
import io.harness.cvng.dashboard.beans.TimeSeriesMetricDataDTO.MetricData;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.cvng.utils.CVNGParallelExecutor;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TimeSeriesDashboardServiceImpl implements TimeSeriesDashboardService {
  @Inject private CVConfigService cvConfigService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Inject private CVNGParallelExecutor cvngParallelExecutor;
  @Inject private DeeplinkURLService deeplinkURLService;

  @Override
  public PageResponse<TimeSeriesMetricDataDTO> getTimeSeriesMetricData(MonitoredServiceParams monitoredServiceParams,
      TimeRangeParams timeRangeParams, TimeSeriesAnalysisFilter timeSeriesAnalysisFilter, PageParams pageParams) {
    List<CVConfig> cvConfigs;
    if (timeSeriesAnalysisFilter.filterByHealthSourceIdentifiers()) {
      cvConfigs = cvConfigService.list(monitoredServiceParams, timeSeriesAnalysisFilter.getHealthSourceIdentifiers());
    } else {
      cvConfigs = cvConfigService.list(monitoredServiceParams);
    }
    Map<String, CVConfig> cvConfigIdToConfigMap =
        cvConfigs.stream().collect(Collectors.toMap(CVConfig::getUuid, Function.identity()));
    List<String> cvConfigIds = cvConfigs.stream().map(CVConfig::getUuid).collect(Collectors.toList());
    PageResponse<TimeSeriesMetricDataDTO> timeSeriesMetricDataDTOPageResponse =
        getMetricData(cvConfigIds, cvConfigIdToConfigMap, monitoredServiceParams, timeRangeParams.getStartTime(),
            timeRangeParams.getEndTime(), timeRangeParams.getStartTime(), pageParams, timeSeriesAnalysisFilter);
    setUpMetricDataForFullTimeRange(timeRangeParams, timeSeriesMetricDataDTOPageResponse);
    return timeSeriesMetricDataDTOPageResponse;
  }

  private void setUpMetricDataForFullTimeRange(
      TimeRangeParams timeRangeParams, PageResponse<TimeSeriesMetricDataDTO> timeSeriesMetricDataDTOPageResponse) {
    Instant startTime = roundDownToMinBoundary(timeRangeParams.getStartTime(), 1);
    Instant endTime = roundDownToMinBoundary(timeRangeParams.getEndTime(), 1);
    timeSeriesMetricDataDTOPageResponse.getContent().forEach(timeSeriesMetricDataDTO -> {
      List<MetricData> metricDataList = new ArrayList<>();
      Instant time = startTime;
      while (time.isBefore(endTime) || time.compareTo(endTime) == 0) {
        metricDataList.add(MetricData.builder().timestamp(time.toEpochMilli()).value(null).risk(Risk.NO_DATA).build());
        time = time.plus(1, ChronoUnit.MINUTES);
      }
      timeSeriesMetricDataDTO.getMetricDataList().forEach(metricData -> {
        int index = (int) ChronoUnit.MINUTES.between(startTime, Instant.ofEpochMilli(metricData.getTimestamp()));
        if (index >= 0 && index < metricDataList.size()) {
          metricDataList.set(index, metricData);
        }
      });
      SortedSet<MetricData> metricDataSortedSet = new TreeSet<>(metricDataList);
      timeSeriesMetricDataDTO.setMetricDataList(metricDataSortedSet);
    });
  }

  private PageResponse<TimeSeriesMetricDataDTO> getMetricData(List<String> cvConfigIds,
      Map<String, CVConfig> cvConfigIdToConfigMap, MonitoredServiceParams monitoredServiceParams, Instant startTime,
      Instant endTime, Instant analysisStartTime, PageParams pageParams,
      TimeSeriesAnalysisFilter timeSeriesAnalysisFilter) {
    List<Callable<List<TimeSeriesRecord>>> recordsPerId = new ArrayList<>();
    Map<String, DataSourceType> cvConfigIdToDataSourceTypeMap =
        cvConfigService.getDataSourceTypeForCVConfigs(monitoredServiceParams);
    cvConfigIds.forEach(cvConfigId -> recordsPerId.add(() -> {
      List<TimeSeriesRecord> timeSeriesRecordsfromDB =
          timeSeriesRecordService.getTimeSeriesRecordsForConfigs(Arrays.asList(cvConfigId), startTime, endTime, false);
      List<TimeSeriesRecord> timeSeriesRecords = Collections.synchronizedList(new ArrayList<>());
      if (isEmpty(timeSeriesRecordsfromDB)) {
        return timeSeriesRecords;
      }
      if (!timeSeriesAnalysisFilter.isAnomalousMetricsOnly()) {
        timeSeriesRecords.addAll(timeSeriesRecordsfromDB);
      } else {
        // it is possible that there are some transactions with good risk and some with bad.
        // We want to surface only those with bad. So filter out the good ones.

        List<TimeSeriesRecord> lastAnalysisRecords =
            timeSeriesRecordsfromDB.stream()
                .filter(record -> !record.getBucketStartTime().isBefore(analysisStartTime))
                .collect(Collectors.toList());

        Map<String, Set<String>> riskMetricGroupNamesMap = new HashMap<>();

        lastAnalysisRecords.forEach(timeSeriesRecord -> {
          Set<TimeSeriesRecord.TimeSeriesGroupValue> groupValuesWithRisk = timeSeriesRecord.getTimeSeriesGroupValues()
                                                                               .stream()
                                                                               .filter(gv -> gv.getRiskScore() > 0)
                                                                               .collect(Collectors.toSet());

          if (!riskMetricGroupNamesMap.containsKey(timeSeriesRecord.getMetricName())) {
            riskMetricGroupNamesMap.put(timeSeriesRecord.getMetricName(), new HashSet<>());
          }

          groupValuesWithRisk.forEach(
              gv -> riskMetricGroupNamesMap.get(timeSeriesRecord.getMetricName()).add(gv.getGroupName()));
        });

        for (TimeSeriesRecord tsRecord : timeSeriesRecordsfromDB) {
          String metricName = tsRecord.getMetricName();
          if (riskMetricGroupNamesMap.containsKey(metricName)) {
            Set<TimeSeriesRecord.TimeSeriesGroupValue> badGroupValues =
                tsRecord.getTimeSeriesGroupValues()
                    .stream()
                    .filter(timeSeriesGroupValue
                        -> riskMetricGroupNamesMap.get(metricName).contains(timeSeriesGroupValue.getGroupName()))
                    .collect(Collectors.toSet());

            tsRecord.setTimeSeriesGroupValues(badGroupValues);
            timeSeriesRecords.add(tsRecord);
          }
        }
      }
      return timeSeriesRecords;
    }));

    List<List<TimeSeriesRecord>> timeSeriesThatMatter = cvngParallelExecutor.executeParallel(recordsPerId);

    List<TimeSeriesRecord> timeSeriesRecords = new ArrayList<>();
    timeSeriesThatMatter.forEach(timeSeriesRecords::addAll);

    Map<String, TimeSeriesMetricDataDTO> transactionMetricDataMap = new HashMap<>();

    // make the timeseries records a flat map for transaction+metric combination
    timeSeriesRecords.forEach(record -> {
      String metricName = record.getMetricName();
      record.getTimeSeriesGroupValues().forEach(timeSeriesGroupValue -> {
        String txnName = timeSeriesGroupValue.getGroupName();
        String key = txnName + "." + metricName;
        if (isNotEmpty(timeSeriesAnalysisFilter.getFilter())
            && !txnName.toLowerCase().contains(timeSeriesAnalysisFilter.getFilter())
            && !metricName.toLowerCase().contains(timeSeriesAnalysisFilter.getFilter().toLowerCase())) {
          return;
        }
        if (!transactionMetricDataMap.containsKey(key)) {
          DataSourceType dataSourceType = cvConfigIdToDataSourceTypeMap.get(record.getVerificationTaskId());
          Optional<String> deeplinkURL =
              deeplinkURLService.buildDeeplinkURLFromCVConfig(cvConfigIdToConfigMap.get(record.getVerificationTaskId()),
                  record.getMetricIdentifier(), startTime, endTime);
          transactionMetricDataMap.put(key,
              TimeSeriesMetricDataDTO.builder()
                  .metricName(metricName)
                  .deeplinkURL(deeplinkURL.orElse(null))
                  .groupName(txnName)
                  .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
                  .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
                  .environmentIdentifier(monitoredServiceParams.getEnvironmentIdentifier())
                  .serviceIdentifier(monitoredServiceParams.getServiceIdentifier())
                  .monitoredServiceIdentifier(monitoredServiceParams.getMonitoredServiceIdentifier())
                  .metricType(record.getMetricType())
                  .dataSourceType(dataSourceType)
                  .monitoredServiceDataSourceType(
                      MonitoredServiceDataSourceType.getMonitoredServiceDataSourceType(dataSourceType))
                  .build());
        }
        TimeSeriesMetricDataDTO timeSeriesMetricDataDTO = transactionMetricDataMap.get(key);

        if (!TimeSeriesMetricType.ERROR.equals(record.getMetricType())) {
          timeSeriesMetricDataDTO.addMetricData(timeSeriesGroupValue.getMetricValue(),
              timeSeriesGroupValue.getTimeStamp().toEpochMilli(), timeSeriesGroupValue.getRiskScore());
        } else if (timeSeriesGroupValue.getPercentValue() != null) {
          timeSeriesMetricDataDTO.addMetricData(timeSeriesGroupValue.getPercentValue(),
              timeSeriesGroupValue.getTimeStamp().toEpochMilli(), timeSeriesGroupValue.getRiskScore());
        } else {
          // if there is no percent for error then zero fill
          timeSeriesMetricDataDTO.addMetricData(Double.valueOf(0), timeSeriesGroupValue.getTimeStamp().toEpochMilli(),
              timeSeriesGroupValue.getRiskScore());
        }
      });
    });
    SortedSet<TimeSeriesMetricDataDTO> sortedMetricData = new TreeSet<>(transactionMetricDataMap.values());
    // updateLastWindowRisk(sortedMetricData, endTime);
    return PageUtils.offsetAndLimit(new ArrayList<>(sortedMetricData), pageParams.getPage(), pageParams.getSize());
  }

  private void updateLastWindowRisk(SortedSet<TimeSeriesMetricDataDTO> sortedMetricData, Instant endTime) {
    // TODO: This seems buggy. Need to fix this with Sowmya's help.

    /*
     * We need to update the last 15 minutes in the UI to reflect the state which was present just after instant endTime
     * as these risks might later get updated if the next analysis window risk is high (as we use 15 min as test data in
     * LE).
     * */
    Instant lastWindowStartTime = endTime.minus(TIMESERIES_SERVICE_GUARD_WINDOW_SIZE, ChronoUnit.MINUTES);
    for (TimeSeriesMetricDataDTO timeSeriesMetricDataDTO : sortedMetricData) {
      Optional<Risk> risk = timeSeriesMetricDataDTO.getMetricDataList()
                                .stream()
                                .filter(data -> data.getTimestamp() >= lastWindowStartTime.toEpochMilli())
                                .map(MetricData::getRisk)
                                .findFirst();
      risk.ifPresent(timeSeriesRisk
          -> timeSeriesMetricDataDTO.getMetricDataList()
                 .stream()
                 .filter(data -> data.getTimestamp() >= lastWindowStartTime.toEpochMilli())
                 .forEach(data -> data.setRisk(timeSeriesRisk)));
    }
  }
}
