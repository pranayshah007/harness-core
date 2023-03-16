/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.prometheus;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.YamlUtils;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.apm.Method;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.metrics.MetricType;
import software.wings.service.impl.analysis.APMDelegateService;
import software.wings.service.impl.analysis.TimeSeries;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.prometheus.PrometheusAnalysisService;
import software.wings.service.intfc.security.SecretManager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Pranjal on 09/02/2018
 */
@ValidateOnExecution
@Singleton
@Slf4j
public class PrometheusAnalysisServiceImpl implements PrometheusAnalysisService {
  private static final String START_TIME_PLACE_HOLDER = "${start_time_seconds}";
  private static final String END_TIME_PLACE_HOLDER = "${end_time_seconds}";
  public static final String HOST_NAME_PLACE_HOLDER = "${host}";

  @Inject private SettingsService settingsService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private MLServiceUtils mlServiceUtils;
  @Inject private SecretManager secretManager;

  private static final URL PROMETHEUS_URL_ = PrometheusAnalysisServiceImpl.class.getResource("/apm/prometheus.yml");
  private static final String PROMETHEUS_YAML;
  static {
    try {
      PROMETHEUS_YAML = Resources.toString(PROMETHEUS_URL_, Charsets.UTF_8);
    } catch (IOException ex) {
      throw new DataCollectionException(ex);
    }
  }

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(PrometheusSetupTestNodeData setupTestNodeData) {
    renderFetchQueries(setupTestNodeData.getTimeSeriesToAnalyze());
    final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
    ThirdPartyApiCallLog apiCallLog = createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid());

    // Request is made without host
    String hostName = setupTestNodeData.isServiceLevel() ? null : mlServiceUtils.getHostName(setupTestNodeData);
    Map<TimeSeries, PrometheusMetricDataResponse> metricDataResponseByTimeSeries =
        getMetricDataByTimeSeries(setupTestNodeData, settingAttribute, apiCallLog, hostName);

    PrometheusMetricDataResponse responseDataFromDelegateForServiceLevel =
        metricDataResponseByTimeSeries.values().iterator().next();
    boolean isLoadPresentForServiceLevel = responseDataFromDelegateForServiceLevel != null
        && isNotEmpty(responseDataFromDelegateForServiceLevel.getData().getResult());

    if (setupTestNodeData.isServiceLevel()) {
      VerificationNodeDataSetupResponse responseForServiceLevel =
          VerificationNodeDataSetupResponse.builder().providerReachable(true).build();
      responseForServiceLevel.setLoadResponse(VerificationLoadResponse.builder()
                                                  .isLoadPresent(isLoadPresentForServiceLevel)
                                                  .loadResponse(responseDataFromDelegateForServiceLevel)
                                                  .build());

      return responseForServiceLevel;
    }

    // make a call with hostname
    if (isEmpty(metricDataResponseByTimeSeries)) {
      // provider was reachable based on first find but something happened in the call with host.
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder()
                            .isLoadPresent(isLoadPresentForServiceLevel)
                            .loadResponse(responseDataFromDelegateForServiceLevel)
                            .build())
          .dataForNode(null)
          .build();
    }
    validateMetricDataResponseByTimeSeriesWithHost(metricDataResponseByTimeSeries);
    PrometheusMetricDataResponse responseDataFromDelegateForHost =
        metricDataResponseByTimeSeries.values().iterator().next();
    Object responseDataForHost;
    if (responseDataFromDelegateForHost == null || responseDataFromDelegateForHost.getData() == null) {
      responseDataForHost = null;
    } else {
      responseDataForHost = responseDataFromDelegateForHost.getData().getResult();
    }
    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(true)
        .loadResponse(VerificationLoadResponse.builder()
                          .isLoadPresent(isLoadPresentForServiceLevel)
                          .loadResponse(responseDataFromDelegateForServiceLevel)
                          .build())
        .dataForNode(responseDataForHost)
        .build();
  }

  private void validateMetricDataResponseByTimeSeriesWithHost(
      Map<TimeSeries, PrometheusMetricDataResponse> metricDataResponseByTimeSeriesWithHost) {
    metricDataResponseByTimeSeriesWithHost.forEach((timeSeries, prometheusMetricDataResponse) -> {
      if (prometheusMetricDataResponse != null && prometheusMetricDataResponse.getData() != null
          && prometheusMetricDataResponse.getData().getResult() != null
          && prometheusMetricDataResponse.getData().getResult().size() > 1) {
        throw new VerificationOperationException(ErrorCode.PROMETHEUS_CONFIGURATION_ERROR,
            "Multiple time series values are returned for metric name " + timeSeries.getMetricName()
                + " and group name " + timeSeries.getTxnName()
                + ". Please add more filters to your query to return only one time series.");
      }
    });
  }

  /**
   * Method containing actual implementation for fetch MetricData per timeSeries provided.
   * @param setupTestNodeData
   * @param settingAttribute
   * @param apiCallLog
   * @param hostName
   * @return
   */
  private Map<TimeSeries, PrometheusMetricDataResponse> getMetricDataByTimeSeries(
      PrometheusSetupTestNodeData setupTestNodeData, SettingAttribute settingAttribute, ThirdPartyApiCallLog apiCallLog,
      String hostName) {
    Map<TimeSeries, PrometheusMetricDataResponse> metricDataResponseByTimeSeries = new HashMap<>();
    for (TimeSeries timeSeries : setupTestNodeData.getTimeSeriesToAnalyze()) {
      String url = timeSeries.getUrl();
      Preconditions.checkState(url.contains(START_TIME_PLACE_HOLDER));
      Preconditions.checkState(url.contains(END_TIME_PLACE_HOLDER));
      url = url.replace(START_TIME_PLACE_HOLDER, String.valueOf(setupTestNodeData.getFromTime()));
      url = url.replace(END_TIME_PLACE_HOLDER, String.valueOf(setupTestNodeData.getToTime()));

      if (!setupTestNodeData.isServiceLevel()) {
        Preconditions.checkState(url.contains(HOST_NAME_PLACE_HOLDER));
        url = updateUrlByHostName(url, hostName);
      }
      SyncTaskContext taskContext = SyncTaskContext.builder()
                                        .accountId(settingAttribute.getAccountId())
                                        .appId(GLOBAL_APP_ID)
                                        .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                        .build();
      final PrometheusConfig prometheusConfig = (PrometheusConfig) settingAttribute.getValue();
      APMValidateCollectorConfig apmValidateCollectorConfig = prometheusConfig.createAPMValidateCollectorConfig(url);
      apmValidateCollectorConfig.setBase64EncodingRequired(prometheusConfig.usesBasicAuth());

      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      apmValidateCollectorConfig.setEncryptedDataDetails(encryptionDetails);

      final String validateResponseJson = delegateProxyFactory.getV2(APMDelegateService.class, taskContext)
                                              .fetch(apmValidateCollectorConfig, apiCallLog);
      PrometheusMetricDataResponse response =
          JsonUtils.asObject(validateResponseJson, PrometheusMetricDataResponse.class);
      metricDataResponseByTimeSeries.put(timeSeries, response);
    }
    return metricDataResponseByTimeSeries;
  }

  /**
   * Returns URL with hostname replaced.
   * @param url
   * @param hostName
   * @return
   */
  private String updateUrlByHostName(String url, String hostName) {
    if (isEmpty(hostName)) {
      return url.substring(0, url.lastIndexOf('{'));
    } else {
      return url.replace(HOST_NAME_PLACE_HOLDER, hostName);
    }
  }

  private void renderFetchQueries(List<TimeSeries> timeSeriesToAnalyze) {
    timeSeriesToAnalyze.stream()
        .filter(timeSeries -> !timeSeries.getUrl().contains("api/v1/query_range"))
        .forEach(timeSeries
            -> timeSeries.setUrl(
                "api/v1/query_range?start=${start_time_seconds}&end=${end_time_seconds}&step=60s&query="
                + timeSeries.getUrl()));

    timeSeriesToAnalyze.forEach(timeSeries -> {
      try {
        String url = timeSeries.getUrl();
        url = url.replace(" ", URLEncoder.encode(" ", StandardCharsets.UTF_8.name()));
        url = url.replace("\r", URLEncoder.encode("\r", StandardCharsets.UTF_8.name()));
        url = url.replace("\t", URLEncoder.encode("\t", StandardCharsets.UTF_8.name()));

        timeSeries.setUrl(url.replace("$startTime", "${start_time_seconds}")
                              .replace("$endTime", "${end_time_seconds}")
                              .replace("$hostName", "${host}"));
      } catch (Exception e) {
        throw new DataCollectionException(e);
      }
    });
  }

  public Map<String, List<APMMetricInfo>> apmMetricEndPointsFetchInfo(List<TimeSeries> timeSeriesInfos) {
    Map<String, List<APMMetricInfo>> rv = new HashMap<>();
    if (isEmpty(timeSeriesInfos)) {
      return rv;
    }
    renderFetchQueries(timeSeriesInfos);
    YamlUtils yamlUtils = new YamlUtils();
    APMMetricInfo metricInfos;
    try {
      metricInfos = yamlUtils.read(PROMETHEUS_YAML, new TypeReference<APMMetricInfo>() {});
    } catch (IOException e) {
      throw new DataCollectionException(e);
    }
    timeSeriesInfos.forEach(timeSeries -> {
      final HashMap<String, APMMetricInfo.ResponseMapper> responseMappers =
          new HashMap<>(metricInfos.getResponseMappers());
      responseMappers.put("txnName",
          APMMetricInfo.ResponseMapper.builder().fieldName("txnName").fieldValue(timeSeries.getTxnName()).build());
      rv.put(timeSeries.getUrl(),
          Lists.newArrayList(APMMetricInfo.builder()
                                 .metricName(timeSeries.getMetricName())
                                 .metricType(MetricType.valueOf(timeSeries.getMetricType()))
                                 .method(Method.GET)
                                 .responseMappers(responseMappers)
                                 .build()));
    });

    return rv;
  }
}
