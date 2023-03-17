/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.elk;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.network.Http.getOkHttpClientBuilder;
import static io.harness.network.Http.getOkHttpClientBuilderWithReadtimeOut;

import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.ElkConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.elk.ElkRestClient;
import software.wings.helpers.ext.elk.KibanaRestClient;
import software.wings.service.impl.analysis.ElkConnector;
import software.wings.service.impl.analysis.ElkValidationType;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@BreakDependencyOn("software.wings.service.impl.ThirdPartyApiCallLog")
public class ElkDelegateServiceImpl implements ElkDelegateService {
  public static final int MAX_RECORDS = 10000;

  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public boolean validateConfig(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isNotBlank(elkConfig.getUsername()) && isEmpty(elkConfig.getPassword())) {
      throw new IllegalArgumentException("User name is given but password is empty");
    }

    if (isBlank(elkConfig.getUsername()) && isNotEmpty(elkConfig.getPassword())) {
      throw new IllegalArgumentException("User name is empty but password is given");
    }
    getLogSample(elkConfig, "*", false, encryptedDataDetails);
    return true;
  }

  @Override
  public Object search(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails,
      ElkLogFetchRequest logFetchRequest, ThirdPartyApiCallLog apiCallLog, int maxRecords) throws IOException {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(elkConfig.getAccountId(), null);
    }

    apiCallLog.setTitle("Fetching logs from " + elkConfig.getElkUrl());
    apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name("connector")
                                     .value(elkConfig.getElkConnector().getName())
                                     .type(FieldType.TEXT)
                                     .build());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name("indices")
                                     .value(logFetchRequest.getIndices())
                                     .type(FieldType.TEXT)
                                     .build());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name("body")
                                     .value(JsonUtils.asJson(logFetchRequest.toElasticSearchJsonObject()))
                                     .type(FieldType.JSON)
                                     .build());
    apiCallLog.addFieldToRequest(
        ThirdPartyApiCallField.builder().name("url").value(elkConfig.getElkUrl()).type(FieldType.URL).build());
    final Call<Object> request = elkConfig.getElkConnector() == ElkConnector.KIBANA_SERVER
        ? getKibanaRestClient(elkConfig, encryptedDataDetails)
              .getLogSample(format(KibanaRestClient.searchPathPattern, logFetchRequest.getIndices(), 10000),
                  KibanaRestClient.searchMethod, logFetchRequest.toElasticSearchJsonObject())
        : getElkRestClient(elkConfig, encryptedDataDetails)
              .search(logFetchRequest.getIndices(), logFetchRequest.toElasticSearchJsonObject(), maxRecords);
    final Response<Object> response;
    try {
      response = request.execute();
    } catch (Exception e) {
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), FieldType.TEXT);
      delegateLogService.save(elkConfig.getAccountId(), apiCallLog);
      throw new WingsException(e.getMessage());
    }
    apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    if (response.isSuccessful()) {
      apiCallLog.addFieldToResponse(response.code(), response.body(), FieldType.JSON);
      delegateLogService.save(elkConfig.getAccountId(), apiCallLog);
      return response.body();
    }
    String errorMessage = response.errorBody().string();

    apiCallLog.addFieldToResponse(response.code(), errorMessage, FieldType.TEXT);
    delegateLogService.save(elkConfig.getAccountId(), apiCallLog);
    throw new WingsException("Unable to get search response - " + errorMessage);
  }

  @Override
  public Map<String, ElkIndexTemplate> getIndices(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    ThirdPartyApiCallLog apiCallLog = createApiCallLog(elkConfig.getAccountId(), null);
    apiCallLog.setTitle("Fetching indices from " + elkConfig.getElkUrl());

    final Call<Map<String, Map<String, Object>>> request = elkConfig.getElkConnector() == ElkConnector.KIBANA_SERVER
        ? getKibanaRestClient(elkConfig, encryptedDataDetails).template()
        : getElkRestClient(elkConfig, encryptedDataDetails).template();

    final Map<String, Map<String, Object>> response = requestExecutor.executeRequest(apiCallLog, request);
    final Map<String, ElkIndexTemplate> rv = new HashMap<>();
    for (Entry<String, Map<String, Object>> indexEntry : response.entrySet()) {
      if (indexEntry.getKey().charAt(0) != '.') {
        if (indexEntry.getValue().containsKey("index_patterns")) {
          List<String> indexPatterns = (List<String>) indexEntry.getValue().get("index_patterns");
          indexPatterns.forEach(indexPattern -> {
            ElkIndexTemplate indexTemplate = new ElkIndexTemplate();
            indexTemplate.setName(indexPattern);
            indexTemplate.setProperties(
                Collections
                    .emptyMap()); // Just putting empty map to avoid changing the api. Only Keys are used in the UI.
            rv.put(indexTemplate.getName(), indexTemplate);
          });
        } else {
          ElkIndexTemplate indexTemplate = new ElkIndexTemplate();
          indexTemplate.setName((String) indexEntry.getValue().get("template"));
          indexTemplate.setProperties(
              Collections
                  .emptyMap()); // Just putting empty map to avoid changing the api. Only Keys are used in the UI.
          rv.put(indexTemplate.getName(), indexTemplate);
        }
      }
    }
    return rv;
  }

  @Override
  public Object getLogSample(
      ElkConfig elkConfig, String index, boolean shouldSort, List<EncryptedDataDetail> encryptedDataDetails) {
    final Call<Object> request = elkConfig.getElkConnector() == ElkConnector.KIBANA_SERVER
        ? getKibanaRestClient(elkConfig, encryptedDataDetails)
              .getLogSample(format(KibanaRestClient.searchPathPattern, index, 1), KibanaRestClient.searchMethod,
                  ElkLogFetchRequest.lastInsertedRecordObject(shouldSort))
        : getElkRestClient(elkConfig, encryptedDataDetails)
              .getLogSample(index, ElkLogFetchRequest.lastInsertedRecordObject(shouldSort));
    return requestExecutor.executeRequest(request);
  }

  @Override
  public String getVersion(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) throws IOException {
    if (elkConfig.getElkConnector() == ElkConnector.KIBANA_SERVER) {
      try {
        final Call<Object> request = getKibanaRestClient(elkConfig, encryptedDataDetails).version();
        final Response<Object> response = request.execute();
        return response.headers().get("kbn-version");
      } catch (Exception ex) {
        throw new WingsException("Unable to get version. Check url : " + ExceptionUtils.getMessage(ex), ex);
      }
    } else {
      throw new WingsException("Get version is supported only for the Kibana connector");
    }
  }

  private ElkRestClient getElkRestClient(final ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    return createRetrofit(elkConfig, encryptedDataDetails).create(ElkRestClient.class);
  }

  private KibanaRestClient getKibanaRestClient(
      final ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    return createRetrofit(elkConfig, encryptedDataDetails).create(KibanaRestClient.class);
  }

  private Retrofit createRetrofit(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(elkConfig, encryptedDataDetails, false);
    OkHttpClient.Builder httpClient = elkConfig.getElkUrl().startsWith("https")
        ? getUnsafeOkHttpClient().readTimeout(60, TimeUnit.SECONDS)
        : getOkHttpClientBuilderWithReadtimeOut(60, TimeUnit.SECONDS);
    httpClient
        .addInterceptor(chain -> {
          Request original = chain.request();

          boolean shouldAuthenticate = isNotBlank(elkConfig.getUsername()) && elkConfig.getPassword() != null;
          boolean usePassword = elkConfig.getValidationType() == ElkValidationType.PASSWORD;
          boolean isKibana = elkConfig.getElkConnector() == ElkConnector.KIBANA_SERVER;
          Request.Builder builder = shouldAuthenticate
              ? usePassword ? original.newBuilder()
                                  .header("Accept", "application/json")
                                  .header("Content-Type", "application/json")
                                  .header("Authorization", getHeaderWithCredentials(elkConfig, encryptedDataDetails))
                            : original.newBuilder()
                                  .header("Accept", "application/json")
                                  .header("Content-Type", "application/json")
                                  .header(elkConfig.getUsername(), getAPIToken(elkConfig, encryptedDataDetails))
              : original.newBuilder().header("Accept", "application/json").header("Content-Type", "application/json");

          if (isKibana) {
            builder.addHeader("kbn-version", elkConfig.getKibanaVersion());
          }

          Request request = builder.method(original.method(), original.body()).build();

          return chain.proceed(request);
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(elkConfig.getElkUrl()));

    String baseUrl = elkConfig.getElkUrl();
    if (baseUrl.charAt(baseUrl.length() - 1) != '/') {
      baseUrl = baseUrl + "/";
    }

    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JacksonConverterFactory.create())
        .client(httpClient.build())
        .build();
  }

  private String getHeaderWithCredentials(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(elkConfig, encryptedDataDetails, false);
    return "Basic "
        + Base64.encodeBase64String(format("%s:%s", elkConfig.getUsername(), new String(elkConfig.getPassword()))
                                        .getBytes(StandardCharsets.UTF_8));
  }

  private String getAPIToken(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(elkConfig, encryptedDataDetails, false);
    return String.valueOf(elkConfig.getPassword());
  }

  private static OkHttpClient.Builder getUnsafeOkHttpClient() {
    try {
      // Create a trust manager that does not validate certificate chains
      final TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager(){
          @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
              throws CertificateException{}

          @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
              throws CertificateException{}

                     @Override public java.security.cert.X509Certificate[] getAcceptedIssuers(){
                         return new X509Certificate[] {};
    }
  }
};

// Install the all-trusting trust manager
final SSLContext sslContext = SSLContext.getInstance("SSL");
sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
// Create an ssl socket factory with our all-trusting manager
final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

OkHttpClient.Builder builder = getOkHttpClientBuilder();
builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
builder.hostnameVerifier(new NoopHostnameVerifier());

return builder;
}
catch (Exception e) {
  throw new WingsException("Unexpected error : " + ExceptionUtils.getMessage(e), e);
}
}
}
