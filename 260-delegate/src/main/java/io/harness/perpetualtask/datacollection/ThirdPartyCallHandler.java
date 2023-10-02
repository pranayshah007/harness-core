/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.beans.cvnglog.ApiCallLogDTO.REQUEST_HEADERS;
import static io.harness.cvng.beans.cvnglog.ApiCallLogDTO.REQUEST_METHOD;
import static io.harness.cvng.beans.cvnglog.ApiCallLogDTO.REQUEST_URL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.utils.DataCollectionUtils;
import io.harness.serializer.JsonUtils;

import software.wings.delegatetasks.DelegateLogService;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@OwnedBy(CV)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ThirdPartyCallHandler implements Consumer<CallDetails> {
  private String accountId;
  private String requestUuid;
  private DelegateLogService delegateLogService;
  private Instant startTime;
  private Instant endTime;

  public ThirdPartyCallHandler(
      String accountId, String requestUuid, DelegateLogService delegateLogService, Instant startTime, Instant endTime) {
    this.accountId = accountId;
    this.requestUuid = requestUuid;
    this.delegateLogService = delegateLogService;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  @Override
  public void accept(CallDetails callDetails) {
    // Actual data coll path TODO remove
    Call callDetailsRequest = callDetails.getRequest();
    Preconditions.checkNotNull(callDetailsRequest, "Call Details request is null.");
    final ApiCallLogDTO cvngLogDTO = ApiCallLogDTO.builder()
                                         .traceableId(requestUuid)
                                         .traceableType(TraceableType.VERIFICATION_TASK)
                                         .accountId(accountId)
                                         .startTime(startTime.toEpochMilli())
                                         .endTime(endTime.toEpochMilli())
                                         .requestTime(callDetails.getRequestTime().toEpochMilli())
                                         .responseTime(callDetails.getResponseTime().toEpochMilli())
                                         .build();
    Request request = callDetailsRequest.request();
    String encodedURL = request.url().toString();
    cvngLogDTO.addFieldToRequest(ApiCallLogDTOField.builder()
                                     .name(REQUEST_URL)
                                     .type(ApiCallLogDTO.FieldType.URL)
                                     .value(URLDecoder.decode(encodedURL, StandardCharsets.UTF_8))
                                     .build());
    String method = request.method();
    cvngLogDTO.addFieldToRequest(
        ApiCallLogDTOField.builder().type(ApiCallLogDTO.FieldType.TEXT).name(REQUEST_METHOD).value(method).build());
    Set<String> headerNames = request.headers().names();
    if (headerNames.size() > 0) {
      cvngLogDTO.addFieldToRequest(ApiCallLogDTOField.builder()
                                       .type(ApiCallLogDTO.FieldType.TEXT)
                                       .name(REQUEST_HEADERS)
                                       .value(JsonUtils.asJson(headerNames))
                                       .build());
    }
    setApiCallLogDTOWithResponse(callDetails, cvngLogDTO);
    delegateLogService.save(accountId, cvngLogDTO);
  }

  private static void setApiCallLogDTOWithResponse(CallDetails callDetails, ApiCallLogDTO cvngLogDTO) {
    Call request = callDetails.getRequest();
    if (request.request().body() != null) {
      cvngLogDTO.addCallDetailsBodyFieldToRequest(request.request());
    }
    Response response = callDetails.getResponse();
    long responseTimeinMs = callDetails.getResponseTime().toEpochMilli() - callDetails.getRequestTime().toEpochMilli();
    if (response != null && response.body() != null) {
      cvngLogDTO.addFieldToResponse(response.code(), responseTimeinMs, response.body(), ApiCallLogDTO.FieldType.JSON);
    } else if (response != null && response.errorBody() != null) {
      try {
        cvngLogDTO.addFieldToResponse(response.code(), responseTimeinMs,
            DataCollectionUtils.getErrorBodyString(response), ApiCallLogDTO.FieldType.JSON);
      } catch (IOException ignored) {
      }
    }
  }
}
