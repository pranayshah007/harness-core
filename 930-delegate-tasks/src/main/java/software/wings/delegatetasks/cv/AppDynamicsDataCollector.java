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

import static software.wings.service.impl.appdynamics.AppdynamicsDelegateServiceImpl.BT_PERFORMANCE_PATH_PREFIX;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.common.DataCollectionExecutorService;

import software.wings.beans.AppDynamicsConfig;
import software.wings.helpers.ext.appdynamics.AppdynamicsRestClient;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.appdynamics.AppDynamicsDataCollectionInfoV2;
import software.wings.service.impl.appdynamics.AppdynamicsMetric;
import software.wings.service.impl.appdynamics.AppdynamicsMetricData;
import software.wings.service.impl.appdynamics.AppdynamicsTier;
import software.wings.service.impl.appdynamics.AppdynamicsTimeSeries;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.codec.binary.Base64;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AppDynamicsDataCollector implements MetricsDataCollector<AppDynamicsDataCollectionInfoV2> {
  @Inject private DataCollectionExecutorService dataCollectionService;

  private AppDynamicsDataCollectionInfoV2 dataCollectionInfo;
  private DataCollectionExecutionContext dataCollectionExecutionContext;
  private AppdynamicsTier appdynamicsTier;
  private List<AppdynamicsMetric> tierMetrics;

  private static final Set<String> REJECTED_METRICS_24X7 =
      new HashSet<>(Arrays.asList(AppdynamicsTimeSeries.NUMBER_OF_SLOW_CALLS.getMetricName(),
          AppdynamicsTimeSeries.RESPONSE_TIME_95.getMetricName()));
  private static final Set<String> REJECTED_METRICS_WORKFLOW =
      new HashSet<>(Collections.singletonList(AppdynamicsTimeSeries.AVG_RESPONSE_TIME.getMetricName()));

  @Override
  public void init(DataCollectionExecutionContext dataCollectionExecutionContext,
      AppDynamicsDataCollectionInfoV2 dataCollectionInfo) {
    this.dataCollectionExecutionContext = dataCollectionExecutionContext;
    this.dataCollectionInfo = dataCollectionInfo;
    this.appdynamicsTier = getAppDynamicsTier();
    this.tierMetrics = getTierBusinessTransactionMetrics();
  }

  @VisibleForTesting
  AppdynamicsRestClient getAppDynamicsRestClient() {
    AppDynamicsConfig appDynamicsConfig = dataCollectionInfo.getAppDynamicsConfig();
    OkHttpClient.Builder httpClient = getOkHttpClientBuilder();
    httpClient.addInterceptor(chain -> {
      Request original = chain.request();
      Request request =
          original.newBuilder().url(original.url().toString().replaceAll("\\{", "%7B").replaceAll("}", "%7D")).build();
      return chain.proceed(request);
    });

    final String baseUrl = appDynamicsConfig.getControllerUrl().endsWith("/")
        ? appDynamicsConfig.getControllerUrl()
        : appDynamicsConfig.getControllerUrl() + "/";
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(httpClient.build())
                                  .build();
    return retrofit.create(AppdynamicsRestClient.class);
  }

  @VisibleForTesting
  String getHeaderWithCredentials() {
    AppDynamicsConfig appDynamicsConfig = dataCollectionInfo.getAppDynamicsConfig();
    return "Basic "
        + Base64.encodeBase64String(
            String
                .format("%s@%s:%s", appDynamicsConfig.getUsername(), appDynamicsConfig.getAccountname(),
                    new String(appDynamicsConfig.getPassword()))
                .getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public int getHostBatchSize() {
    return 1;
  }

  @Override
  public List<MetricElement> fetchMetrics(List<String> hostBatch) {
    Preconditions.checkArgument(hostBatch.size() == getHostBatchSize());
    return fetchMetricData(hostBatch.get(0));
  }

  @Override
  public List<MetricElement> fetchMetrics() {
    return fetchMetricData(null);
  }

  private List<MetricElement> fetchMetricData(@Nullable String hostName) {
    List<Callable<List<AppdynamicsMetricData>>> callables = new ArrayList<>();
    tierMetrics.forEach(tierMetric -> callables.add(() -> getTierBTMetricData(hostName, tierMetric.getName())));
    List<Optional<List<AppdynamicsMetricData>>> results = dataCollectionService.executeParrallel(callables);
    return parseAppDynamicsResponse(results, hostName);
  }

  private List<AppdynamicsMetricData> getTierBTMetricData(@Nullable String hostName, String btName) {
    String metricPath = BT_PERFORMANCE_PATH_PREFIX + appdynamicsTier.getName() + "|" + btName + "|"
        + (isEmpty(hostName) ? "*" : "Individual Nodes|" + hostName + "|*");
    log.info("fetching metrics for path {} ", metricPath);
    Call<List<AppdynamicsMetricData>> tierBTMetricRequest = getAppDynamicsRestClient().getMetricDataTimeRange(
        getHeaderWithCredentials(), dataCollectionInfo.getAppDynamicsApplicationId(), metricPath,
        dataCollectionInfo.getStartTime().toEpochMilli(), dataCollectionInfo.getEndTime().toEpochMilli(), false);
    return dataCollectionExecutionContext.executeRequest(
        "Fetching data for metric path: " + metricPath, tierBTMetricRequest);
  }

  @VisibleForTesting
  AppdynamicsTier getAppDynamicsTier() {
    final Call<List<AppdynamicsTier>> tierDetail = getAppDynamicsRestClient().getTierDetails(getHeaderWithCredentials(),
        dataCollectionInfo.getAppDynamicsApplicationId(), dataCollectionInfo.getAppDynamicsTierId());
    return dataCollectionExecutionContext
        .executeRequest(
            "Fetching tiers from " + dataCollectionInfo.getAppDynamicsConfig().getControllerUrl(), tierDetail)
        .get(0);
  }

  @VisibleForTesting
  List<AppdynamicsMetric> getTierBusinessTransactionMetrics() {
    final String path = BT_PERFORMANCE_PATH_PREFIX + appdynamicsTier.getName();
    Call<List<AppdynamicsMetric>> tierBTMetricRequest = getAppDynamicsRestClient().listMetrices(
        getHeaderWithCredentials(), dataCollectionInfo.getAppDynamicsApplicationId(), path);

    return dataCollectionExecutionContext.executeRequest(
        "Fetching business transactions for tier from " + dataCollectionInfo.getAppDynamicsConfig().getControllerUrl(),
        tierBTMetricRequest);
  }

  @VisibleForTesting
  List<MetricElement> parseAppDynamicsResponse(
      List<Optional<List<AppdynamicsMetricData>>> appDynamicsMetricDataOptionalList, String host) {
    TreeBasedTable<String, Long, MetricElement> metricElementTreeBasedTable = TreeBasedTable.create();
    for (Optional<List<AppdynamicsMetricData>> appDynamicsMetricDataList : appDynamicsMetricDataOptionalList) {
      if (!appDynamicsMetricDataList.isPresent()) {
        continue;
      }
      for (AppdynamicsMetricData appdynamicsMetricData : appDynamicsMetricDataList.get()) {
        String[] appdynamicsPathPieces = appdynamicsMetricData.getMetricPath().split(Pattern.quote("|"));
        String tierName = parseAppdynamicsInternalName(appdynamicsPathPieces, 2);
        String nodeName = isEmpty(host) ? tierName : appdynamicsPathPieces[5];
        if (isNotEmpty(host) && !dataCollectionInfo.getHosts().contains(nodeName)) {
          log.info("skipping: {}", nodeName);
          continue;
        }
        String txnName = parseAppdynamicsInternalName(appdynamicsPathPieces, 3);
        String metricName = isNotEmpty(host) ? parseAppdynamicsInternalName(appdynamicsPathPieces, 6)
                                             : parseAppdynamicsInternalName(appdynamicsPathPieces, 4);

        if ((isEmpty(host) && !REJECTED_METRICS_24X7.contains(metricName))
            || (isNotEmpty(host) && !REJECTED_METRICS_WORKFLOW.contains(metricName))) {
          appdynamicsMetricData.getMetricValues().forEach(metricValue -> {
            MetricElement metricElement;
            long timestamp = metricValue.getStartTimeInMillis();
            if (metricElementTreeBasedTable.get(txnName, timestamp) != null) {
              metricElement = metricElementTreeBasedTable.get(txnName, timestamp);
            } else {
              metricElement = MetricElement.builder()
                                  .name(txnName)
                                  .timestamp(timestamp)
                                  .groupName(tierName)
                                  .values(new HashMap<>())
                                  .build();
              if (isNotEmpty(host)) {
                metricElement.setHost(host);
              }
            }
            if (AppdynamicsTimeSeries.getMetricsToTrack().contains(metricName)) {
              metricElement.getValues().putIfAbsent(metricName, metricValue.getValue());
              metricElementTreeBasedTable.put(txnName, timestamp, metricElement);
            }
          });
        }
      }
    }
    return new ArrayList<>(metricElementTreeBasedTable.values());
  }

  private static String parseAppdynamicsInternalName(String[] appdynamicsPathPieces, int index) {
    String name = appdynamicsPathPieces[index];
    if (name == null) {
      return name;
    }
    // mongo doesn't like dots in the names
    return name.replaceAll("\\.", "-");
  }
}
