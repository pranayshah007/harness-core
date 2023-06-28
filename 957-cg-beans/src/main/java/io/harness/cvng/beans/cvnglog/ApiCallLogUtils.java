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
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okio.Buffer;
import org.apache.commons.text.StringEscapeUtils;

@OwnedBy(CV)
@UtilityClass
@Slf4j
public class ApiCallLogUtils {
  public static String requestBodyToString(final Request request) {
    try {
      final Request copy = request.newBuilder().build();
      final Buffer buffer = new Buffer();
      Objects.requireNonNull(copy.body()).writeTo(buffer);
      return StringEscapeUtils.unescapeJava(buffer.readUtf8());
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
}
