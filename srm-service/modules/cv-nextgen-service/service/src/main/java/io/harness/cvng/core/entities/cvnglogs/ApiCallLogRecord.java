/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.cvnglogs;

import static io.harness.cvng.CVConstants.TAG_ACCOUNT_ID;
import static io.harness.cvng.CVConstants.TAG_DATA_SOURCE;
import static io.harness.cvng.CVConstants.TAG_VERIFICATION_TYPE;
import static io.harness.cvng.metrics.CVNGMetricsUtils.API_CALL_EXECUTION_TIME;
import static io.harness.cvng.metrics.CVNGMetricsUtils.API_CALL_RESPONSE_SIZE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.beans.ApiCallLogMetricContext;
import io.harness.metrics.AutoMetricContext;
import io.harness.metrics.service.api.MetricService;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@OwnedBy(HarnessTeam.CV)
@Slf4j
public class ApiCallLogRecord extends CVNGLogRecord {
  private List<ApiCallLogField> requests;
  private List<ApiCallLogField> responses;
  private Instant requestTime;
  private Instant responseTime;
  private static final int ERROR_RESPONSE_CODE = 400;
  private static final String RESPONSE_NAME = "Status Code";

  public void addFieldToRequest(ApiCallLogField field) {
    Preconditions.checkNotNull(field, "Api call log request field is null.");
    if (this.requests == null) {
      this.requests = new ArrayList<>();
    }
    requests.add(field);
  }

  public void addFieldToResponse(ApiCallLogField field) {
    Preconditions.checkNotNull(field, "Api call log response field is null.");
    if (this.responses == null) {
      this.responses = new ArrayList<>();
    }
    responses.add(field);
  }

  public List<ApiCallLogField> getRequests() {
    if (requests == null) {
      return new ArrayList<>();
    }
    return requests;
  }

  public List<ApiCallLogField> getResponses() {
    if (responses == null) {
      return new ArrayList<>();
    }
    return responses;
  }

  public static CVNGLogRecord toCVNGLogRecord(CVNGLogDTO logRecordDTO) {
    ApiCallLogRecord apiCallLogRecord =
        ApiCallLogRecord.builder()
            .requestTime(Instant.ofEpochMilli(((ApiCallLogDTO) logRecordDTO).getRequestTime()))
            .responseTime(Instant.ofEpochMilli(((ApiCallLogDTO) logRecordDTO).getResponseTime()))
            .build();

    (((ApiCallLogDTO) logRecordDTO).getRequests())
        .stream()
        .map(ApiCallLogRecord::toApiCallLogField)
        .collect(Collectors.toList())
        .forEach(request -> apiCallLogRecord.addFieldToRequest(request));

    (((ApiCallLogDTO) logRecordDTO).getResponses())
        .stream()
        .map(ApiCallLogRecord::toApiCallLogField)
        .collect(Collectors.toList())
        .forEach(response -> apiCallLogRecord.addFieldToResponse(response));

    return apiCallLogRecord;
  }

  private static ApiCallLogField toApiCallLogField(ApiCallLogDTOField apiCallLogDTOField) {
    return ApiCallLogField.builder()
        .name(apiCallLogDTOField.getName())
        .type(toApiCallLogFieldType(apiCallLogDTOField.getType()))
        .value(apiCallLogDTOField.getValue())
        .build();
  }

  private static FieldType toApiCallLogFieldType(ApiCallLogDTO.FieldType dtoFieldType) {
    return FieldType.valueOf(dtoFieldType.name());
  }

  @Override
  public CVNGLogDTO toCVNGLogDTO() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder()
                                      .requestTime(requestTime.toEpochMilli())
                                      .responseTime(responseTime.toEpochMilli())
                                      .createdAt(getCreatedAt())
                                      .build();
    getRequests().forEach(logRecord -> apiCallLogDTO.addFieldToRequest(toApiCallLogDTOField(logRecord)));
    getResponses().forEach(logRecord -> apiCallLogDTO.addFieldToResponse(toApiCallLogDTOField(logRecord)));
    return apiCallLogDTO;
  }

  @Override
  public boolean isErrorLog() {
    for (ApiCallLogField response : responses) {
      if (response.getName().equals(RESPONSE_NAME)) {
        return Integer.parseInt(response.getValue()) >= ERROR_RESPONSE_CODE;
      }
    }
    return false;
  }

  private ApiCallLogDTOField toApiCallLogDTOField(ApiCallLogField apiCallLogField) {
    return ApiCallLogDTOField.builder()
        .name(apiCallLogField.getName())
        .type(toApiCallLogDTOFieldType(apiCallLogField.getType()))
        .value(apiCallLogField.getValue())
        .build();
  }

  private ApiCallLogDTO.FieldType toApiCallLogDTOFieldType(FieldType fieldType) {
    return ApiCallLogDTO.FieldType.valueOf(fieldType.name());
  }

  @Data
  @Builder
  public static class ApiCallLogField {
    private String name;
    private String value;
    @Builder.Default private ApiCallLogRecord.FieldType type = ApiCallLogRecord.FieldType.TEXT;
  }
  public enum FieldType { JSON, XML, NUMBER, URL, TEXT, TIMESTAMP }

  @Override
  public void recordsMetrics(MetricService metricService, Map<String, String> tags) {
    try (AutoMetricContext cvngLogMetricContext = new ApiCallLogMetricContext(tags.get(TAG_ACCOUNT_ID),
             tags.get(TAG_DATA_SOURCE) != null ? tags.get(TAG_DATA_SOURCE).toLowerCase() : null,
             tags.get(TAG_VERIFICATION_TYPE))) {
      metricService.recordDuration(
          API_CALL_EXECUTION_TIME, Duration.between(this.getRequestTime(), this.getResponseTime()));
      if (this.getResponses().size() >= 2) {
        metricService.recordMetric(API_CALL_RESPONSE_SIZE, this.getResponses().get(1).getValue().getBytes().length);
      }
      if (this.getResponses().size() >= 1) {
        metricService.incCounter(
            CVNGMetricsUtils.getApiCallLogResponseCodeMetricName(this.getResponses().get(0).getValue()));
      }
    }
  }
}
