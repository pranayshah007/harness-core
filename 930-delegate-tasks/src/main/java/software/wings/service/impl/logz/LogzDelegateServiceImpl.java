/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.logz;

import static io.harness.network.Http.getOkHttpClientBuilder;

import static software.wings.beans.dto.ThirdPartyApiCallLog.PAYLOAD;
import static software.wings.delegatetasks.cv.CVConstants.URL_STRING;

import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.config.LogzConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.logz.LogzRestClient;
import software.wings.service.impl.elk.ElkLogFetchRequest;
import software.wings.service.intfc.logz.LogzDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Created by rsingh on 8/21/17.
 */
@Singleton
public class LogzDelegateServiceImpl implements LogzDelegateService {
  private static final Object logzQuery = JsonUtils.asObject(
      "{ \"size\": 0, \"query\": { \"bool\": { \"must\": [{ \"range\": { \"@timestamp\": { \"gte\": \"now-5m\", \"lte\": \"now\" } } }] } }, \"aggs\": { \"byType\": { \"terms\": { \"field\": \"type\", \"size\": 5 } } } }",
      Object.class);
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public boolean validateConfig(LogzConfig logzConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    final Call<Object> request = getLogzRestClient(logzConfig, encryptedDataDetails).search(logzQuery);
    requestExecutor.executeRequest(request);
    return true;
  }

  @Override
  public Object search(LogzConfig logzConfig, List<EncryptedDataDetail> encryptedDataDetails,
      ElkLogFetchRequest logFetchRequest, ThirdPartyApiCallLog apiCallLog) throws IOException {
    Preconditions.checkNotNull(apiCallLog);

    apiCallLog.setTitle("Fetching logs from " + logzConfig.getLogzUrl());
    apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name(URL_STRING)
                                     .value(logzConfig.getLogzUrl() + "/v1/search?size=10000")
                                     .type(FieldType.URL)
                                     .build());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name(PAYLOAD)
                                     .value(JsonUtils.asJson(logFetchRequest))
                                     .type(FieldType.JSON)
                                     .build());
    final Call<Object> request =
        getLogzRestClient(logzConfig, encryptedDataDetails).search(logFetchRequest.toElasticSearchJsonObject());
    final Response<Object> response;
    try {
      response = request.execute();
    } catch (Exception e) {
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), FieldType.TEXT);
      delegateLogService.save(logzConfig.getAccountId(), apiCallLog);
      throw new WingsException(e.getMessage());
    }
    apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    if (response.isSuccessful()) {
      apiCallLog.addFieldToResponse(response.code(), response.body(), FieldType.JSON);
      delegateLogService.save(logzConfig.getAccountId(), apiCallLog);
      return response.body();
    }

    apiCallLog.addFieldToResponse(response.code(), response.errorBody().string(), FieldType.TEXT);
    delegateLogService.save(logzConfig.getAccountId(), apiCallLog);
    throw new WingsException(response.errorBody().string());
  }

  @Override
  public Object getLogSample(LogzConfig logzConfig, List<EncryptedDataDetail> encryptedDataDetails) throws IOException {
    final Call<Object> request = getLogzRestClient(logzConfig, encryptedDataDetails)
                                     .getLogSample(ElkLogFetchRequest.lastInsertedRecordObject(true));
    final Response<Object> response = request.execute();
    if (response.isSuccessful()) {
      return response.body();
    }
    throw new WingsException(response.errorBody().string());
  }

  private LogzRestClient getLogzRestClient(
      final LogzConfig logzConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(logzConfig, encryptedDataDetails, false);
    OkHttpClient.Builder httpClient = getOkHttpClientBuilder();
    httpClient.addInterceptor(chain -> {
      Request original = chain.request();

      Request request = original.newBuilder()
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("X-USER-TOKEN", new String(logzConfig.getToken()))
                            .method(original.method(), original.body())
                            .build();

      return chain.proceed(request);
    });

    final String baseUrl =
        logzConfig.getLogzUrl().endsWith("/") ? logzConfig.getLogzUrl() : logzConfig.getLogzUrl() + "/";
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(httpClient.build())
                                  .build();
    return retrofit.create(LogzRestClient.class);
  }
}
