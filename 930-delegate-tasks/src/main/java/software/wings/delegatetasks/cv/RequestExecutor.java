/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.delegatetasks.cv.CVConstants.MAX_RETRIES;
import static software.wings.delegatetasks.cv.CVConstants.RATE_LIMIT_STATUS;
import static software.wings.delegatetasks.cv.CVConstants.RETRY_SLEEP_DURATION;
import static software.wings.delegatetasks.cv.CVConstants.URL_STRING;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okio.Buffer;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class RequestExecutor {
  private static final String DATADOG_API_MASK = "api_key=([^&]*)";
  private static final String DATADOG_APP_MASK = "application_key=([^&]*)";

  @Inject private DelegateLogService delegateLogService;

  public <U> U executeRequest(Call<U> request) {
    return executeRequestGetResponse(request).body();
  }

  public <U> Response<U> executeRequestGetResponse(Call<U> request) {
    try {
      Response<U> response = request.clone().execute();
      if (response.isSuccessful()) {
        return response;
      } else {
        throw new DataCollectionException("Response code: " + response.code() + ", Message: " + response.message()
            + ", Error: " + response.errorBody().string());
      }
    } catch (IOException e) {
      throw new DataCollectionException(e);
    }
  }

  public <U> U executeRequest(
      ThirdPartyApiCallLog thirdPartyApiCallLog, Call<U> request, Map<String, String> patternsToMask) {
    int retryCount = 0;
    while (true) {
      try {
        return executeRequest(thirdPartyApiCallLog, retryCount, request, patternsToMask);
      } catch (RateLimitExceededException e) {
        int randomNum = ThreadLocalRandom.current().nextInt(1, 5);
        sleep(RETRY_SLEEP_DURATION.plus(Duration.ofSeconds(randomNum)));
        if (retryCount == MAX_RETRIES) {
          log.error("Request did not succeed after " + MAX_RETRIES + "  retries ");
          throw new DataCollectionException(e);
        }
      } catch (Exception e) {
        if (retryCount == MAX_RETRIES) {
          throw new DataCollectionException(e);
        }
      }
      retryCount++;
    }
  }

  public <U> U executeRequest(ThirdPartyApiCallLog thirdPartyApiCallLog, Call<U> request) {
    return executeRequest(thirdPartyApiCallLog, request, null);
  }

  private String bodyToString(final Request request) {
    try {
      final Request copy = request.newBuilder().build();
      final Buffer buffer = new Buffer();
      copy.body().writeTo(buffer);
      return buffer.readUtf8();
    } catch (final IOException e) {
      throw new DataCollectionException(e);
    }
  }

  private String maskRequiredFieldsFromCallLogs(String field, Map<String, String> patternsToMask) {
    if (isNotEmpty(patternsToMask)) {
      for (Map.Entry<String, String> entry : patternsToMask.entrySet()) {
        field = field.replace(entry.getKey(), entry.getValue());
      }
    }

    if (field.contains("api_key")) {
      Pattern batchPattern = Pattern.compile(DATADOG_API_MASK);
      Matcher matcher = batchPattern.matcher(field);
      while (matcher.find()) {
        final String apiKey = matcher.group(1);
        field = field.replace(apiKey, "<apiKey>");
      }
    }

    if (field.contains("application_key")) {
      Pattern batchPattern = Pattern.compile(DATADOG_APP_MASK);
      Matcher matcher = batchPattern.matcher(field);
      while (matcher.find()) {
        final String appKey = matcher.group(1);
        field = field.replace(appKey, "<appKey>");
      }
    }
    return field;
  }

  private <U> U executeRequest(
      ThirdPartyApiCallLog apiCallLog, int retryCount, Call<U> request, Map<String, String> patternsToMask) {
    apiCallLog = apiCallLog.copy();
    apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    try {
      String urlString = request.request().url().toString();
      urlString = maskRequiredFieldsFromCallLogs(urlString, patternsToMask);
      apiCallLog.addFieldToRequest(
          ThirdPartyApiCallField.builder().name(URL_STRING).value(urlString).type(FieldType.URL).build());
      if (retryCount != 0) {
        apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                         .name("RETRY")
                                         .value(String.valueOf(retryCount))
                                         .type(FieldType.NUMBER)
                                         .build());
      }
      apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                       .name("METHOD")
                                       .value(request.request().method())
                                       .type(FieldType.TEXT)
                                       .build());
      if (request.request().body() != null) {
        String body = bodyToString(request.request());
        if (isNotEmpty(body)) {
          body = maskRequiredFieldsFromCallLogs(body, patternsToMask);
          apiCallLog.addFieldToRequest(
              ThirdPartyApiCallField.builder().name("body").value(body).type(FieldType.JSON).build());
        }
      }
      Response<U> response = request.clone().execute();
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      if (response.isSuccessful()) {
        apiCallLog.addFieldToResponse(response.code(), response.body(), FieldType.JSON);

      } else if (response.code() == RATE_LIMIT_STATUS) {
        apiCallLog.addFieldToResponse(response.code(), response.toString(), FieldType.TEXT);
        throw new RateLimitExceededException(
            "Response code: " + response.code() + " Error: " + response.errorBody().string());
      } else {
        apiCallLog.addFieldToResponse(response.code(), response.toString(), FieldType.TEXT);
        throw new DataCollectionException(
            "Response code: " + response.code() + " Error: " + response.errorBody().string());
      }
      return response.body();
    } catch (IOException e) {
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), FieldType.TEXT);
      throw new DataCollectionException(e);
    } finally {
      delegateLogService.save(apiCallLog.getAccountId(), apiCallLog);
    }
  }
}
