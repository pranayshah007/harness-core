/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.cvnglog;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import org.apache.commons.text.StringEscapeUtils;

@OwnedBy(CV)
@UtilityClass
@Slf4j
public class ApiCallLogUtils {
  final String HIDDEN_VALUE = "****";
  final String CLIENT_SECRET = "client_secret";
  final List<String> sensitiveKeys = List.of(CLIENT_SECRET);

  public static String requestBodyToString(final Request request, boolean isEscapeWhitespace) {
    try {
      final Request copy = request.newBuilder().build();
      final Buffer buffer = new Buffer();
      Objects.requireNonNull(copy.body()).writeTo(buffer);
      if (isEscapeWhitespace) {
        return StringEscapeUtils.unescapeJson(buffer.readUtf8());
      } else {
        return buffer.readUtf8();
      }
    } catch (final IOException e) {
      return "cannot parse the byteArray Request Body";
    }
  }

  public static ApiCallLogDTO.FieldType mapRequestBodyContentTypeToFieldType(Request request) {
    ApiCallLogDTO.FieldType fieldType = ApiCallLogDTO.FieldType.TEXT;
    try {
      String mediaType = Objects.requireNonNull(Objects.requireNonNull(request.body()).contentType()).toString();
      boolean isJSONMediaType =
          Pattern.compile(Pattern.quote("json"), Pattern.CASE_INSENSITIVE).matcher(mediaType).find();
      if (isJSONMediaType) {
        fieldType = ApiCallLogDTO.FieldType.JSON;
      }
    } catch (Exception ignored) {
    }
    return fieldType;
  }

  public static boolean isFormEncoded(Request request) {
    boolean isFormEncoded = false;
    try {
      String mediaType = Objects.requireNonNull(Objects.requireNonNull(request.body()).contentType()).toString();
      isFormEncoded =
          Pattern.compile(Pattern.quote("x-www-form-urlencoded"), Pattern.CASE_INSENSITIVE).matcher(mediaType).find();
    } catch (Exception ignored) {
    }
    return isFormEncoded;
  }

  public static Request requestWithoutSensitiveKeys(Request request) {
    if (areSensitiveKeysPresentInRequestBody(request.body())) {
      FormBody.Builder formBodyBuilder = new FormBody.Builder();
      FormBody existingFormBody = (FormBody) request.body();
      Headers existingHeaders = request.headers();
      for (int i = 0; i < existingFormBody.size(); i++) {
        if (!sensitiveKeys.contains(existingFormBody.encodedName(i))) {
          formBodyBuilder.addEncoded(existingFormBody.encodedName(i), existingFormBody.encodedValue(i));
        } else {
          formBodyBuilder.addEncoded(existingFormBody.encodedName(i), HIDDEN_VALUE);
        }
      }
      Request.Builder requestBuilder = new Request.Builder().url(request.url()).post(formBodyBuilder.build());
      for (int i = 0; i < existingHeaders.size(); i++) {
        requestBuilder.addHeader(existingHeaders.name(i), existingHeaders.value(i));
      }
      return requestBuilder.build();
    }
    return request;
  }

  private boolean areSensitiveKeysPresentInRequestBody(RequestBody requestBody) {
    if (requestBody instanceof FormBody) {
      FormBody formBody = (FormBody) requestBody;
      for (int i = 0; i < formBody.size(); i++) {
        if (sensitiveKeys.contains(formBody.encodedName(i))) {
          return true;
        }
      }
    }
    return false;
  }
}
