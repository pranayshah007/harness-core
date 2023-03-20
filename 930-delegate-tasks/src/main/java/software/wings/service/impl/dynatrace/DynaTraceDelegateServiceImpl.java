/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.dynatrace;

import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.getUnsafeHttpClient;

import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.DynaTraceConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.CVConstants;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.dynatrace.DynaTraceRestClient;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Created by rsingh on 1/29/18.
 */
@Singleton
@Slf4j
public class DynaTraceDelegateServiceImpl implements DynaTraceDelegateService {
  private static final int PAGE_SIZE = 500;
  private static final int MAX_SERVICES_SIZE = 500;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public boolean validateConfig(DynaTraceConfig dynaTraceConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    final Call<Object> request = getDynaTraceRestClient(dynaTraceConfig)
                                     .listTimeSeries(getHeaderWithCredentials(dynaTraceConfig, encryptedDataDetails));
    requestExecutor.executeRequest(request);

    return true;
  }

  @Override
  public DynaTraceMetricDataResponse fetchMetricData(DynaTraceConfig dynaTraceConfig,
      DynaTraceMetricDataRequest dataRequest, List<EncryptedDataDetail> encryptedDataDetails,
      ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    apiCallLog.setTitle(
        "Fetching metric data for " + dataRequest.getTimeseriesId() + " from " + dynaTraceConfig.getDynaTraceUrl());
    final Call<DynaTraceMetricDataResponse> request =
        getDynaTraceRestClient(dynaTraceConfig)
            .fetchMetricData(getHeaderWithCredentials(dynaTraceConfig, encryptedDataDetails), dataRequest);
    return requestExecutor.executeRequest(apiCallLog, request);
  }

  @Override
  public List<DynaTraceMetricDataResponse> getMetricsWithDataForNode(DynaTraceConfig config,
      List<EncryptedDataDetail> encryptionDetails, DynaTraceSetupTestNodeData setupTestNodeData,
      ThirdPartyApiCallLog thirdPartyApiCallLog) {
    final List<DynaTraceMetricDataResponse> metricDataResponses = new ArrayList<>();

    List<Callable<DynaTraceMetricDataResponse>> callables = new ArrayList<>();
    if (setupTestNodeData.getServiceEntityId() == null) {
      callables.addAll(
          getCallablesForTestButton(null, config, encryptionDetails, setupTestNodeData, thirdPartyApiCallLog));
    } else {
      List<String> entityIds = Arrays.asList(setupTestNodeData.getServiceEntityId().split(","));
      entityIds.replaceAll(String::trim);
      entityIds.forEach(entity
          -> callables.addAll(
              getCallablesForTestButton(entity, config, encryptionDetails, setupTestNodeData, thirdPartyApiCallLog)));
    }
    List<Optional<DynaTraceMetricDataResponse>> results = dataCollectionService.executeParrallel(callables);
    results.forEach(result -> {
      if (result.isPresent()) {
        metricDataResponses.add(result.get());
      }
    });

    return metricDataResponses;
  }

  @Override
  public List<DynaTraceApplication> getServices(DynaTraceConfig config, List<EncryptedDataDetail> encryptionDetails,
      ThirdPartyApiCallLog thirdPartyApiCallLog, Boolean shouldResolveAllServices) {
    boolean isLastPage = false;
    List<DynaTraceApplication> serviceList = new ArrayList<>();
    String nextPageKey = null;
    int numPages = 0;
    while (!isLastPage) {
      Call<List<DynaTraceApplication>> servicesRequest = getDynaTraceRestClient(config).getServices(
          getHeaderWithCredentials(config, encryptionDetails), PAGE_SIZE, nextPageKey);
      Response<List<DynaTraceApplication>> response =
          requestExecutor.executeRequestGetResponse(servicesRequest.clone());
      Headers headers = response.headers();
      int totalCount = Integer.parseInt(headers.get("total-Count"));
      if (!shouldResolveAllServices && totalCount > MAX_SERVICES_SIZE) {
        return prepareAndReturnTooManyServicesResponse();
      }
      nextPageKey = headers.get("next-page-key");
      serviceList.addAll(response.body());
      if (nextPageKey == null) {
        isLastPage = true;
      }
    }

    return serviceList;
  }

  private List<DynaTraceApplication> prepareAndReturnTooManyServicesResponse() {
    DynaTraceApplication application =
        DynaTraceApplication.builder()
            .displayName("Too many services to list. Please type in your Service Entity ID")
            .entityId("-1")
            .build();
    return Arrays.asList(application);
  }

  private List<Callable<DynaTraceMetricDataResponse>> getCallablesForTestButton(String entityId, DynaTraceConfig config,
      List<EncryptedDataDetail> encryptionDetails, DynaTraceSetupTestNodeData setupTestNodeData,
      ThirdPartyApiCallLog thirdPartyApiCallLog) {
    List<Callable<DynaTraceMetricDataResponse>> callables = new ArrayList<>();
    for (DynaTraceTimeSeries timeSeries : Lists.newArrayList(DynaTraceTimeSeries.REQUEST_PER_MINUTE)) {
      callables.add(() -> {
        DynaTraceMetricDataRequest dataRequest = DynaTraceMetricDataRequest.builder()
                                                     .timeseriesId(timeSeries.getTimeseriesId())
                                                     .aggregationType(timeSeries.getAggregationType())
                                                     .percentile(timeSeries.getPercentile())
                                                     .startTimestamp(setupTestNodeData.getFromTime())
                                                     .endTimestamp(setupTestNodeData.getToTime())
                                                     .entities(Collections.singleton(entityId))
                                                     .build();

        DynaTraceMetricDataResponse metricDataResponse =
            fetchMetricData(config, dataRequest, encryptionDetails, thirdPartyApiCallLog);
        metricDataResponse.getResult().setHost(CVConstants.TEST_HOST_NAME);
        return metricDataResponse;
      });
    }
    return callables;
  }

  private DynaTraceRestClient getDynaTraceRestClient(final DynaTraceConfig dynaTraceConfig) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(dynaTraceConfig.getDynaTraceUrl())
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(getUnsafeHttpClient(dynaTraceConfig.getDynaTraceUrl()))
                                  .build();
    return retrofit.create(DynaTraceRestClient.class);
  }

  private String getHeaderWithCredentials(
      DynaTraceConfig dynaTraceConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(dynaTraceConfig, encryptionDetails, false);
    return "Api-Token " + new String(dynaTraceConfig.getApiToken());
  }
}
