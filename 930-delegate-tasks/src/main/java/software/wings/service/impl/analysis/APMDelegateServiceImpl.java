/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.delegatetasks.cv.CVConstants.AZURE_BASE_URL;
import static software.wings.delegatetasks.cv.CVConstants.AZURE_TOKEN_URL;

import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.apm.Method;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.apm.APMRestClient;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.xerces.impl.dv.util.Base64;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class APMDelegateServiceImpl implements APMDelegateService {
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private RequestExecutor requestExecutor;

  private Map<String, String> decryptedFields = new HashMap<>();

  @Override
  public boolean validateCollector(APMValidateCollectorConfig config) {
    if (!config.getHeaders()
             .keySet()
             .stream()
             .map(String::toLowerCase)
             .collect(Collectors.toList())
             .contains("accept")) {
      config.getHeaders().put("Accept", "application/json");
    }
    decryptFields(config.getEncryptedDataDetails());

    Call<Object> request = getCall(config);

    requestExecutor.executeRequest(request);
    return true;
  }

  private String resolveDollarReferences(String input) {
    if (input == null) {
      return null;
    }
    while (input.contains("${")) {
      int startIndex = input.indexOf("${");
      int endIndex = input.indexOf('}', startIndex);
      String fieldName = input.substring(startIndex + 2, endIndex);
      String headerBeforeIndex = input.substring(0, startIndex);
      if (!decryptedFields.containsKey(fieldName)) {
        throw new DataCollectionException("Could not resolve \"${" + fieldName + "}\" provided in input");
      }
      input = headerBeforeIndex + decryptedFields.get(fieldName) + input.substring(endIndex + 1);
    }
    return input;
  }

  private Map<String, Object> resolveDollarReferences(Map<String, String> input) {
    Map<String, Object> output = new HashMap<>();
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

  private void decryptFields(List<EncryptedDataDetail> encryptedDataDetails) {
    if (isNotEmpty(encryptedDataDetails)) {
      char[] decryptedValue;
      for (EncryptedDataDetail encryptedDataDetail : encryptedDataDetails) {
        decryptedValue = encryptionService.getDecryptedValue(encryptedDataDetail, false);
        if (decryptedValue != null) {
          decryptedFields.put(encryptedDataDetail.getFieldName(), new String(decryptedValue));
        }
      }
    }
  }

  @Override
  public String fetch(APMValidateCollectorConfig config, ThirdPartyApiCallLog apiCallLog) {
    if (!config.getHeaders()
             .keySet()
             .stream()
             .map(String::toLowerCase)
             .collect(Collectors.toList())
             .contains("accept")) {
      config.getHeaders().put("Accept", "application/json");
    }
    if (config.getEncryptedDataDetails() != null) {
      char[] decryptedValue;
      for (EncryptedDataDetail encryptedDataDetail : config.getEncryptedDataDetails()) {
        decryptedValue = encryptionService.getDecryptedValue(encryptedDataDetail, false);
        if (decryptedValue != null) {
          decryptedFields.put(encryptedDataDetail.getFieldName(), new String(decryptedValue));
        }
      }
    }
    apiCallLog.setTitle("Fetching data from " + config.getUrl());

    Call<Object> request = getCall(config);

    return JsonUtils.asJson(requestExecutor.executeRequest(apiCallLog, request));
  }

  private Call<Object> getCall(APMValidateCollectorConfig config) {
    Call<Object> request;
    // Special case for getting the bearer token for azure log analytics token
    if (config.getBaseUrl().contains(AZURE_BASE_URL)) {
      try {
        config.setHeaders(getAzureAuthHeader(config));
        config.getOptions().remove("client_id");
        config.getOptions().remove("tenant_id");
        config.getOptions().remove("client_secret");
      } catch (Exception e) {
        throw new DataCollectionException("Unable to fetch the bearer token for Azure Log Analytics: ", e);
      }
    }
    if (config.getCollectionMethod() != null && config.getCollectionMethod() == Method.POST) {
      Map<String, Object> body = new HashMap<>();
      if (isNotEmpty(config.getBody())) {
        config.setBody(resolveDollarReferences(config.getBody()));
        body = new JSONObject(config.getBody()).toMap();
      }
      request = getAPMRestClient(config.getBaseUrl())
                    .postCollect(resolveDollarReferences(config.getUrl()), resolveDollarReferences(config.getHeaders()),
                        resolveDollarReferences(config.getOptions()), body);
    } else {
      Map<String, Object> resolvedHeaders = resolveDollarReferences(config.getHeaders());
      if (config.isBase64EncodingRequired()) {
        resolveBase64Reference(resolvedHeaders);
      }
      request = getAPMRestClient(config.getBaseUrl())
                    .collect(resolveDollarReferences(config.getUrl()), resolvedHeaders,
                        resolveDollarReferences(config.getOptions()));
    }
    return request;
  }

  private APMRestClient getAPMRestClient(String baseUrl) {
    final Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(JacksonConverterFactory.create())
            .client(Http.getOkHttpClientWithNoProxyValueSet(baseUrl).connectTimeout(30, TimeUnit.SECONDS).build())
            .build();
    return retrofit.create(APMRestClient.class);
  }

  private Map<String, String> getAzureAuthHeader(APMValidateCollectorConfig config) {
    Map<String, Object> resolvedOptions = resolveDollarReferences(config.getOptions());
    String clientId = (String) resolvedOptions.get("client_id");
    String clientSecret = (String) resolvedOptions.get("client_secret");
    String tenantId = (String) resolvedOptions.get("tenant_id");
    Preconditions.checkNotNull(
        clientId, "client_id parameter cannot be null when collecting data from azure log analytics");
    Preconditions.checkNotNull(
        tenantId, "tenant_id parameter cannot be null when collecting data from azure log analytics");
    Preconditions.checkNotNull(
        clientSecret, "client_secret parameter cannot be null when collecting data from azure log analytics");

    String urlForToken = tenantId + "/oauth2/token";

    Map<String, String> bearerTokenHeader = new HashMap<>();
    bearerTokenHeader.put("Content-Type", "application/x-www-form-urlencoded");
    Call<Object> bearerTokenCall = getAPMRestClient(AZURE_TOKEN_URL)
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

  private void resolveBase64Reference(Map<String, Object> headers) {
    if (isEmpty(headers)) {
      return;
    }

    for (Map.Entry<String, Object> entry : headers.entrySet()) {
      if (entry == null) {
        continue;
      }
      String headerValue = (String) entry.getValue();
      int placeholderIndex = headerValue.indexOf("encodeWithBase64(");
      if (placeholderIndex != -1) {
        String stringToEncode = headerValue.substring(
            placeholderIndex + "encodeWithBase64(".length(), headerValue.indexOf(')', placeholderIndex));
        String encodedString = headerValue.replace(
            String.format("encodeWithBase64(%s)", stringToEncode), Base64.encode(stringToEncode.getBytes()));
        headers.put(entry.getKey(), encodedString);
      }
    }
  }
}
