/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.APDEX_SCORE;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.AVERAGE_RESPONSE_TIME;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.CALL_COUNT;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.ERROR;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.REQUSET_PER_MINUTE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.DataCollectionExecutorService;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.serializer.JsonUtils;

import software.wings.beans.NewRelicConfig;
import software.wings.helpers.ext.newrelic.NewRelicRestClient;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.newrelic.NewRelicApdex;
import software.wings.service.impl.newrelic.NewRelicApplicationInstance;
import software.wings.service.impl.newrelic.NewRelicApplicationInstancesResponse;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfoV2;
import software.wings.service.impl.newrelic.NewRelicErrors;
import software.wings.service.impl.newrelic.NewRelicMetric;
import software.wings.service.impl.newrelic.NewRelicMetricData;
import software.wings.service.impl.newrelic.NewRelicMetricData.NewRelicMetricSlice;
import software.wings.service.impl.newrelic.NewRelicMetricData.NewRelicMetricTimeSlice;
import software.wings.service.impl.newrelic.NewRelicMetricDataResponse;
import software.wings.service.impl.newrelic.NewRelicMetricResponse;
import software.wings.service.impl.newrelic.NewRelicWebTransactions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Table.Cell;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class NewRelicDataCollector implements MetricsDataCollector<NewRelicDataCollectionInfoV2> {
  private static final Set<String> txnsToCollect = Sets.newHashSet("WebTransaction/", "WebTransactionTotalTime/");
  private static final int METRIC_DATA_QUERY_BATCH_SIZE = 15;
  private static final int MIN_RPM = 1;
  private static final String NEW_RELIC_DATE_FORMAT = "YYYY-MM-dd'T'HH:mm:ssZ";
  private static final String ALL_WEB_TXN_NAME = "WebTransaction/all";
  private static final String ALL_ERRORS_TXN_NAME = "Errors/all";
  private static final String OVERALL_APDEX_TXN_NAME = "Apdex";
  private static final Pattern METRIC_NAME_PATTERN_NO_SPECIAL_CHAR = Pattern.compile("[a-zA-Z0-9_\\-\\+\\s/]*");
  @Inject private DataCollectionExecutorService dataCollectionService;

  private NewRelicDataCollectionInfoV2 dataCollectionInfo;
  private DataCollectionExecutionContext dataCollectionExecutionContext;
  @VisibleForTesting List<NewRelicApplicationInstance> instances;
  @VisibleForTesting Set<NewRelicMetric> transactionsToCollect;
  @Override
  public void init(DataCollectionExecutionContext dataCollectionExecutionContext,
      NewRelicDataCollectionInfoV2 dataCollectionInfo) throws DataCollectionException {
    this.dataCollectionExecutionContext = dataCollectionExecutionContext;
    this.dataCollectionInfo = dataCollectionInfo;
    transactionsToCollect = getTransactionsToCollect();
    log.info("Found total new relic metrics " + transactionsToCollect.size());
    if (!dataCollectionInfo.getHosts().isEmpty()) {
      instances = getApplicationInstances();
      log.info("Got {} new relic nodes.", instances.size());
    }
  }

  @Override
  public int getHostBatchSize() {
    return 1;
  }

  @Override
  public List<MetricElement> fetchMetrics(List<String> hostBatch) throws DataCollectionException {
    Preconditions.checkArgument(hostBatch.size() == 1);
    return fetchMetrics(hostBatch.get(0));
  }

  @Override
  public List<MetricElement> fetchMetrics() throws DataCollectionException {
    return fetchMetric(transactionsToCollect);
  }

  private List<MetricElement> fetchMetrics(String host) throws DataCollectionException {
    return instances.stream()
        .filter(node -> node.getHost().equals(host))
        .findFirst()
        .map(node -> fetchMetricsForNode(node, transactionsToCollect))
        .orElse(new ArrayList<>());
  }

  private List<MetricElement> fetchMetricsForNode(
      NewRelicApplicationInstance node, Set<NewRelicMetric> transactionsToCollect) {
    MetricElementTable records = MetricElementTable.create();
    Iterables.partition(getTxnsWithoutSpecialChars(transactionsToCollect), METRIC_DATA_QUERY_BATCH_SIZE)
        .forEach(batch
            -> records.putAll(
                getMetricData(node, batch.stream().map(NewRelicMetric::getName).collect(Collectors.toSet()))));

    try {
      Iterables.partition(getTxnsWithSpecialChars(transactionsToCollect), METRIC_DATA_QUERY_BATCH_SIZE)
          .forEach(batch
              -> records.putAll(
                  getMetricData(node, batch.stream().map(NewRelicMetric::getName).collect(Collectors.toSet()))));
    } catch (Exception e) {
      // ignoring the exception because we don't know what is causing this yet. We want to find out and fix this bug.
      log.error("Data collection failed when special chars are present in metric name {}",
          getTxnsWithSpecialChars(transactionsToCollect), e);
    }
    return getAllMetricRecords(records);
  }
  private Set<NewRelicMetric> getTxnsWithoutSpecialChars(Collection<NewRelicMetric> transactionsToCollect) {
    return transactionsToCollect.stream()
        .filter(metric -> METRIC_NAME_PATTERN_NO_SPECIAL_CHAR.matcher(metric.getName()).matches())
        .collect(Collectors.toSet());
  }

  private Set<NewRelicMetric> getTxnsWithSpecialChars(Collection<NewRelicMetric> transactionsToCollect) {
    return transactionsToCollect.stream()
        .filter(metric -> !METRIC_NAME_PATTERN_NO_SPECIAL_CHAR.matcher(metric.getName()).matches())
        .collect(Collectors.toSet());
  }

  private List<MetricElement> fetchMetric(Set<NewRelicMetric> transactionsToCollect) {
    MetricElementTable records = MetricElementTable.create();
    Iterables.partition(getTxnsWithoutSpecialChars(transactionsToCollect), METRIC_DATA_QUERY_BATCH_SIZE)
        .forEach(batch
            -> records.putAll(getMetricData(batch.stream().map(NewRelicMetric::getName).collect(Collectors.toSet()))));
    try {
      Iterables.partition(getTxnsWithSpecialChars(transactionsToCollect), METRIC_DATA_QUERY_BATCH_SIZE)
          .forEach(batch
              -> records.putAll(
                  getMetricData(batch.stream().map(NewRelicMetric::getName).collect(Collectors.toSet()))));
    } catch (Exception e) {
      // ignoring the exception because we don't know what is causing this yet. We want to find out and fix this bug.
      log.error("Data collection failed when special chars are present in metric name {}",
          getTxnsWithSpecialChars(transactionsToCollect), e);
    }
    return getAllMetricRecords(records);
  }
  private List<MetricElement> getAllMetricRecords(MetricElementTable records) {
    List<MetricElement> rv = new ArrayList<>();
    for (Cell<String, Long, MetricElement> cell : records.cellSet()) {
      MetricElement value = cell.getValue();
      value.setName(
          value.getName().equals("WebTransaction") ? ALL_WEB_TXN_NAME : value.getName().replace("WebTransaction/", ""));
      rv.add(value);
    }

    return rv;
  }
  private MetricElementTable getMetricData(NewRelicApplicationInstance node, Set<String> metricNames) {
    MetricElementTable records = MetricElementTable.create();

    log.info("Fetching for host {} for stateExecutionId {} for metrics {}", node,
        dataCollectionInfo.getStateExecutionId(), metricNames);
    try {
      getWebTransactionMetrics(node, metricNames, records);
      getErrorMetrics(node, metricNames, records);
      getApdexMetrics(node, metricNames, records);
    } catch (IOException e) {
      throw new DataCollectionException(e);
    }

    log.info("Fetching done for host {} for stateExecutionId {} for metrics {}", node,
        dataCollectionInfo.getStateExecutionId(), metricNames);

    log.debug(records.toString());
    return records;
  }

  private MetricElementTable getMetricData(Set<String> metricNames) {
    MetricElementTable records = MetricElementTable.create();

    log.info("Fetching metrics names {}", metricNames);
    try {
      getWebTransactionMetrics(metricNames, records);
      getErrorMetrics(metricNames, records);
      getApdexMetrics(metricNames, records);
    } catch (IOException e) {
      throw new DataCollectionException(e);
    }
    log.info("Finished fetching. Metrics names {}", metricNames);
    log.debug(records.toString());
    return records;
  }

  private void getApdexMetrics(NewRelicApplicationInstance node, Set<String> metricNames, MetricElementTable records)
      throws IOException {
    Set<String> apdexMetricNames = getApdexMetricNames(metricNames);
    if (isEmpty(apdexMetricNames)) {
      return;
    }
    NewRelicMetricData metricData = getMetricData(getApdexMetricNames(metricNames), node);
    populateApdexMetricsRecords(records, metricData);
  }

  private void populateApdexMetricsRecords(MetricElementTable records, NewRelicMetricData metricData) {
    for (NewRelicMetricSlice metric : metricData.getMetrics()) {
      for (NewRelicMetricTimeSlice timeslice : metric.getTimeslices()) {
        long timeStamp = TimeUnit.SECONDS.toMillis(OffsetDateTime.parse(timeslice.getFrom()).toEpochSecond());
        String metricName = metric.getName().equals(OVERALL_APDEX_TXN_NAME)
            ? metric.getName()
            : metric.getName().replace("Apdex", "WebTransaction");

        MetricElement metricElement = records.get(metricName, timeStamp);
        if (metricElement != null) {
          final String apdexJson = JsonUtils.asJson(timeslice.getValues());
          NewRelicApdex apdex = JsonUtils.asObject(apdexJson, NewRelicApdex.class);
          metricElement.getValues().put(APDEX_SCORE, apdex.getScore());
        }
      }
    }
  }

  private void getApdexMetrics(Set<String> metricNames, MetricElementTable records) throws IOException {
    Set<String> apdexMetricNames = getApdexMetricNames(metricNames);
    if (isEmpty(apdexMetricNames)) {
      return;
    }
    NewRelicMetricData metricData = getMetricDataByApplication(getApdexMetricNames(metricNames));
    populateApdexMetricsRecords(records, metricData);
  }

  private void getErrorMetrics(NewRelicApplicationInstance node, Set<String> metricNames, MetricElementTable records)
      throws IOException {
    // get error metrics
    NewRelicMetricData metricData = getMetricData(getErrorMetricNames(metricNames), node);
    populateErrorMetricsRecords(records, metricData);
  }

  private void populateErrorMetricsRecords(MetricElementTable records, NewRelicMetricData metricData) {
    for (NewRelicMetricSlice metric : metricData.getMetrics()) {
      for (NewRelicMetricTimeSlice timeslice : metric.getTimeslices()) {
        long timeStamp = TimeUnit.SECONDS.toMillis(OffsetDateTime.parse(timeslice.getFrom()).toEpochSecond());
        String metricName =
            metric.getName().equals(ALL_ERRORS_TXN_NAME) ? metric.getName() : metric.getName().replace("Errors/", "");

        MetricElement metricElement = records.get(metricName, timeStamp);
        if (metricElement != null) {
          final String errorsJson = JsonUtils.asJson(timeslice.getValues());
          NewRelicErrors errors = JsonUtils.asObject(errorsJson, NewRelicErrors.class);
          metricElement.getValues().put(ERROR, (double) errors.getError_count());
        }
      }
    }
  }

  private void getErrorMetrics(Set<String> metricNames, MetricElementTable records) throws IOException {
    NewRelicMetricData metricData = getMetricDataByApplication(getErrorMetricNames(metricNames));
    populateErrorMetricsRecords(records, metricData);
  }

  private void getWebTransactionMetrics(
      NewRelicApplicationInstance node, Set<String> metricNames, MetricElementTable records) throws IOException {
    NewRelicMetricData metricData = getMetricData(metricNames, node);
    populateWebTransactionRecords(records, metricData, Optional.of(node.getHost()));
  }

  private void populateWebTransactionRecords(
      MetricElementTable records, NewRelicMetricData metricData, Optional<String> hostname) {
    for (NewRelicMetricSlice metric : metricData.getMetrics()) {
      for (NewRelicMetricTimeSlice timeSlice : metric.getTimeslices()) {
        // set from time to the timestamp
        long timeStamp = TimeUnit.SECONDS.toMillis(OffsetDateTime.parse(timeSlice.getFrom()).toEpochSecond());
        String host = hostname.orElseGet(() -> DEFAULT_GROUP_NAME);
        String groupName = isEmpty(dataCollectionInfo.getHostsToGroupNameMap().get(host))
            ? DEFAULT_GROUP_NAME
            : dataCollectionInfo.getHostsToGroupNameMap().get(host);
        MetricElement metricElement = MetricElement.builder()
                                          .host(host)
                                          .values(new HashMap<>())
                                          .name(metric.getName())
                                          .groupName(groupName)
                                          .timestamp(timeStamp)
                                          .build();
        final String webTxnJson = JsonUtils.asJson(timeSlice.getValues());
        NewRelicWebTransactions webTransactions = JsonUtils.asObject(webTxnJson, NewRelicWebTransactions.class);
        if (webTransactions.getCall_count() > 0) {
          metricElement.getValues().put(AVERAGE_RESPONSE_TIME, webTransactions.getAverage_response_time());
          metricElement.getValues().put(CALL_COUNT, (double) webTransactions.getCall_count());
          metricElement.getValues().put(REQUSET_PER_MINUTE, (double) webTransactions.getRequests_per_minute());
          records.put(metric.getName(), timeStamp, metricElement);
        }
      }
    }
  }

  private void getWebTransactionMetrics(Set<String> metricNames, MetricElementTable records) throws IOException {
    NewRelicMetricData metricData = getMetricDataByApplication(metricNames);
    populateWebTransactionRecords(records, metricData, Optional.empty());
  }

  private NewRelicMetricData getMetricData(final Set<String> metricNames, NewRelicApplicationInstance node)
      throws IOException {
    return getMetricDataApplicationInstance(Optional.of(node.getId()), metricNames,
        dataCollectionInfo.getStartTime().toEpochMilli(), dataCollectionInfo.getEndTime().toEpochMilli());
  }

  private NewRelicMetricData getMetricDataByApplication(final Set<String> metricNames) throws IOException {
    return getMetricDataApplicationInstance(Optional.empty(), metricNames,
        dataCollectionInfo.getStartTime().toEpochMilli(), dataCollectionInfo.getEndTime().toEpochMilli());
  }

  private Set<NewRelicMetric> getTransactionsToCollect() {
    log.info("Collecting txn names ");
    Set<NewRelicMetric> transactions = getTxnNameToCollect();
    log.info("new txns {}", transactions.size());
    Set<NewRelicMetric> txnsWithData = getTransactionsWithDataInLastHour(transactions);
    log.info("txns with data {}", txnsWithData.size());
    return txnsWithData;
  }

  private static Set<String> getApdexMetricNames(Collection<String> metricNames) {
    final Set<String> rv = new HashSet<>();
    for (String metricName : metricNames) {
      if (metricName.startsWith("WebTransaction/")) {
        rv.add(metricName.replace("WebTransaction", "Apdex"));
      }
    }

    return rv;
  }

  private static Set<String> getErrorMetricNames(Collection<String> metricNames) {
    final Set<String> rv = new HashSet<>();
    for (String metricName : metricNames) {
      rv.add("Errors/" + metricName);
    }

    return rv;
  }

  private List<NewRelicApplicationInstance> getApplicationInstances() {
    List<NewRelicApplicationInstance> rv = new ArrayList<>();
    int pageCount = 1;
    while (true) {
      final Call<NewRelicApplicationInstancesResponse> request =
          getNewRelicRestClient().listAppInstances(getApiKey(), dataCollectionInfo.getNewRelicAppId(), pageCount);
      List<NewRelicApplicationInstance> applicationInstances =
          dataCollectionExecutionContext
              .executeRequest(
                  "Fetching application instances from " + dataCollectionInfo.getNewRelicConfig().getNewRelicUrl(),
                  request)
              .getApplication_instances();
      if (isEmpty(applicationInstances)) {
        break;
      } else {
        rv.addAll(applicationInstances);
      }
      pageCount++;
    }

    return rv;
  }

  private Set<NewRelicMetric> getTxnNameToCollect() {
    NewRelicConfig newRelicConfig = dataCollectionInfo.getNewRelicConfig();
    Set<NewRelicMetric> newRelicMetrics = new HashSet<>();
    for (String txnName : txnsToCollect) {
      final Call<NewRelicMetricResponse> request =
          getNewRelicRestClient().listMetricNames(getApiKey(), dataCollectionInfo.getNewRelicAppId(), txnName);
      List<NewRelicMetric> metrics =
          dataCollectionExecutionContext
              .executeRequest("Fetching web transactions names from " + newRelicConfig.getNewRelicUrl(), request)
              .getMetrics();
      if (isNotEmpty(metrics)) {
        metrics.forEach(metric -> {
          if (metric.getName().startsWith(txnName)) {
            newRelicMetrics.add(metric);
          }
        });
      }
    }
    return newRelicMetrics;
  }

  private Set<NewRelicMetric> getTransactionsWithDataInLastHour(Collection<NewRelicMetric> metrics) {
    Map<String, NewRelicMetric> webTransactionMetrics = new HashMap<>();
    for (NewRelicMetric metric : metrics) {
      webTransactionMetrics.put(metric.getName(), metric);
    }

    List<Callable<Set<String>>> metricDataCallabels = new ArrayList<>();
    Iterables.partition(getTxnsWithoutSpecialChars(metrics), METRIC_DATA_QUERY_BATCH_SIZE)
        .forEach(metricBatch
            -> metricDataCallabels.add(
                ()
                    -> getMetricsWithNoData(
                        metricBatch.stream().map(NewRelicMetric::getName).collect(Collectors.toSet()))));

    List<Optional<Set<String>>> results = dataCollectionService.executeParrallel(metricDataCallabels);
    metricDataCallabels.clear();
    Iterables.partition(getTxnsWithSpecialChars(metrics), METRIC_DATA_QUERY_BATCH_SIZE)
        .forEach(metricBatch
            -> metricDataCallabels.add(
                ()
                    -> getMetricsWithNoData(
                        metricBatch.stream().map(NewRelicMetric::getName).collect(Collectors.toSet()))));
    try {
      results.addAll(dataCollectionService.executeParrallel(metricDataCallabels));
    } catch (Exception e) {
      log.error("Ignoring exception for special chars in the metric name {}", getTxnsWithSpecialChars(metrics), e);
    }
    results.forEach(result -> {
      if (result.isPresent()) {
        for (String metricName : result.get()) {
          webTransactionMetrics.remove(metricName);
        }
      }
    });
    return new HashSet<>(webTransactionMetrics.values());
  }

  private Set<String> getMetricsWithNoData(Set<String> metricNames) throws IOException {
    final long currentTime = System.currentTimeMillis();
    Set<String> metricsWithNoData = Sets.newHashSet(metricNames);
    NewRelicMetricData metricData =
        getMetricDataApplication(metricNames, currentTime - TimeUnit.HOURS.toMillis(1), currentTime, true);

    if (metricData == null) {
      throw WingsException.builder()
          .code(ErrorCode.NEWRELIC_ERROR)
          .message(
              "Unable to get NewRelic metric data for metric name collection " + dataCollectionInfo.getNewRelicConfig())
          .reportTargets(EnumSet.of(ReportTarget.UNIVERSAL))
          .build();
    }

    metricsWithNoData.removeAll(metricData.getMetrics_found());

    NewRelicMetricData errorMetricData = getMetricDataApplication(
        getErrorMetricNames(metricNames), currentTime - TimeUnit.HOURS.toMillis(1), currentTime, true);

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
  }

  private NewRelicMetricData getMetricDataApplication(
      Collection<String> metricNames, long fromTime, long toTime, boolean summarize) throws IOException {
    return getMetricData(Optional.empty(), metricNames, fromTime, toTime, summarize);
  }

  private NewRelicMetricData getMetricDataApplicationInstance(
      Optional<Long> instanceId, Collection<String> metricNames, long fromTime, long toTime) throws IOException {
    return getMetricData(instanceId, metricNames, fromTime, toTime, false);
  }

  private NewRelicMetricData getMetricData(Optional<Long> instanceId, Collection<String> metricNames, long fromTime,
      long toTime, boolean summarize) throws IOException {
    long newRelicApplicationId = dataCollectionInfo.getNewRelicAppId();
    NewRelicConfig newRelicConfig = dataCollectionInfo.getNewRelicConfig();
    Collection<String> updatedMetrics = new ArrayList<>();
    // TODO: Make a longterm fix for encoding '|'
    for (String name : metricNames) {
      if (name.contains("|")) {
        name = name.replace("|", URLEncoder.encode("|", "UTF-8"));
      }
      updatedMetrics.add(name);
    }

    String urlToCall = instanceId.isPresent()
        ? "/v2/applications/" + newRelicApplicationId + "/instances/" + instanceId.get() + "/metrics/data.json"
        : "/v2/applications/" + newRelicApplicationId + "/metrics/data.json";
    final SimpleDateFormat dateFormatter = new SimpleDateFormat(NEW_RELIC_DATE_FORMAT);
    final Call<NewRelicMetricDataResponse> request = instanceId.isPresent()
        ? getNewRelicRestClient().getInstanceMetricData(getApiKey(), newRelicApplicationId, instanceId.get(),
            dateFormatter.format(new Date(fromTime)), dateFormatter.format(new Date(toTime)), updatedMetrics)
        : getNewRelicRestClient().getApplicationMetricData(getApiKey(), newRelicApplicationId, summarize,
            dateFormatter.format(new Date(fromTime)), dateFormatter.format(new Date(toTime)), updatedMetrics);

    return dataCollectionExecutionContext
        .executeRequest("Fetching " + (instanceId.isPresent() ? "instance" : "application") + " metric data for "
                + updatedMetrics.size() + " transactions from " + newRelicConfig.getNewRelicUrl() + urlToCall,
            request)
        .getMetric_data();
  }
  @VisibleForTesting
  NewRelicRestClient getNewRelicRestClient() {
    NewRelicConfig newRelicConfig = dataCollectionInfo.getNewRelicConfig();
    OkHttpClient.Builder httpClient = getOkHttpClientBuilder();
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

  private String getApiKey() {
    return String.valueOf(dataCollectionInfo.getNewRelicConfig().getApiKey());
  }

  private static class MetricElementTable {
    private TreeBasedTable<String, Long, MetricElement> records;
    private MetricElementTable() {
      records = TreeBasedTable.create();
    }

    public static MetricElementTable create() {
      return new MetricElementTable();
    }

    public void putAll(MetricElementTable records) {
      this.records.putAll(records.records);
    }

    public void put(String name, long timeStamp, MetricElement metricElement) {
      this.records.put(name, timeStamp, metricElement);
    }

    public MetricElement get(String metricName, long timeStamp) {
      return this.records.get(metricName, timeStamp);
    }

    public Set<Cell<String, Long, MetricElement>> cellSet() {
      return records.cellSet();
    }
  }
}
