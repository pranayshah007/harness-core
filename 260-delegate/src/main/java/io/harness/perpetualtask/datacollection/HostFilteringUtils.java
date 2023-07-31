/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.TimeSeriesRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class HostFilteringUtils {
  static List<TimeSeriesRecord> getHostFilteredTimeSeriesRecordAndPopulateExecutionLogs(
      List<TimeSeriesRecord> timeSeriesRecords, DataCollectionInfo<?> dataCollectionInfo,
      List<DataCollectionTaskDTO.DataCollectionTaskResult.ExecutionLog> executionLogs) {
    Set<String> validHosts = getValidHostsAndPopulateExecutionLogs(
        timeSeriesRecords.stream().map(TimeSeriesRecord::getHostname).collect(Collectors.toSet()), dataCollectionInfo,
        executionLogs);
    return timeSeriesRecords.stream()
        .filter(tsr -> CollectionUtils.emptyIfNull(validHosts).contains(tsr.getHostname()))
        .collect(Collectors.toList());
  }

  static List<LogDataRecord> getHostFilteredLogDataAndPopulateExecutionLogs(List<LogDataRecord> logDataRecords,
      DataCollectionInfo<?> dataCollectionInfo,
      List<DataCollectionTaskDTO.DataCollectionTaskResult.ExecutionLog> executionLogs) {
    Set<String> validHosts = getValidHostsAndPopulateExecutionLogs(
        logDataRecords.stream().map(LogDataRecord::getHostname).collect(Collectors.toSet()), dataCollectionInfo,
        executionLogs);
    return logDataRecords.stream()
        .filter(tsr -> CollectionUtils.emptyIfNull(validHosts).contains(tsr.getHostname()))
        .collect(Collectors.toList());
  }

  private static Set<String> getValidHostsAndPopulateExecutionLogs(Set<String> hosts,
      DataCollectionInfo<?> dataCollectionInfo,
      List<DataCollectionTaskDTO.DataCollectionTaskResult.ExecutionLog> executionLogs) {
    Map<HostValidity, Set<String>> hostValidityMap = hosts.stream().collect(
        Collectors.groupingBy(host -> getHostValidity(host, dataCollectionInfo), Collectors.toSet()));
    hostValidityMap.entrySet()
        .stream()
        .filter(entry -> !entry.getKey().equals(HostValidity.VALID))
        .map(entry
            -> DataCollectionTaskDTO.DataCollectionTaskResult.ExecutionLog.builder()
                   .logLevel(ExecutionLogDTO.LogLevel.INFO)
                   .log(entry.getKey().getLogMessage(dataCollectionInfo, entry.getValue()))
                   .build())
        .forEach(executionLogs::add);
    return hostValidityMap.get(HostValidity.VALID);
  }

  private static HostValidity getHostValidity(String hostName, DataCollectionInfo<?> dataCollectionInfo) {
    if (!dataCollectionInfo.isCollectHostData() || StringUtils.isEmpty(hostName)) {
      return HostValidity.VALID;
    }
    if (CollectionUtils.isNotEmpty(dataCollectionInfo.getServiceInstances())
        && !dataCollectionInfo.getServiceInstances().contains(hostName)) {
      return HostValidity.SERVICE_INSTANCE_LIST_VALIDATION_FAILED;
    }
    if (CollectionUtils.isNotEmpty(dataCollectionInfo.getValidServiceInstanceRegExPatterns())
        && dataCollectionInfo.getValidServiceInstanceRegExPatterns().stream().noneMatch(
            regEx -> Pattern.matches(regEx, hostName))) {
      return HostValidity.REGEX_VALIDATION_FAILED;
    }
    return HostValidity.VALID;
  }
  private enum HostValidity {
    VALID,
    SERVICE_INSTANCE_LIST_VALIDATION_FAILED,
    REGEX_VALIDATION_FAILED;

    public String getLogMessage(DataCollectionInfo<?> dataCollectionInfo, Set<String> ignoredNodes) {
      if (CollectionUtils.isEmpty(ignoredNodes)) {
        return null;
      }
      switch (this) {
        case SERVICE_INSTANCE_LIST_VALIDATION_FAILED:
          return "Data from following service instances ignored as they are not in sampled node list : "
              + String.join(",", ignoredNodes);
        case REGEX_VALIDATION_FAILED:
          return "Data from following service instances ignored as they didn't pass any specified regex ["
              + String.join(",",
                  dataCollectionInfo.getValidServiceInstanceRegExPatterns() + "] :" + String.join(",", ignoredNodes));
        default:
          return null;
      }
    }
  }
}
