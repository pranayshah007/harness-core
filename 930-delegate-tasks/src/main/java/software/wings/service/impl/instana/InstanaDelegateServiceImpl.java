/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.getUnsafeHttpClient;

import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.time.Timestamp;

import software.wings.beans.InstanaConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.instana.InstanaRestClient;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
public class InstanaDelegateServiceImpl implements InstanaDelegateService {
  @Inject private EncryptionService encryptionService;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public InstanaInfraMetrics getInfraMetrics(InstanaConfig instanaConfig, List<EncryptedDataDetail> encryptionDetails,
      InstanaInfraMetricRequest infraMetricRequest, ThirdPartyApiCallLog apiCallLog) {
    apiCallLog.setTitle("Fetching Infrastructure metrics from " + instanaConfig.getInstanaUrl());
    final Call<InstanaInfraMetrics> request =
        getRestClient(instanaConfig)
            .getInfrastructureMetrics(getAuthorizationHeader(instanaConfig, encryptionDetails), infraMetricRequest);
    return requestExecutor.executeRequest(apiCallLog, request);
  }

  @Override
  public boolean validateConfig(InstanaConfig instanaConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    InstanaTimeFrame instanaTimeFrame =
        InstanaTimeFrame.builder().windowSize(60000).to(Timestamp.currentMinuteBoundary()).build();
    InstanaInfraMetricRequest instanaInfraMetricRequest = InstanaInfraMetricRequest.builder()
                                                              .timeframe(instanaTimeFrame)
                                                              .plugin("docker")
                                                              .metrics(Arrays.asList("cpu.totalusage"))
                                                              .query("dummyQuery")
                                                              .rollup(60)
                                                              .build();

    final Call<InstanaInfraMetrics> request =
        getRestClient(instanaConfig)
            .getInfrastructureMetrics(
                getAuthorizationHeader(instanaConfig, encryptedDataDetails), instanaInfraMetricRequest);
    requestExecutor.executeRequest(request);
    return true;
  }
  public InstanaAnalyzeMetrics getInstanaTraceMetrics(InstanaConfig instanaConfig,
      List<EncryptedDataDetail> encryptedDataDetails, InstanaAnalyzeMetricRequest instanaAnalyzeMetricRequest,
      ThirdPartyApiCallLog apiCallLog) {
    apiCallLog.setTitle("Fetching application call metrics from " + instanaConfig.getInstanaUrl());
    final Call<InstanaAnalyzeMetrics> request =
        getRestClient(instanaConfig)
            .getGroupedTraceMetrics(
                getAuthorizationHeader(instanaConfig, encryptedDataDetails), instanaAnalyzeMetricRequest);
    return requestExecutor.executeRequest(apiCallLog, request);
  }

  InstanaRestClient getRestClient(final InstanaConfig instanaConfig) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(instanaConfig.getInstanaUrl())
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(getUnsafeHttpClient(instanaConfig.getInstanaUrl()))
                                  .build();
    return retrofit.create(InstanaRestClient.class);
  }

  private String getAuthorizationHeader(InstanaConfig instanaConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(instanaConfig, encryptionDetails, false);
    return "apiToken " + new String(instanaConfig.getApiToken());
  }
}
