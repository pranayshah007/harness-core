/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.delegatetasks.cv.CVConstants.URL_STRING;

import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.NewRelicConfig;
import software.wings.beans.NewRelicDeploymentMarkerPayload;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.newrelic.NewRelicRestClient;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Created by rsingh on 8/28/17.
 */

@Singleton
@Slf4j
public class NewRelicDelgateServiceImpl implements NewRelicDelegateService {
  public static final String METRIC_NAME_NON_SPECIAL_CHARS = "METRIC_NAME_NON_SPECIAL_CHARS";
  public static final String METRIC_NAME_SPECIAL_CHARS = "METRIC_NAME_SPECIAL_CHAR";
  private static final Set<String> txnsToCollect = Sets.newHashSet("WebTransaction/", "WebTransactionTotalTime/");
  private static final int METRIC_DATA_QUERY_BATCH_SIZE = 15;
  private static final int MIN_RPM = 1;
  private static final String NEW_RELIC_DATE_FORMAT = "YYYY-MM-dd'T'HH:mm:ssZ";
  private static final Pattern METRIC_NAME_PATTERN_NO_SPECIAL_CHAR = Pattern.compile("[a-zA-Z0-9_\\-\\+\\s/]*");
  private static final List<String> NOT_ALLOWED_STRINGS = Lists.newArrayList("{", "}");

  @Inject private EncryptionService encryptionService;
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private RequestExecutor requestExecutor;

  public static Map<String, List<Set<String>>> batchMetricsToCollect(
      Collection<NewRelicMetric> metrics, boolean checkNotAllowedStrings) {
    Map<String, List<Set<String>>> rv = new HashMap<>();
    rv.put(METRIC_NAME_NON_SPECIAL_CHARS, new ArrayList<>());
    rv.put(METRIC_NAME_SPECIAL_CHARS, new ArrayList<>());

    Set<String> batchedNonSpecialCharMetrics = new HashSet<>();
    Set<String> batchedSpecialCharMetrics = new HashSet<>();
    for (NewRelicMetric metric : metrics) {
      if (checkNotAllowedStrings && containsNotAllowedChars(metric.getName())) {
        log.info("metric {} contains not allowed characters {}. This will skip analysis", metric.getName(),
            NOT_ALLOWED_STRINGS);
        continue;
      }
      if (METRIC_NAME_PATTERN_NO_SPECIAL_CHAR.matcher(metric.getName()).matches()) {
        batchedNonSpecialCharMetrics.add(metric.getName());
      } else {
        batchedSpecialCharMetrics.add(metric.getName());
      }

      if (batchedNonSpecialCharMetrics.size() == METRIC_DATA_QUERY_BATCH_SIZE) {
        rv.get(METRIC_NAME_NON_SPECIAL_CHARS).add(batchedNonSpecialCharMetrics);
        batchedNonSpecialCharMetrics = new HashSet<>();
      }

      if (batchedSpecialCharMetrics.size() == METRIC_DATA_QUERY_BATCH_SIZE) {
        rv.get(METRIC_NAME_SPECIAL_CHARS).add(batchedSpecialCharMetrics);
        batchedSpecialCharMetrics = new HashSet<>();
      }
    }

    if (!batchedNonSpecialCharMetrics.isEmpty()) {
      rv.get(METRIC_NAME_NON_SPECIAL_CHARS).add(batchedNonSpecialCharMetrics);
    }

    if (!batchedSpecialCharMetrics.isEmpty()) {
      rv.get(METRIC_NAME_SPECIAL_CHARS).add(batchedSpecialCharMetrics);
    }

    return rv;
  }

  private static boolean containsNotAllowedChars(String name) {
    for (String str : NOT_ALLOWED_STRINGS) {
      if (name.contains(str)) {
        return true;
      }
    }
    return false;
  }

  public static Set<String> getApdexMetricNames(Collection<String> metricNames) {
    final Set<String> rv = new HashSet<>();
    for (String metricName : metricNames) {
      if (metricName.startsWith("WebTransaction/")) {
        rv.add(metricName.replace("WebTransaction", "Apdex"));
      }
    }

    return rv;
  }

  public static Set<String> getErrorMetricNames(Collection<String> metricNames) {
    final Set<String> rv = new HashSet<>();
    for (String metricName : metricNames) {
      rv.add("Errors/" + metricName);
    }

    return rv;
  }

  @Override

  public boolean validateConfig(NewRelicConfig newRelicConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    validateApplications(newRelicConfig, encryptedDataDetails, 1);
    return true;
  }

  private List<NewRelicApplication> validateApplications(
      NewRelicConfig newRelicConfig, List<EncryptedDataDetail> encryptedDataDetails, int pageCount) {
    final Call<NewRelicApplicationsResponse> request =
        getNewRelicRestClient(newRelicConfig)
            .listAllApplications(getApiKey(newRelicConfig, encryptedDataDetails), pageCount);
    return requestExecutor.executeRequest(request).getApplications();
  }

  @Override
  public NewRelicApplication resolveNewRelicApplicationName(@NotNull NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String newRelicApplicationName, ThirdPartyApiCallLog apiCallLog) {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(newRelicConfig.getAccountId(), null);
    }
    final Call<NewRelicApplicationsResponse> request =
        getNewRelicRestClient(newRelicConfig)
            .listAllApplicationsByNameFilter(getApiKey(newRelicConfig, encryptedDataDetails), newRelicApplicationName);
    List<NewRelicApplication> newRelicApplications =
        requestExecutor.executeRequest(apiCallLog, request).getApplications();
    if (isEmpty(newRelicApplications)) {
      throw new WingsException(
          "Application Name " + newRelicApplicationName + " could not be resolved to a valid NewRelic Application.");
    }
    if (newRelicApplications.size() > 1) {
      for (NewRelicApplication application : newRelicApplications) {
        if (isNotEmpty(newRelicApplicationName) && newRelicApplicationName.equals(application.getName())) {
          return application;
        }
      }
      throw new WingsException(
          "Application Name " + newRelicApplicationName + " matched more than one NewRelic Application.");
    }

    if (isNotEmpty(newRelicApplicationName) && !newRelicApplicationName.equals(newRelicApplications.get(0).getName())) {
      throw new WingsException(
          "Application Name " + newRelicApplicationName + " does not match a NewRelic Application.");
    }
    return newRelicApplications.get(0);
  }

  @Override
  public NewRelicApplication resolveNewRelicApplicationId(@NotNull NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String newRelicApplicationId, ThirdPartyApiCallLog apiCallLog) {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(newRelicConfig.getAccountId(), null);
    }
    final Call<NewRelicApplicationsResponse> request =
        getNewRelicRestClient(newRelicConfig)
            .listAllApplicationsByIdFilter(getApiKey(newRelicConfig, encryptedDataDetails), newRelicApplicationId);
    List<NewRelicApplication> newRelicApplications =
        requestExecutor.executeRequest(apiCallLog, request).getApplications();
    if (isEmpty(newRelicApplications)) {
      throw new WingsException(
          "Application ID " + newRelicApplicationId + " could not be resolved to a valid NewRelic Application.");
    }
    if (newRelicApplications.size() > 1) {
      throw new WingsException(
          "Application ID " + newRelicApplicationId + " matched more than one NewRelic Application.");
    }

    return newRelicApplications.get(0);
  }

  @Override
  public List<NewRelicApplication> getAllApplications(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog) throws IOException {
    List<NewRelicApplication> rv = new ArrayList<>();
    List<NewRelicApplication> largeApps = validateApplications(newRelicConfig, encryptedDataDetails, 5);
    if (!isEmpty(largeApps)) {
      rv.add(NewRelicApplication.builder().id(-1).build());
      return rv;
    }

    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(newRelicConfig.getAccountId(), null);
    }
    int pageCount = 1;
    while (true) {
      apiCallLog = apiCallLog.copy();
      apiCallLog.setTitle("Fetching applications from " + newRelicConfig.getNewRelicUrl());
      apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      final Call<NewRelicApplicationsResponse> request =
          getNewRelicRestClient(newRelicConfig)
              .listAllApplications(getApiKey(newRelicConfig, encryptedDataDetails), pageCount);
      List<NewRelicApplication> apps = callAndParseApplicationAPI(request, newRelicConfig, apiCallLog);
      if (isEmpty(apps)) {
        break;
      }
      rv.addAll(apps);
      pageCount++;
    }

    return rv;
  }

  private List<NewRelicApplication> callAndParseApplicationAPI(Call<NewRelicApplicationsResponse> request,
      NewRelicConfig newRelicConfig, ThirdPartyApiCallLog apiCallLog) throws IOException {
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name(URL_STRING)
                                     .value(request.request().url().toString())
                                     .type(FieldType.URL)
                                     .build());

    Response<NewRelicApplicationsResponse> response;
    try {
      response = request.execute();
    } catch (Exception e) {
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), FieldType.TEXT);
      delegateLogService.save(newRelicConfig.getAccountId(), apiCallLog);
      throw new WingsException("Unsuccessful response while fetching data from NewRelic. Error message: "
          + e.getMessage() + " Request: " + request.request().url().toString());
    }
    apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    if (response.isSuccessful()) {
      apiCallLog.addFieldToResponse(response.code(), response.body(), FieldType.JSON);
      List<NewRelicApplication> applications = response.body().getApplications();
      delegateLogService.save(newRelicConfig.getAccountId(), apiCallLog);
      if (isEmpty(applications)) {
        return null;
      } else {
        return applications;
      }
    } else {
      apiCallLog.addFieldToResponse(response.code(), response.errorBody().string(), FieldType.TEXT);
      delegateLogService.save(newRelicConfig.getAccountId(), apiCallLog);
      throw new WingsException(
          ErrorCode.NEWRELIC_ERROR, response.errorBody().string(), EnumSet.of(ReportTarget.UNIVERSAL));
    }
  }

  @Override
  public List<NewRelicApplicationInstance> getApplicationInstances(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicApplicationId, ThirdPartyApiCallLog apiCallLog) {
    List<NewRelicApplicationInstance> rv = new ArrayList<>();
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(newRelicConfig.getAccountId(), null);
    }
    int pageCount = 1;
    while (true) {
      apiCallLog = apiCallLog.copy();
      apiCallLog.setTitle("Fetching application instances from " + newRelicConfig.getNewRelicUrl());
      apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      final Call<NewRelicApplicationInstancesResponse> request =
          getNewRelicRestClient(newRelicConfig)
              .listAppInstances(getApiKey(newRelicConfig, encryptedDataDetails), newRelicApplicationId, pageCount);
      NewRelicApplicationInstancesResponse response;
      try {
        response = requestExecutor.executeRequest(apiCallLog, request);
      } catch (Exception e) {
        throw new WingsException("Unsuccessful response while fetching data from NewRelic. Error message: "
            + e.getMessage() + " Request: " + request.request().url().toString());
      }
      List<NewRelicApplicationInstance> applicationInstances = response.getApplication_instances();
      if (isEmpty(applicationInstances)) {
        break;
      } else {
        rv.addAll(applicationInstances);
      }
      pageCount++;
    }

    return rv;
  }

  @Override
  public List<NewRelicMetric> getTxnsWithData(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptionDetails, long applicationId, boolean checkNotAllowedStrings,
      ThirdPartyApiCallLog apiCallLog) {
    Set<NewRelicMetric> txnNameToCollect =
        getTxnNameToCollect(newRelicConfig, encryptionDetails, applicationId, apiCallLog);
    Set<NewRelicMetric> txnsWithDataInLastHour = getTxnsWithDataInLastHour(
        txnNameToCollect, newRelicConfig, encryptionDetails, applicationId, checkNotAllowedStrings, apiCallLog);
    return Lists.newArrayList(txnsWithDataInLastHour);
  }

  @Override
  public Set<NewRelicMetric> getTxnNameToCollect(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicAppId, ThirdPartyApiCallLog apiCallLog) {
    Set<NewRelicMetric> newRelicMetrics = new HashSet<>();
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(newRelicConfig.getAccountId(), null);
    }
    for (String txnName : txnsToCollect) {
      apiCallLog.setTitle("Fetching web transactions names from " + newRelicConfig.getNewRelicUrl());
      final Call<NewRelicMetricResponse> request =
          getNewRelicRestClient(newRelicConfig)
              .listMetricNames(getApiKey(newRelicConfig, encryptedDataDetails), newRelicAppId, txnName);
      NewRelicMetricResponse response;
      try {
        response = requestExecutor.executeRequest(apiCallLog, request);
      } catch (Exception e) {
        throw new WingsException("Unsuccessful response while fetching data from NewRelic. Error message: "
            + e.getMessage() + " Request: " + request.request().url().toString());
      }
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      if (isNotEmpty(response.getMetrics())) {
        response.getMetrics().forEach(metric -> {
          if (metric.getName().startsWith(txnName)) {
            newRelicMetrics.add(metric);
          }
        });
      }
    }
    return newRelicMetrics;
  }

  @Override
  public Set<NewRelicMetric> getTxnsWithDataInLastHour(Collection<NewRelicMetric> metrics,
      NewRelicConfig newRelicConfig, List<EncryptedDataDetail> encryptedDataDetails, long applicationId,
      boolean checkNotAllowedStrings, ThirdPartyApiCallLog apiCallLog) {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(newRelicConfig.getAccountId(), null);
    }
    Map<String, NewRelicMetric> webTransactionMetrics = new HashMap<>();
    for (NewRelicMetric metric : metrics) {
      webTransactionMetrics.put(metric.getName(), metric);
    }
    Map<String, List<Set<String>>> metricBatches = batchMetricsToCollect(metrics, checkNotAllowedStrings);
    List<Callable<Set<String>>> metricDataCallabels = new ArrayList<>();
    final ThirdPartyApiCallLog apiCallLogCopy = apiCallLog.copy();
    metricBatches.forEach((metricTypeName, metricSets) -> {
      if (metricTypeName.equals(METRIC_NAME_NON_SPECIAL_CHARS)) {
        metricSets.forEach(metricNames
            -> metricDataCallabels.add(()
                                           -> getMetricsWithNoData(metricNames, newRelicConfig, encryptedDataDetails,
                                               applicationId, apiCallLogCopy, true)));
        return;
      }

      if (metricTypeName.equals(METRIC_NAME_SPECIAL_CHARS)) {
        metricSets.forEach(metricNames
            -> metricDataCallabels.add(()
                                           -> getMetricsWithNoData(metricNames, newRelicConfig, encryptedDataDetails,
                                               applicationId, apiCallLogCopy, false)));
        return;
      }

      throw new IllegalStateException("Invalid metric batch type " + metricTypeName);
    });
    List<Optional<Set<String>>> results = dataCollectionService.executeParrallel(metricDataCallabels);
    results.forEach(result -> {
      if (result.isPresent()) {
        for (String metricName : result.get()) {
          webTransactionMetrics.remove(metricName);
        }
      }
    });
    return new HashSet<>(webTransactionMetrics.values());
  }

  private Set<String> getMetricsWithNoData(Set<String> metricNames, NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long applicationId, ThirdPartyApiCallLog apiCallLog,
      boolean failOnException) throws IOException {
    try {
      final long currentTime = System.currentTimeMillis();
      Set<String> metricsWithNoData = Sets.newHashSet(metricNames);
      NewRelicMetricData metricData = getMetricDataApplication(newRelicConfig, encryptedDataDetails, applicationId,
          metricNames, currentTime - TimeUnit.HOURS.toMillis(1), currentTime, true, apiCallLog.copy());

      if (metricData == null) {
        throw new WingsException(ErrorCode.NEWRELIC_ERROR,
            "Unable to get NewRelic metric data for metric name collection " + newRelicConfig,
            EnumSet.of(ReportTarget.UNIVERSAL));
      }

      metricsWithNoData.removeAll(metricData.getMetrics_found());

      NewRelicMetricData errorMetricData = getMetricDataApplication(newRelicConfig, encryptedDataDetails, applicationId,
          getErrorMetricNames(metricNames), currentTime - TimeUnit.HOURS.toMillis(1), currentTime, true,
          apiCallLog.copy());

      metricsWithNoData.removeAll(metricData.getMetrics_found());

      for (NewRelicMetricData.NewRelicMetricSlice metric : metricData.getMetrics()) {
        for (NewRelicMetricData.NewRelicMetricTimeSlice timeSlice : metric.getTimeslices()) {
          final String webTxnJson = JsonUtils.asJson(timeSlice.getValues());
          NewRelicWebTransactions webTransactions = JsonUtils.asObject(webTxnJson, NewRelicWebTransactions.class);
          if (webTransactions.getRequests_per_minute() < MIN_RPM) {
            metricsWithNoData.add(metric.getName());
          }
        }
      }

      for (NewRelicMetricData.NewRelicMetricSlice metric : errorMetricData.getMetrics()) {
        for (NewRelicMetricData.NewRelicMetricTimeSlice timeSlice : metric.getTimeslices()) {
          final String webTxnJson = JsonUtils.asJson(timeSlice.getValues());
          NewRelicErrors webTransactions = JsonUtils.asObject(webTxnJson, NewRelicErrors.class);
          if (webTransactions.getError_count() > 0 || webTransactions.getErrors_per_minute() > 0) {
            metricsWithNoData.remove(metric.getName());
          }
        }
      }
      return metricsWithNoData;
    } catch (RuntimeException e) {
      if (!failOnException) {
        log.info("for {} marking all metrics to be not with data", apiCallLog.getStateExecutionId(), e);
        return metricNames;
      }
      throw e;
    }
  }

  @Override
  public NewRelicMetricData getMetricDataApplication(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicApplicationId, Collection<String> metricNames,
      long fromTime, long toTime, boolean summarize, ThirdPartyApiCallLog apiCallLog) throws IOException {
    return getMetricData(newRelicConfig, encryptedDataDetails, newRelicApplicationId, -1, metricNames, fromTime, toTime,
        apiCallLog, summarize);
  }

  @Override
  public NewRelicMetricData getMetricDataApplicationInstance(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicApplicationId, long instanceId,
      Collection<String> metricNames, long fromTime, long toTime, ThirdPartyApiCallLog apiCallLog) throws IOException {
    return getMetricData(newRelicConfig, encryptedDataDetails, newRelicApplicationId, instanceId, metricNames, fromTime,
        toTime, apiCallLog, false);
  }

  private NewRelicMetricData getMetricData(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicApplicationId, long instanceId,
      Collection<String> metricNames, long fromTime, long toTime, ThirdPartyApiCallLog apiCallLog, boolean summarize)
      throws IOException {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(newRelicConfig.getAccountId(), null);
    }

    Collection<String> updatedMetrics = new ArrayList<>();
    // TODO: Make a longterm fix for encoding '|'
    for (String name : metricNames) {
      if (name.contains("|")) {
        name = name.replace("|", URLEncoder.encode("|", "UTF-8"));
      }
      updatedMetrics.add(name);
    }

    String urlToCall = instanceId > 0
        ? "/v2/applications/" + newRelicApplicationId + "/instances/" + instanceId + "/metrics/data.json"
        : "/v2/applications/" + newRelicApplicationId + "/metrics/data.json";
    apiCallLog.setTitle("Fetching " + (instanceId > 0 ? "instance" : "application") + " metric data for "
        + updatedMetrics.size() + " transactions from " + newRelicConfig.getNewRelicUrl() + urlToCall);
    apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    final SimpleDateFormat dateFormatter = new SimpleDateFormat(NEW_RELIC_DATE_FORMAT);
    final Call<NewRelicMetricDataResponse> request = instanceId > 0
        ? getNewRelicRestClient(newRelicConfig)
              .getInstanceMetricData(getApiKey(newRelicConfig, encryptedDataDetails), newRelicApplicationId, instanceId,
                  dateFormatter.format(new Date(fromTime)), dateFormatter.format(new Date(toTime)), updatedMetrics)
        : getNewRelicRestClient(newRelicConfig)
              .getApplicationMetricData(getApiKey(newRelicConfig, encryptedDataDetails), newRelicApplicationId,
                  summarize, dateFormatter.format(new Date(fromTime)), dateFormatter.format(new Date(toTime)),
                  updatedMetrics);
    NewRelicMetricDataResponse response = requestExecutor.executeRequest(apiCallLog, request);
    return response.getMetric_data();
  }

  @Override
  public String postDeploymentMarker(NewRelicConfig config, List<EncryptedDataDetail> encryptedDataDetails,
      long newRelicApplicationId, NewRelicDeploymentMarkerPayload body, ThirdPartyApiCallLog apiCallLog) {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(config.getAccountId(), null);
    }
    final String baseUrl =
        config.getNewRelicUrl().endsWith("/") ? config.getNewRelicUrl() : config.getNewRelicUrl() + "/";
    final String url = baseUrl + "v2/applications/" + newRelicApplicationId + "/deployments.json";
    apiCallLog.setTitle("Posting deployment marker to " + config.getNewRelicUrl());
    final Call<Object> request =
        getNewRelicRestClient(config).postDeploymentMarker(getApiKey(config, encryptedDataDetails), url, body);

    requestExecutor.executeRequest(apiCallLog, request);
    return "Successfully posted deployment marker to NewRelic";
  }

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, NewRelicSetupTestNodeData setupTestNodeData, long instanceId,
      boolean checkNotAllowedStrings, ThirdPartyApiCallLog apiCallLog) {
    try {
      List<NewRelicMetric> txnsWithData = getTxnsWithData(newRelicConfig, encryptedDataDetails,
          setupTestNodeData.getNewRelicAppId(), checkNotAllowedStrings, apiCallLog);
      if (isEmpty(txnsWithData)) {
        return VerificationNodeDataSetupResponse.builder()
            .providerReachable(true)
            .loadResponse(VerificationLoadResponse.builder().isLoadPresent(false).build())
            .build();
      }
      if (setupTestNodeData.isServiceLevel()) {
        return VerificationNodeDataSetupResponse.builder()
            .providerReachable(true)
            .dataForNode(null)
            .loadResponse(VerificationLoadResponse.builder().isLoadPresent(true).loadResponse(txnsWithData).build())
            .build();
      }
      Map<String, List<Set<String>>> metricBatches = batchMetricsToCollect(txnsWithData, checkNotAllowedStrings);
      for (Entry<String, List<Set<String>>> metricBatchEntry : metricBatches.entrySet()) {
        for (Set<String> metricNames : metricBatchEntry.getValue()) {
          NewRelicMetricData newRelicMetricData =
              getMetricData(newRelicConfig, encryptedDataDetails, setupTestNodeData.getNewRelicAppId(), instanceId,
                  metricNames, setupTestNodeData.getFromTime(), setupTestNodeData.getToTime(), apiCallLog, true);
          if (isNotEmpty(newRelicMetricData.getMetrics_found())) {
            return VerificationNodeDataSetupResponse.builder()
                .providerReachable(true)
                .dataForNode(newRelicMetricData)
                .loadResponse(VerificationLoadResponse.builder().isLoadPresent(true).loadResponse(txnsWithData).build())
                .build();
          }
        }
      }
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .dataForNode(null)
          .loadResponse(VerificationLoadResponse.builder().isLoadPresent(true).loadResponse(txnsWithData).build())
          .build();
    } catch (Exception e) {
      log.info("Error while getting data for node", e);
      return VerificationNodeDataSetupResponse.builder().providerReachable(false).build();
    }
  }

  private NewRelicRestClient getNewRelicRestClient(final NewRelicConfig newRelicConfig) {
    OkHttpClient.Builder httpClient = getOkHttpClientBuilder();
    httpClient.readTimeout(30, TimeUnit.SECONDS);
    httpClient.addInterceptor(chain -> {
      Request original = chain.request();

      Request request =
          original.newBuilder().url(original.url().toString().replaceAll("\\{", "%7B").replaceAll("}", "%7D")).build();
      return chain.proceed(request);
    });

    final String baseUrl = newRelicConfig.getNewRelicUrl().endsWith("/") ? newRelicConfig.getNewRelicUrl()
                                                                         : newRelicConfig.getNewRelicUrl() + "/";
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(httpClient.build())
                                  .build();
    return retrofit.create(NewRelicRestClient.class);
  }

  private String getApiKey(NewRelicConfig newRelicConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(newRelicConfig, encryptedDataDetails, false);
    return String.valueOf(newRelicConfig.getApiKey());
  }
}
