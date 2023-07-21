/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.cvnglog;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import okhttp3.Request;

@Data
@SuperBuilder
@JsonTypeName("ApiCallLog")
@NoArgsConstructor
@OwnedBy(HarnessTeam.CV)
public class ApiCallLogDTO extends CVNGLogDTO {
  private static final int MAX_JSON_RESPONSE_LENGTH = 16384;
  public static final String PAYLOAD = "Payload";
  public static final String RESPONSE_BODY = "Response Body";
  public static final String REQUEST_BODY = "Request Body";
  public static final String REQUEST_URL = "url";
  public static final String STATUS_CODE = "Status Code";
  public static final String REQUEST_METHOD = "Request Method";
  public static final String REQUEST_HEADERS = "Request Headers";

  private List<ApiCallLogDTOField> requests;
  private List<ApiCallLogDTOField> responses;
  private long requestTime;
  private long responseTime;

  @Override
  public CVNGLogType getType() {
    return CVNGLogType.API_CALL_LOG;
  }

  public void addFieldToRequest(ApiCallLogDTOField field) {
    Preconditions.checkNotNull(field, "Api call log request field is null.");

    if (this.requests == null) {
      this.requests = new ArrayList<>();
    }
    requests.add(field);
  }

  public void addCallDetailsBodyFieldToRequest(Request request) {
    Preconditions.checkNotNull(request, "Api call request field is null.");
    if (this.requests == null) {
      this.requests = new ArrayList<>();
    }
    FieldType fieldType = ApiCallLogUtils.mapRequestBodyContentTypeToFieldType(request);
    String requestBody = getCallObjectToLog(ApiCallLogUtils.requestBodyToString(request, false), fieldType);
    if (fieldType == FieldType.TEXT && ApiCallLogUtils.isFormEncoded(request)) {
      requestBody = URLDecoder.decode(requestBody, StandardCharsets.UTF_8);
    }
    this.requests.add(ApiCallLogDTOField.builder()
                          .type(fieldType)
                          .name(REQUEST_BODY)
                          .value(requestBody.substring(0, Math.min(requestBody.length(), MAX_JSON_RESPONSE_LENGTH)))
                          .build());
  }

  public void addFieldToResponse(int statusCode, Object response, FieldType fieldType) {
    Preconditions.checkNotNull(response, "Api call log response field is null.");

    if (this.responses == null) {
      this.responses = new ArrayList<>();
    }
    String jsonResponse = getCallObjectToLog(response, fieldType);
    this.responses.add(ApiCallLogDTOField.builder()
                           .type(FieldType.NUMBER)
                           .name(STATUS_CODE)
                           .value(Integer.toString(statusCode))
                           .build());
    String trimmedResponse = jsonResponse.substring(0, Math.min(jsonResponse.length(), MAX_JSON_RESPONSE_LENGTH));
    this.responses.add(ApiCallLogDTOField.builder().type(fieldType).name(RESPONSE_BODY).value(trimmedResponse).build());
  }

  public void addFieldToResponse(ApiCallLogDTOField field) {
    Preconditions.checkNotNull(field, "Api call log response field is null.");

    if (this.responses == null) {
      this.responses = new ArrayList<>();
    }
    responses.add(field);
  }

  private String getCallObjectToLog(Object entity, FieldType fieldType) {
    if (fieldType == null) {
      return entity.toString();
    }
    switch (fieldType) {
      case JSON:
        try {
          if (entity instanceof String) {
            return entity.toString();
          }
          return JsonUtils.asPrettyJson(entity);
        } catch (Exception e) {
          return entity.toString();
        }
      default:
        return entity.toString();
    }
  }
  public List<ApiCallLogDTOField> getRequests() {
    if (requests == null) {
      return new ArrayList<>();
    }
    return requests;
  }

  public List<ApiCallLogDTOField> getResponses() {
    if (responses == null) {
      return new ArrayList<>();
    }
    return responses;
  }

  @Data
  @Builder
  public static class ApiCallLogDTOField {
    private String name;
    private String value;
    @Builder.Default private ApiCallLogDTO.FieldType type = ApiCallLogDTO.FieldType.TEXT;
  }

  public enum FieldType { JSON, XML, NUMBER, URL, TEXT, TIMESTAMP }
}
