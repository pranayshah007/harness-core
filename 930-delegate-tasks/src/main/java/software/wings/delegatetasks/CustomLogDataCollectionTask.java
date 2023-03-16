/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.delegatetasks.cv.CVConstants.AZURE_BASE_URL;
import static software.wings.delegatetasks.cv.CVConstants.AZURE_TOKEN_URL;
import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.NON_HOST_PREVIOUS_ANALYSIS;
import static software.wings.delegatetasks.cv.CVConstants.URL_BODY_APPENDER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.time.Timestamp;

import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.helpers.ext.apm.APMRestClient;
import software.wings.service.impl.analysis.AzureLogAnalyticsConnectionDetails;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.log.LogResponseParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * @author Praveen
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CV)
public class CustomLogDataCollectionTask extends AbstractDelegateDataCollectionTask {
  @Inject private LogAnalysisStoreService logAnalysisStoreService;
  @Inject private RequestExecutor requestExecutor;
  private CustomLogDataCollectionInfo dataCollectionInfo;
  private Map<String, String> decryptedFields = new HashMap<>();
  private static final String DATADOG_API_MASK = "api_key=([^&]*)&application_key=([^&]*)";

  // special case for azure. This is unfortunately a hack
  private AzureLogAnalyticsConnectionDetails azureLogAnalyticsConnectionDetails;

  public CustomLogDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DelegateStateType getStateType() {
    return dataCollectionInfo.getStateType();
  }

  @Override
  protected int getInitialDelayMinutes() {
    return dataCollectionInfo.getDelayMinutes();
  }

  @Override
  protected boolean is24X7Task() {
    return getTaskType().equals(TaskType.CUSTOM_COLLECT_24_7_LOG_DATA.name());
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    dataCollectionInfo = (CustomLogDataCollectionInfo) parameters;
    log.info("Log collection - dataCollectionInfo: {}", dataCollectionInfo);
    if (!isEmpty(dataCollectionInfo.getEncryptedDataDetails())) {
      char[] decryptedValue;
      for (EncryptedDataDetail encryptedDataDetail : dataCollectionInfo.getEncryptedDataDetails()) {
        decryptedValue = encryptionService.getDecryptedValue(encryptedDataDetail, false);
        if (decryptedValue != null) {
          decryptedFields.put(encryptedDataDetail.getFieldName(), new String(decryptedValue));
        }
      }
    }
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskResult.DataCollectionTaskStatus.SUCCESS)
        .stateType(dataCollectionInfo.getStateType())
        .build();
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) {
    return new LogDataCollector(getTaskId(), dataCollectionInfo, taskResult);
  }

  private APMRestClient getRestClient(final String baseUrl) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(getUnsafeHttpClient(baseUrl))
                                  .build();
    return retrofit.create(APMRestClient.class);
  }

  private class LogDataCollector implements Runnable {
    private final CustomLogDataCollectionInfo dataCollectionInfo;
    private long collectionStartTime;
    private boolean firstDataCollectionCompleted;
    private long lastEndTime;
    private int logCollectionMinute;
    private DataCollectionTaskResult taskResult;
    private String delegateTaskId;

    private LogDataCollector(
        String delegateTaskId, CustomLogDataCollectionInfo dataCollectionInfo, DataCollectionTaskResult taskResult) {
      this.delegateTaskId = delegateTaskId;
      this.dataCollectionInfo = dataCollectionInfo;
      this.logCollectionMinute = is24X7Task() ? (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getEndTime())
                                              : dataCollectionInfo.getStartMinute();
      this.collectionStartTime = getCollectionStartTime();
      this.taskResult = taskResult;
      this.lastEndTime = isPerMinuteWorkflowState() && !is24X7Task()
          ? TimeUnit.MINUTES.toMillis(dataCollectionInfo.getStartMinute())
          : Timestamp.minuteBoundary(dataCollectionInfo.getStartTime());
    }

    private long getCollectionStartTime() {
      if (is24X7Task()) {
        return dataCollectionInfo.getStartTime();
      }

      if (isPerMinuteWorkflowState()) {
        return TimeUnit.MINUTES.toMillis(dataCollectionInfo.getStartMinute());
      }

      return Timestamp.minuteBoundary(dataCollectionInfo.getStartTime());
    }

    private boolean isPerMinuteWorkflowState() {
      return dataCollectionInfo.getStateType().equals(DelegateStateType.DATA_DOG_LOG);
    }

    private Map<String, String> fetchAdditionalHeaders(CustomLogDataCollectionInfo dataCollectionInfo) {
      // Special case for getting the bearer token for azure log analytics
      if (!dataCollectionInfo.getBaseUrl().contains(AZURE_BASE_URL)) {
        return null;
      }
      Map<String, Object> resolvedOptions = resolveDollarReferences(dataCollectionInfo.getOptions());
      String clientId = azureLogAnalyticsConnectionDetails == null ? (String) resolvedOptions.get("client_id")
                                                                   : azureLogAnalyticsConnectionDetails.getClientId();
      String clientSecret = azureLogAnalyticsConnectionDetails == null
          ? (String) resolvedOptions.get("client_secret")
          : azureLogAnalyticsConnectionDetails.getClientSecret();
      String tenantId = azureLogAnalyticsConnectionDetails == null ? (String) resolvedOptions.get("tenant_id")
                                                                   : azureLogAnalyticsConnectionDetails.getTenantId();
      if (azureLogAnalyticsConnectionDetails == null) {
        // saving the details in this object so we can remove the details from the request parameters
        azureLogAnalyticsConnectionDetails = AzureLogAnalyticsConnectionDetails.builder()
                                                 .clientId(clientId)
                                                 .clientSecret(clientSecret)
                                                 .tenantId(tenantId)
                                                 .build();

        dataCollectionInfo.getOptions().remove("client_id");
        dataCollectionInfo.getOptions().remove("tenant_id");
        dataCollectionInfo.getOptions().remove("client_secret");
      }

      Preconditions.checkNotNull(
          clientId, "client_id parameter cannot be null when collecting data from azure log analytics");
      Preconditions.checkNotNull(
          tenantId, "tenant_id parameter cannot be null when collecting data from azure log analytics");
      Preconditions.checkNotNull(
          clientSecret, "client_secret parameter cannot be null when collecting data from azure log analytics");
      String urlForToken = tenantId + "/oauth2/token";

      Map<String, String> bearerTokenHeader = new HashMap<>();
      bearerTokenHeader.put("Content-Type", "application/x-www-form-urlencoded");
      Call<Object> bearerTokenCall = getRestClient(AZURE_TOKEN_URL)
                                         .getAzureBearerToken(urlForToken, bearerTokenHeader, "client_credentials",
                                             clientId, AZURE_BASE_URL, clientSecret);

      Object response = requestExecutor.executeRequest(bearerTokenCall);
      Map<String, Object> responseMap = new JSONObject(JsonUtils.asJson(response)).toMap();
      String bearerToken = (String) responseMap.get("access_token");

      String headerVal = "Bearer " + bearerToken;
      Map<String, String> header = new HashMap<>();
      header.put("Authorization", headerVal);
      return header;
    }

    private BiMap<String, Object> resolveDollarReferences(Map<String, String> input) {
      BiMap<String, Object> output = HashBiMap.create();
      if (input == null) {
        return output;
      }
      for (Map.Entry<String, String> entry : input.entrySet()) {
        String headerVal = entry.getValue();
        if (!headerVal.contains("${")) {
          output.put(entry.getKey(), entry.getValue());
          continue;
        }
        while (headerVal.contains("${")) {
          int startIndex = headerVal.indexOf("${");
          int endIndex = headerVal.indexOf('}', startIndex);
          String fieldName = headerVal.substring(startIndex + 2, endIndex);
          String headerBeforeIndex = headerVal.substring(0, startIndex);

          headerVal = headerBeforeIndex + decryptedFields.get(fieldName) + headerVal.substring(endIndex + 1);
          output.put(entry.getKey(), headerVal);
        }
      }

      return output;
    }

    private Map<String, String> getStringsToMask() {
      Map<String, String> maskFields = new HashMap<>();
      if (isNotEmpty(decryptedFields)) {
        decryptedFields.forEach((k, v) -> { maskFields.put(v, "<" + k + ">"); });
      }
      return maskFields;
    }

    private long getEndTime(long startTime) {
      if (is24X7Task()) {
        return dataCollectionInfo.getEndTime();
      }
      if (!firstDataCollectionCompleted) {
        return startTime + TimeUnit.MINUTES.toMillis(1);
      }
      long possibleEndTime = startTime + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getCollectionFrequency());
      return Math.min(
          possibleEndTime, collectionStartTime + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getCollectionTime()));
    }

    private String fetchLogs(long startTime, long endTime, String url, Map<String, String> headers,
        Map<String, String> options, String jsonBody, String query, String host, String hostNameSeparator) {
      try {
        BiMap<String, Object> headersBiMap = resolveDollarReferences(headers);
        BiMap<String, Object> optionsBiMap = resolveDollarReferences(options);

        String bodyStr = null;
        // We're doing this check because in SG24/7 we allow only one logCollection. So the body is present in the
        // dataCollectionInfo. In workflow there can be one body per logCollection in setup.
        // So we're taking care of both.
        String[] urlAndBody = url.split(URL_BODY_APPENDER);
        url = urlAndBody[0];
        if (isEmpty(jsonBody)) {
          bodyStr = urlAndBody.length > 1 ? urlAndBody[1] : "";
        } else {
          bodyStr = jsonBody;
        }
        String resolvedUrl =
            CustomDataCollectionUtils.resolvedUrl(url, host, startTime, endTime, dataCollectionInfo.getQuery());
        resolvedUrl = CustomDataCollectionUtils.resolveDollarReferences(resolvedUrl, decryptedFields);

        String resolvedBodyStr =
            CustomDataCollectionUtils.resolvedUrl(bodyStr, host, startTime, endTime, dataCollectionInfo.getQuery());
        resolvedBodyStr = CustomDataCollectionUtils.resolveDollarReferences(resolvedBodyStr, decryptedFields);
        Map<String, Object> resolvedBody = isNotEmpty(resolvedBodyStr) ? new JSONObject(resolvedBodyStr).toMap() : null;

        Call<Object> request;
        if (isNotEmpty(resolvedBody)) {
          request = getRestClient(dataCollectionInfo.getBaseUrl())
                        .postCollect(resolvedUrl, headersBiMap, optionsBiMap, resolvedBody);
        } else {
          request = getRestClient(dataCollectionInfo.getBaseUrl()).collect(resolvedUrl, headersBiMap, optionsBiMap);
        }
        ThirdPartyApiCallLog apiCallLog =
            ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId()));
        apiCallLog.setTitle("Fetch request to: " + dataCollectionInfo.getBaseUrl());
        Object response = requestExecutor.executeRequest(apiCallLog, request, getStringsToMask());
        return JsonUtils.asJson(response);

      } catch (Exception ex) {
        String err = ex.getMessage()
            + "Exception occurred while fetching logs. StateExecutionId: " + dataCollectionInfo.getStateExecutionId();
        log.error(err);
        throw new WingsException(err);
      }
    }

    private boolean shouldCollectData() {
      if (is24X7Task()) {
        return true;
      }
      if (!firstDataCollectionCompleted) {
        log.info("First data not yet collected. Returning true");
        return true;
      }
      long currentTime = Timestamp.currentMinuteBoundary();
      long lastCollectionTime = lastEndTime + TimeUnit.MINUTES.toMillis(1);
      if ((int) TimeUnit.MILLISECONDS.toMinutes(currentTime - lastCollectionTime)
              % dataCollectionInfo.getCollectionFrequency()
          == 0) {
        log.info("shouldCollectData is {} for minute {}, lastCollectionTime {}", true, currentTime, lastCollectionTime);
        return true;
      }

      if (currentTime > collectionStartTime
              + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getCollectionTime() + getInitialDelayMinutes())) {
        log.info("ShouldCollectDataCollection is {} for minute {}, collectionStartMinute {}", true, currentTime,
            collectionStartTime);
        return true;
      }

      return false;
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      int retry = 0;
      if (!shouldCollectData()) {
        return;
      }
      while (!completed.get() && retry < RETRIES) {
        try {
          final long startTime = lastEndTime;
          final long endTime = getEndTime(startTime);
          if (!is24X7Task() && !isPerMinuteWorkflowState()) {
            logCollectionMinute = (int) (TimeUnit.MILLISECONDS.toMinutes(endTime - collectionStartTime) - 1);
          }

          for (Map.Entry<String, Map<String, CustomLogResponseMapper>> logDataInfo :
              dataCollectionInfo.getLogResponseDefinition().entrySet()) {
            List<LogElement> logs = new ArrayList<>();
            String tempHost = dataCollectionInfo.getHosts().iterator().next();
            Map<String, String> additionalHeaders = fetchAdditionalHeaders(dataCollectionInfo);
            if (isNotEmpty(additionalHeaders)) {
              if (dataCollectionInfo.getHeaders() == null) {
                dataCollectionInfo.setHeaders(new HashMap<>());
              }
              additionalHeaders.forEach((key, val) -> dataCollectionInfo.getHeaders().put(key, val));
            }
            // go fetch the logs first
            if (!dataCollectionInfo.isShouldDoHostBasedFiltering()) {
              // this query is not host based. So we should not make one call per host
              String searchResponse = fetchLogs(startTime, endTime, logDataInfo.getKey(),
                  dataCollectionInfo.getHeaders(), dataCollectionInfo.getOptions(), dataCollectionInfo.getJsonBody(),
                  dataCollectionInfo.getQuery(), tempHost, dataCollectionInfo.getHostnameSeparator());

              LogResponseParser.LogResponseData data = new LogResponseParser.LogResponseData(searchResponse,
                  dataCollectionInfo.getHosts(), dataCollectionInfo.isShouldDoHostBasedFiltering(),
                  dataCollectionInfo.isFixedHostName(), logDataInfo.getValue());
              // parse the results that were fetched.
              List<LogElement> curLogs = new LogResponseParser().extractLogs(data);
              logs.addAll(curLogs);
            } else {
              dataCollectionInfo.getHosts().forEach(host -> {
                String searchResponse = fetchLogs(startTime, endTime, logDataInfo.getKey(),
                    dataCollectionInfo.getHeaders(), dataCollectionInfo.getOptions(), dataCollectionInfo.getJsonBody(),
                    dataCollectionInfo.getQuery(), host, dataCollectionInfo.getHostnameSeparator());

                LogResponseParser.LogResponseData data = new LogResponseParser.LogResponseData(searchResponse,
                    dataCollectionInfo.getHosts(), dataCollectionInfo.isShouldDoHostBasedFiltering(),
                    dataCollectionInfo.isFixedHostName(), logDataInfo.getValue());
                // parse the results that were fetched.
                List<LogElement> curLogs = new LogResponseParser().extractLogs(data);
                logs.addAll(curLogs);
              });
            }

            int i = 0;
            Set<Integer> collectionMinuteSet = new HashSet<>();
            collectionMinuteSet.add(logCollectionMinute);
            for (LogElement logObject : logs) {
              long timestamp = logObject.getTimeStamp();

              if (logObject.getTimeStamp() != 0 && !is24X7Task() && !isPerMinuteWorkflowState()) {
                int collectionMin =
                    (int) ((Timestamp.minuteBoundary(timestamp) - collectionStartTime) / TimeUnit.MINUTES.toMillis(1));
                logObject.setLogCollectionMinute(collectionMin);
                if (collectionMin >= 0) {
                  collectionMinuteSet.add(collectionMin);
                }
              } else {
                logObject.setLogCollectionMinute(logCollectionMinute);
                collectionMinuteSet.add(logCollectionMinute);
              }

              logObject.setClusterLabel(String.valueOf(i++));
              logObject.setQuery(dataCollectionInfo.getQuery());
              if (logObject.getHost() == null) {
                logObject.setHost(tempHost);
              }
            }

            List<LogElement> filteredLogs = new ArrayList<>(logs);
            Set<String> allHosts = new HashSet<>(dataCollectionInfo.getHosts());
            if (dataCollectionInfo.isShouldDoHostBasedFiltering()) {
              filteredLogs = logs.stream()
                                 .filter(logObject -> dataCollectionInfo.getHosts().contains(logObject.getHost()))
                                 .collect(Collectors.toList());
            }
            filteredLogs.forEach(logObject -> allHosts.add(logObject.getHost()));
            for (String host : allHosts) {
              if (isNotEmpty(collectionMinuteSet)) {
                for (Integer heartBeatMinute : collectionMinuteSet) {
                  addHeartbeat(host, dataCollectionInfo, heartBeatMinute, filteredLogs);
                }
              } else {
                addHeartbeat(host, dataCollectionInfo, logCollectionMinute, filteredLogs);
              }
            }

            if (!dataCollectionInfo.isShouldDoHostBasedFiltering()) {
              addHeartbeat(NON_HOST_PREVIOUS_ANALYSIS, dataCollectionInfo, logCollectionMinute, filteredLogs);
            }

            boolean response = logAnalysisStoreService.save(dataCollectionInfo.getStateType(),
                dataCollectionInfo.getAccountId(), dataCollectionInfo.getApplicationId(),
                dataCollectionInfo.getCvConfigId(), dataCollectionInfo.getStateExecutionId(),
                dataCollectionInfo.getWorkflowId(), dataCollectionInfo.getWorkflowExecutionId(),
                dataCollectionInfo.getServiceId(), delegateTaskId, filteredLogs);
            if (!response) {
              log.error("Error while saving logs for stateExecutionId: {}", dataCollectionInfo.getStateExecutionId());
            }
          }

          if (!firstDataCollectionCompleted) {
            firstDataCollectionCompleted = true;
          }

          lastEndTime = endTime;
          if (is24X7Task() || isPerMinuteWorkflowState()
              || logCollectionMinute >= dataCollectionInfo.getCollectionTime() - 1) {
            // We are done with all data collection, so setting task status to success and quitting.
            log.info(
                "Completed Log collection task. So setting task status to success and quitting. StateExecutionId {}",
                dataCollectionInfo.getStateExecutionId());
            completed.set(true);
            taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
          }
          break;
        } catch (Throwable ex) {
          if (!(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("error fetching logs for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                logCollectionMinute, ex);
            taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
            completed.set(true);
            break;
          } else {
            /*
             * Save the exception from the first attempt. This is usually
             * more meaningful to trouble shoot.
             */
            if (retry == 1) {
              taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
            }
            log.warn("error fetching logs. Retrying in " + DATA_COLLECTION_RETRY_SLEEP + "s", ex);
            sleep(DATA_COLLECTION_RETRY_SLEEP);
          }
        }
      }

      if (completed.get()) {
        log.info("Shutting down log collection {}", dataCollectionInfo.getStateExecutionId());
        shutDownCollection();
      }
    }
  }
}
