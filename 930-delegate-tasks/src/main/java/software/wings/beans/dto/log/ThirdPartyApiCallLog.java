/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans.dto;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.ThirdPartyApiCallLogDetails;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.apache.http.HttpStatus;

/**
 * Created by rsingh on 1/8/18.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"validUntil"})
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ThirdPartyApiCallLogKeys")
public class ThirdPartyApiCallLog {
  public static final String NO_STATE_EXECUTION_ID = "NO_STATE_EXECUTION";
  private static final int MAX_JSON_RESPONSE_LENGTH = 16384;
  public static final String PAYLOAD = "Payload";
  public static final String RESPONSE_BODY = "Response Body";
  public static final String STATUS_CODE = "Status Code";

  private String stateExecutionId;
  private String accountId;
  private String delegateId;
  private String delegateTaskId;
  private String title;
  private List<ThirdPartyApiCallField> request;
  private List<ThirdPartyApiCallField> response;
  private long requestTimeStamp;
  private long responseTimeStamp;
  private long createdAt;
  private String uuid;

  @JsonIgnore private Date validUntil = Date.from(OffsetDateTime.now().plusWeeks(2).toInstant());

  public ThirdPartyApiCallLog copy() {
    return ThirdPartyApiCallLog.builder()
        .stateExecutionId(stateExecutionId)
        .accountId(accountId)
        .delegateId(delegateId)
        .delegateTaskId(delegateTaskId)
        .request(new ArrayList<>())
        .response(new ArrayList<>())
        .title(title)
        .build();
  }

  public void addFieldToResponse(int statusCode, Object response, FieldType fieldType) {
    if (this.response == null) {
      this.response = new ArrayList<>();
    }
    String jsonResponse = getResponseToLog(response, fieldType);
    this.response.add(ThirdPartyApiCallField.builder()
                          .type(FieldType.NUMBER)
                          .name(STATUS_CODE)
                          .value(Integer.toString(statusCode))
                          .build());
    this.response.add(
        ThirdPartyApiCallField.builder()
            .type(fieldType)
            .name(RESPONSE_BODY)
            .value(jsonResponse.substring(
                0, jsonResponse.length() < MAX_JSON_RESPONSE_LENGTH ? jsonResponse.length() : MAX_JSON_RESPONSE_LENGTH))
            .build());
  }

  private String getResponseToLog(Object response, FieldType fieldType) {
    if (fieldType == null) {
      return response.toString();
    }

    switch (fieldType) {
      case JSON:
        try {
          if (response instanceof String) {
            return response.toString();
          }
          return JsonUtils.asJson(response);
        } catch (Exception e) {
          return response.toString();
        }
      default:
        return response.toString();
    }
  }

  public void addFieldToRequest(ThirdPartyApiCallField field) {
    if (request == null) {
      request = new ArrayList<>();
    }
    request.add(field);
  }

  public static ThirdPartyApiCallLog createApiCallLog(String accountId, String stateExecutionId) {
    return ThirdPartyApiCallLog.builder()
        .accountId(accountId)
        .stateExecutionId(isEmpty(stateExecutionId) ? NO_STATE_EXECUTION_ID : stateExecutionId)
        .build();
  }

  @Data
  @Builder
  public static class ThirdPartyApiCallField {
    private String name;
    private String value;
    @Default private FieldType type = FieldType.TEXT;
  }

  public enum FieldType { JSON, XML, NUMBER, URL, TEXT, TIMESTAMP }

  public ExecutionStatus getStatus() {
    /*
     In an unexpected scenario where the response is an empty list of fields,
     we report it as a failed API Call
     This can only happen if the addFieldToResponse(...) method was never invoked
     on this instance.
     This is also for preventing NPE while getting 0th index of response
     */
    if (this.response != null && this.response.isEmpty()) {
      return ExecutionStatus.FAILED;
    }
    if (this.response != null
        && this.response.stream().anyMatch(
            obj -> obj.getName().equals(STATUS_CODE) && !obj.getValue().equals(String.valueOf(HttpStatus.SC_OK)))) {
      return ExecutionStatus.FAILED;
    }

    return ExecutionStatus.SUCCESS;
  }

  public static ThirdPartyApiCallLog fromDetails(ThirdPartyApiCallLogDetails details) {
    if (details == null) {
      return null;
    }
    return ThirdPartyApiCallLog.builder()
        .accountId(details.getAccountId())
        .delegateId(details.getDelegateId())
        .delegateTaskId(details.getDelegateTaskId())
        .stateExecutionId(details.getStateExecutionId())
        .build();
  }
}
