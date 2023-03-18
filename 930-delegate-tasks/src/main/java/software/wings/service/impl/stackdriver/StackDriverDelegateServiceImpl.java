/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.stackdriver;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.delegatetasks.cv.CVConstants.CV_DATA_COLLECTION_INTERVAL_IN_MINUTE;
import static software.wings.delegatetasks.cv.CVConstants.RATE_LIMIT_STATUS;
import static software.wings.delegatetasks.cv.CVConstants.STACKDRIVER_DEFAULT_HOST_NAME_FIELD;
import static software.wings.delegatetasks.cv.CVConstants.STACKDRIVER_DEFAULT_LOG_MESSAGE_FIELD;
import static software.wings.delegatetasks.cv.CVConstants.STACK_DRIVER_QUERY_SEPARATER;

import static java.time.Duration.ofSeconds;

import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.expression.SecretString;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.GcpConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.CustomDataCollectionUtils;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.helpers.ext.gcb.GcbService;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.Region;
import com.google.api.services.logging.v2.Logging;
import com.google.api.services.logging.v2.model.ListLogEntriesRequest;
import com.google.api.services.logging.v2.model.ListLogEntriesResponse;
import com.google.api.services.logging.v2.model.LogEntry;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.ListTimeSeriesResponse;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.http.HttpStatus;
import org.joda.time.DateTime;

/**
 * Created by Pranjal on 11/27/2018
 */
@Singleton
@Slf4j
public class StackDriverDelegateServiceImpl implements StackDriverDelegateService {
  static final int MAX_LOGS_PER_MINUTE = 10000;
  private static final FastDateFormat rfc3339 =
      FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"));

  @Inject private EncryptionService encryptionService;
  @Inject private GcpHelperService gcpHelperService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private DelegateCVActivityLogService delegateCVActivityLogService;
  @Inject private GcbService gcbService;

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(StackdriverGcpConfigTaskParams taskParams,
      StackDriverSetupTestNodeData setupTestNodeData, String hostName, ThirdPartyApiCallLog apiCallLog)
      throws IOException {
    GcpConfig gcpConfig = taskParams.getGcpConfig();
    List<EncryptedDataDetail> encryptionDetails = taskParams.getEncryptedDataDetails();
    encryptionService.decrypt(gcpConfig, encryptionDetails, false);
    String projectId = getProjectId(gcpConfig);
    Monitoring monitoring = gcpHelperService.getMonitoringService(
        gcpConfig.getServiceAccountKeyFileContent(), projectId, gcpConfig.isUseDelegateSelectors());
    String projectResource = "projects/" + projectId;
    List<ListTimeSeriesResponse> responses = new ArrayList<>();
    long startTime = setupTestNodeData.getFromTime() * TimeUnit.SECONDS.toMillis(1);
    long endTime = setupTestNodeData.getToTime() * TimeUnit.SECONDS.toMillis(1);

    if (!isEmpty(setupTestNodeData.getMetricDefinitions())) {
      setupTestNodeData.getMetricDefinitions().forEach(metricDefinition -> {
        ListTimeSeriesResponse response = getTimeSeriesResponse(
            monitoring, projectResource, gcpConfig, metricDefinition, startTime, endTime, apiCallLog.copy(), hostName);
        if (isNotEmpty(response) && isNotEmpty(response.getTimeSeries())) {
          responses.add(response);
          Preconditions.checkState(response.getTimeSeries().size() == 1,
              "Multiple time series values are returned for metric name " + metricDefinition.getMetricName()
                  + " and group name " + metricDefinition.getTxnName()
                  + ". Please add more filters to your query to return only one time series.");
        }
      });
    }

    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(true)
        .loadResponse(
            VerificationLoadResponse.builder().isLoadPresent(isNotEmpty(responses)).loadResponse(responses).build())
        .dataForNode(responses)
        .build();
  }

  @Override
  public List<String> listRegions(StackdriverGcpConfigTaskParams taskParams) {
    GcpConfig gcpConfig = taskParams.getGcpConfig();
    List<EncryptedDataDetail> encryptionDetails = taskParams.getEncryptedDataDetails();
    encryptionService.decrypt(gcpConfig, encryptionDetails, false);
    String projectId = getProjectId(gcpConfig);
    try {
      List<Region> regions =
          gcpHelperService
              .getGCEService(gcpConfig.getServiceAccountKeyFileContent(), projectId, gcpConfig.isUseDelegateSelectors())
              .regions()
              .list(projectId)
              .execute()
              .getItems();
      if (isNotEmpty(regions)) {
        return regions.stream().map(Region::getName).collect(Collectors.toList());
      }
    } catch (Exception e) {
      throw new WingsException(ErrorCode.STACKDRIVER_ERROR).addParam("reason", getMessage(e));
    }
    return Collections.emptyList();
  }

  @Override
  public Map<String, String> listForwardingRules(StackdriverGcpConfigTaskParams taskParams, String region) {
    GcpConfig gcpConfig = taskParams.getGcpConfig();
    List<EncryptedDataDetail> encryptionDetails = taskParams.getEncryptedDataDetails();
    encryptionService.decrypt(gcpConfig, encryptionDetails, false);
    String projectId = getProjectId(gcpConfig);
    try {
      List<ForwardingRule> forwardingRulesByRegion =
          gcpHelperService
              .getGCEService(gcpConfig.getServiceAccountKeyFileContent(), projectId, gcpConfig.isUseDelegateSelectors())
              .forwardingRules()
              .list(projectId, region)
              .execute()
              .getItems();
      if (isNotEmpty(forwardingRulesByRegion)) {
        return forwardingRulesByRegion.stream().collect(
            Collectors.toMap(ForwardingRule::getIPAddress, ForwardingRule::getName));
      }
    } catch (Exception e) {
      throw new WingsException(ErrorCode.STACKDRIVER_ERROR).addParam("reason", getMessage(e));
    }
    return Collections.emptyMap();
  }

  @Override
  public String createFilter(StackDriverNameSpace nameSpace, String metric, String dimensionValue) {
    String filter;
    switch (nameSpace) {
      case LOADBALANCER: {
        filter = "metric.type=\"" + metric + "\" AND resource.label.forwarding_rule_name = \"" + dimensionValue + "\"";
        break;
      }
      case POD_NAME: {
        filter = "metric.type=\"" + metric + "\" AND resource.labels.pod_name = \"" + dimensionValue + "\"";
        break;
      }
      default:
        throw new WingsException("Invalid namespace " + nameSpace);
    }
    return filter;
  }

  @VisibleForTesting
  ListTimeSeriesResponse getTimeSeriesResponse(Monitoring monitoring, String projectResource, GcpConfig config,
      StackDriverMetricDefinition metricDefinition, long startTime, long endTime, ThirdPartyApiCallLog apiCallLog,
      String hostName) {
    String filter = metricDefinition.getFilter();
    if (isNotEmpty(hostName)) {
      filter = CustomDataCollectionUtils.resolveField(filter, "${host}", hostName);
    }

    String projectId = getProjectId(config);
    apiCallLog.setTitle("Fetching metric data from project " + projectId
        + " for the time range: " + getDateFormatTime(startTime) + " to " + getDateFormatTime(endTime));
    apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());

    apiCallLog.addFieldToRequest(
        ThirdPartyApiCallField.builder().name("body").value(JsonUtils.asJson(filter)).type(FieldType.JSON).build());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name("Start Time")
                                     .value(getDateFormatTime(startTime))
                                     .type(FieldType.TIMESTAMP)
                                     .build());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name("End Time")
                                     .value(getDateFormatTime(endTime))
                                     .type(FieldType.TIMESTAMP)
                                     .build());

    ListTimeSeriesResponse response;
    try {
      Monitoring.Projects.TimeSeries.List list =
          monitoring.projects()
              .timeSeries()
              .list(projectResource)
              .setFilter(filter)
              .setAggregationGroupByFields(metricDefinition.getAggregation().getGroupByFields())
              .setAggregationAlignmentPeriod("60s")
              .setIntervalStartTime(getDateFormatTime(startTime))
              .setIntervalEndTime(getDateFormatTime(endTime))
              .setAggregationPerSeriesAligner(metricDefinition.getAggregation().getPerSeriesAligner())
              .setAggregationCrossSeriesReducer(metricDefinition.getAggregation().getCrossSeriesReducer());
      response = list.execute();
    } catch (IOException e) {
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), FieldType.TEXT);
      delegateLogService.save(config.getAccountId(), apiCallLog);
      throw new WingsException(
          "Unsuccessful response while fetching data from StackDriver. Error message: " + getMessage(e));
    }
    apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    apiCallLog.addFieldToResponse(HttpStatus.SC_OK, response, FieldType.JSON);

    delegateLogService.save(config.getAccountId(), apiCallLog);
    return response;
  }

  @Override
  public String getProjectId(GcpConfig gcpConfig) {
    return gcbService.getProjectId(gcpConfig);
  }

  @Override
  public long getTimeStamp(String data) {
    Date date;
    try {
      date = rfc3339.parse(data);
    } catch (ParseException e) {
      throw new WingsException("Unable to convert given timestamp");
    }
    return date.getTime();
  }

  @Override
  public String getDateFormatTime(long time) {
    return rfc3339.format(new Date(time));
  }

  @Override
  public VerificationNodeDataSetupResponse getLogWithDataForNode(StackdriverLogGcpConfigTaskParams taskParams,
      String stateExecutionId, String hostName, StackDriverSetupTestNodeData setupTestNodeData) {
    GcpConfig gcpConfig = taskParams.getGcpConfig();
    List<EncryptedDataDetail> encryptionDetails = taskParams.getEncryptedDataDetails();
    List<LogEntry> entries;
    List<LogEntry> serviceLevelLoad;
    final long startTime = TimeUnit.SECONDS.toMillis(setupTestNodeData.getFromTime());
    final long endTime = TimeUnit.SECONDS.toMillis(setupTestNodeData.getToTime());
    final StackDriverLogDataCollectionInfo dataCollectionInfo = StackDriverLogDataCollectionInfo.builder()
                                                                    .stateExecutionId(stateExecutionId)
                                                                    .query(setupTestNodeData.getQuery())
                                                                    .hosts(Collections.emptySet())
                                                                    .hostnameField(setupTestNodeData.getHostnameField())
                                                                    .gcpConfig(gcpConfig)
                                                                    .encryptedDataDetails(encryptionDetails)
                                                                    .build();
    try {
      // get data without host
      serviceLevelLoad = fetchLogs(dataCollectionInfo, startTime, endTime, true, false);

      if (setupTestNodeData.isServiceLevel()) {
        return VerificationNodeDataSetupResponse.builder()
            .providerReachable(true)
            .loadResponse(VerificationLoadResponse.builder()
                              .isLoadPresent(isNotEmpty(serviceLevelLoad))
                              .loadResponse(serviceLevelLoad)
                              .build())
            .build();
      }

      // get data with host
      dataCollectionInfo.setHosts(Sets.newHashSet(hostName));
      entries = fetchLogs(dataCollectionInfo, startTime, endTime, false, false);

    } catch (Exception e) {
      log.error("error fetching logs", e);
      return VerificationNodeDataSetupResponse.builder().providerReachable(false).build();
    }
    List<LogElement> logElements = new ArrayList<>();
    int clusterLabel = 0;
    if (isNotEmpty(entries)) {
      log.info("Total no. of log records found : {}", entries.size());
      for (LogEntry entry : entries) {
        LogElement logElement;
        try {
          String logMessage = JsonPath.read(entry.toString(),
              isNotEmpty(setupTestNodeData.getMessageField()) ? setupTestNodeData.getMessageField()
                                                              : STACKDRIVER_DEFAULT_LOG_MESSAGE_FIELD);
          String host = JsonPath.read(entry.toString(),
              isNotEmpty(setupTestNodeData.getHostnameField()) ? setupTestNodeData.getHostnameField()
                                                               : STACKDRIVER_DEFAULT_HOST_NAME_FIELD);

          if (isEmpty(host) || isEmpty(logMessage)) {
            continue;
          }

          long timeStamp = new DateTime(entry.getTimestamp()).getMillis();
          if (isNotEmpty(logMessage)) {
            logElement = LogElement.builder()
                             .query(setupTestNodeData.getQuery())
                             .logCollectionMinute((int) TimeUnit.MILLISECONDS.toMinutes(timeStamp))
                             .clusterLabel(String.valueOf(clusterLabel++))
                             .count(1)
                             .logMessage(logMessage)
                             .timeStamp(timeStamp)
                             .host(host)
                             .build();
            logElements.add(logElement);
          }
        } catch (Exception e) {
          log.warn("Unable to parse logEntry due to exception : ", e);
          continue;
        }
      }
    }
    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(true)
        .loadResponse(VerificationLoadResponse.builder()
                          .isLoadPresent(isNotEmpty(serviceLevelLoad))
                          .loadResponse(serviceLevelLoad)
                          .build())
        .dataForNode(logElements)
        .build();
  }

  @Override
  public List<LogEntry> fetchLogs(StackDriverLogDataCollectionInfo dataCollectionInfo, long startTime, long endTime,
      boolean is24x7Task, boolean fetchNextPage) {
    final GcpConfig gcpConfig = dataCollectionInfo.getGcpConfig();
    final List<EncryptedDataDetail> encryptionDetails = dataCollectionInfo.getEncryptedDataDetails();
    encryptionService.decrypt(gcpConfig, encryptionDetails, false);
    String projectId =
        isNotEmpty(dataCollectionInfo.getProjectId()) ? dataCollectionInfo.getProjectId() : getProjectId(gcpConfig);
    Logging logging = gcpHelperService.getLoggingResource(
        gcpConfig.getServiceAccountKeyFileContent(), projectId, gcpConfig.isUseDelegateSelectors());

    String queryField = getQueryField(dataCollectionInfo.getHostnameField(),
        new ArrayList<>(dataCollectionInfo.getHosts()), dataCollectionInfo.getQuery(), startTime, endTime, is24x7Task);

    List<LogEntry> logEntries = new ArrayList<>();
    ListLogEntriesResponse response = null;
    String nextPageToken = null;
    boolean hasReachedRateLimit;
    do {
      ListLogEntriesRequest request = new ListLogEntriesRequest();
      ThirdPartyApiCallLog apiCallLog =
          createApiCallLog(gcpConfig.getAccountId(), dataCollectionInfo.getStateExecutionId());
      apiCallLog.setTitle("Fetching log data from project " + projectId + " from " + getDateFormatTime(startTime)
          + " to " + getDateFormatTime(endTime));
      apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToRequest(ThirdPartyApiCallLog.ThirdPartyApiCallField.builder()
                                       .name("query")
                                       .value(queryField)
                                       .type(ThirdPartyApiCallLog.FieldType.JSON)
                                       .build());
      apiCallLog.addFieldToRequest(ThirdPartyApiCallLog.ThirdPartyApiCallField.builder()
                                       .name("Start Time")
                                       .value(getDateFormatTime(startTime))
                                       .type(ThirdPartyApiCallLog.FieldType.TIMESTAMP)
                                       .build());
      apiCallLog.addFieldToRequest(ThirdPartyApiCallLog.ThirdPartyApiCallField.builder()
                                       .name("End Time")
                                       .value(getDateFormatTime(endTime))
                                       .type(ThirdPartyApiCallLog.FieldType.TIMESTAMP)
                                       .build());
      request.setFilter(queryField);
      request.setProjectIds(Collections.singletonList(projectId));
      request.setPageSize(fetchNextPage ? 1000 : 10);
      if (isNotEmpty(nextPageToken)) {
        request.setPageToken(nextPageToken);
        apiCallLog.addFieldToRequest(ThirdPartyApiCallLog.ThirdPartyApiCallField.builder()
                                         .name("Next Page Token")
                                         .value(SecretString.SECRET_MASK)
                                         .type(FieldType.TEXT)
                                         .build());
      }
      try {
        response = logging.entries().list(request).execute();
        hasReachedRateLimit = false;
      } catch (GoogleJsonResponseException ge) {
        apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
        apiCallLog.addFieldToResponse(
            HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getMessage(ge), ThirdPartyApiCallLog.FieldType.TEXT);
        delegateLogService.save(gcpConfig.getAccountId(), apiCallLog);
        if (ge.getStatusCode() == RATE_LIMIT_STATUS) {
          hasReachedRateLimit = true;
          int randomNum = ThreadLocalRandom.current().nextInt(1, 5);
          log.info("Encountered Rate limiting from stackdriver. Sleeping {} seconds for state {} ", randomNum,
              apiCallLog.getStateExecutionId());
          sleep(ofSeconds(randomNum));
          continue;
        } else {
          throw new VerificationOperationException(
              ErrorCode.STACKDRIVER_ERROR, "error fetching logs from stackdriver", ge);
        }
      } catch (Exception e) {
        log.error("Error fetching logs, request {}", request, e);
        apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
        apiCallLog.addFieldToResponse(
            HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), ThirdPartyApiCallLog.FieldType.TEXT);
        delegateLogService.save(gcpConfig.getAccountId(), apiCallLog);
        throw new WingsException("Unsuccessful response while fetching data from StackDriver. Error message: " + e);
      }
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_OK, response, ThirdPartyApiCallLog.FieldType.JSON);
      delegateLogService.save(gcpConfig.getAccountId(), apiCallLog);
      nextPageToken = response.getNextPageToken();
      if (isNotEmpty(response.getEntries())) {
        logEntries.addAll(response.getEntries());
      }

      if (maxAllowedLogsReached(logEntries.size(), is24x7Task)) {
        if (is24x7Task) {
          delegateCVActivityLogService
              .getLogger(dataCollectionInfo.getAccountId(), dataCollectionInfo.getCvConfigId(),
                  TimeUnit.MILLISECONDS.toMinutes(endTime), dataCollectionInfo.getStateExecutionId(),
                  "[Time range: %t-%t]", startTime, endTime)
              .warn("Limit of " + MAX_LOGS_PER_MINUTE
                  + " logs per minute reached. Log messages will be dropped. Please refine your query.");
        } else {
          throw new DataCollectionException(
              "Limit of " + MAX_LOGS_PER_MINUTE + " logs per minute reached. Please refine your query.");
        }
        break;
      }
    } while (hasReachedRateLimit || (fetchNextPage && isNotEmpty(response) && isNotEmpty(response.getNextPageToken())));

    return logEntries;
  }

  private boolean maxAllowedLogsReached(int currentLogsSize, boolean is24x7Task) {
    return is24x7Task ? currentLogsSize >= MAX_LOGS_PER_MINUTE * CV_DATA_COLLECTION_INTERVAL_IN_MINUTE
                      : currentLogsSize >= MAX_LOGS_PER_MINUTE;
  }

  private String getQueryField(
      String hostnameField, List<String> hosts, String query, long startTime, long endTime, boolean is24X7Task) {
    String formattedStartTime = getDateFormatTime(startTime);
    String formattedEndTime = getDateFormatTime(endTime);

    StringBuilder queryBuilder = new StringBuilder(80);

    if (!is24X7Task) {
      queryBuilder.append(hostnameField).append("=(");
      for (int i = 0; i < hosts.size(); i++) {
        queryBuilder.append(hosts.get(i));
        if (i != hosts.size() - 1) {
          queryBuilder.append(" OR ");
        } else {
          queryBuilder.append(')');
        }
      }
      queryBuilder.append(STACK_DRIVER_QUERY_SEPARATER);
    }
    for (String filter : query.split("\n")) {
      queryBuilder.append(filter).append(STACK_DRIVER_QUERY_SEPARATER);
    }
    queryBuilder.append("timestamp>=\"")
        .append(formattedStartTime)
        .append("\"" + STACK_DRIVER_QUERY_SEPARATER + "timestamp<\"")
        .append(formattedEndTime);

    return queryBuilder.toString() + "\"";
  }

  @Override
  public Object getLogSample(
      StackdriverLogGcpConfigTaskParams taskParams, String guid, String query, long startTime, long endTime) {
    GcpConfig gcpConfig = taskParams.getGcpConfig();
    List<EncryptedDataDetail> encryptionDetails = taskParams.getEncryptedDataDetails();
    final List<LogEntry> logEntries = fetchLogs(StackDriverLogDataCollectionInfo.builder()
                                                    .stateExecutionId(guid)
                                                    .query(query)
                                                    .hosts(Collections.emptySet())
                                                    .gcpConfig(gcpConfig)
                                                    .encryptedDataDetails(encryptionDetails)
                                                    .build(),
        startTime, endTime, true, false);
    if (isEmpty(logEntries)) {
      return null;
    }
    return logEntries.get(0);
  }
}
