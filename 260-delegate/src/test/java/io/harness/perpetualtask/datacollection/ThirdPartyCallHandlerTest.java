/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.datacollection.entity.CallDetails;
import io.harness.rule.Owner;

import software.wings.delegatetasks.DelegateLogService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CV)
public class ThirdPartyCallHandlerTest extends DelegateTestBase {
  private DelegateLogService delegateLogService = mock(DelegateLogService.class);
  private String accountId;
  private String requestUuid;
  private Instant startTime;
  private Instant endTime;

  @Before
  public void setup() {
    accountId = generateUuid();
    requestUuid = generateUuid();
    startTime = OffsetDateTime.now().toInstant();
    endTime = OffsetDateTime.now().toInstant();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category({UnitTests.class})
  public void testAccept_withDefaultRequest() {
    ArgumentCaptor<ApiCallLogDTO> apiCallLogCaptor = ArgumentCaptor.forClass(ApiCallLogDTO.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    ThirdPartyCallHandler thirdPartyCallHandler =
        new ThirdPartyCallHandler(accountId, requestUuid, delegateLogService, startTime, endTime);
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> response = Response.success(responseStr);
    CallDetails callDetails =
        CallDetails.builder().request(call).response(response).requestTime(startTime).responseTime(endTime).build();
    thirdPartyCallHandler.accept(callDetails);
    verify(delegateLogService, times(1)).save(accountIdCaptor.capture(), apiCallLogCaptor.capture());
    assertThat(accountIdCaptor.getValue()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getAccountId()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getStartTime()).isEqualTo(startTime.toEpochMilli());
    assertThat(apiCallLogCaptor.getValue().getEndTime()).isEqualTo(endTime.toEpochMilli());
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category({UnitTests.class})
  public void testAccept_withException() throws IOException {
    ArgumentCaptor<ApiCallLogDTO> apiCallLogCaptor = ArgumentCaptor.forClass(ApiCallLogDTO.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    ThirdPartyCallHandler thirdPartyCallHandler =
        new ThirdPartyCallHandler(accountId, requestUuid, delegateLogService, startTime, endTime);
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, "{\"jsonExample\":\"value\"}");
    Request request =
        new Request.Builder().url("http://example.com/test").post(body).addHeader("headerName", "headerValue").build();
    Call<String> call = mock(Call.class);
    when(call.request()).thenReturn(request);
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode objectNode = objectMapper.createObjectNode();
    objectNode.put("error", "Error Message");
    objectNode.put("isAPIError", false);
    Response<Object> error = Response.error(
        500, ResponseBody.create(MediaType.parse("application/json"), objectMapper.writeValueAsString(objectNode)));
    CallDetails callDetails =
        CallDetails.builder().request(call).response(error).requestTime(startTime).responseTime(endTime).build();
    thirdPartyCallHandler.accept(callDetails);
    verify(delegateLogService, times(1)).save(accountIdCaptor.capture(), apiCallLogCaptor.capture());
    assertThat(accountIdCaptor.getValue()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getAccountId()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getStartTime()).isEqualTo(startTime.toEpochMilli());
    assertThat(apiCallLogCaptor.getValue().getRequests().size()).isEqualTo(4);
    assertThat(apiCallLogCaptor.getValue().getRequests().size()).isEqualTo(4);
    assertThat(apiCallLogCaptor.getValue().getRequests().get(0).getName()).isEqualTo("url");
    assertThat(apiCallLogCaptor.getValue().getRequests().get(1).getName()).isEqualTo("Request Method");
    assertThat(apiCallLogCaptor.getValue().getRequests().get(2).getName()).isEqualTo("Request Headers");
    assertThat(apiCallLogCaptor.getValue().getRequests().get(3).getName()).isEqualTo("Request Body");
    assertThat(apiCallLogCaptor.getValue().getResponses().size()).isEqualTo(2);
    assertThat(apiCallLogCaptor.getValue().getResponses().get(1).getName()).isEqualTo("Response Body");
    assertThat(apiCallLogCaptor.getValue().getResponses().get(0).getValue()).isEqualTo("500");
    assertThat(apiCallLogCaptor.getValue().getResponses().get(1).getValue())
        .isEqualTo("{\"error\":\"Error Message\",\"isAPIError\":false}");
  }
  @Test
  @Owner(developers = ANSUMAN)
  @Category({UnitTests.class})
  public void testAccept_withDefaultRequestAndBody() {
    ArgumentCaptor<ApiCallLogDTO> apiCallLogCaptor = ArgumentCaptor.forClass(ApiCallLogDTO.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    ThirdPartyCallHandler thirdPartyCallHandler =
        new ThirdPartyCallHandler(accountId, requestUuid, delegateLogService, startTime, endTime);
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, "{\"jsonExample\":\"value\"}");
    Request request =
        new Request.Builder().url("http://example.com/test").post(body).addHeader("headerName", "headerValue").build();
    Call<String> call = mock(Call.class);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> response = Response.success(responseStr);
    CallDetails callDetails =
        CallDetails.builder().request(call).response(response).requestTime(startTime).responseTime(endTime).build();
    thirdPartyCallHandler.accept(callDetails);
    verify(delegateLogService, times(1)).save(accountIdCaptor.capture(), apiCallLogCaptor.capture());
    assertThat(accountIdCaptor.getValue()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getAccountId()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getStartTime()).isEqualTo(startTime.toEpochMilli());
    assertThat(apiCallLogCaptor.getValue().getRequests().size()).isEqualTo(4);
    assertThat(apiCallLogCaptor.getValue().getRequests().size()).isEqualTo(4);
    assertThat(apiCallLogCaptor.getValue().getRequests().get(0).getName()).isEqualTo("url");
    assertThat(apiCallLogCaptor.getValue().getRequests().get(1).getName()).isEqualTo("Request Method");
    assertThat(apiCallLogCaptor.getValue().getRequests().get(2).getName()).isEqualTo("Request Headers");
    assertThat(apiCallLogCaptor.getValue().getRequests().get(3).getName()).isEqualTo("Request Body");
    assertThat(apiCallLogCaptor.getValue().getResponses().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category({UnitTests.class})
  public void testAccept_withNULLRequest() {
    ThirdPartyCallHandler thirdPartyCallHandler =
        new ThirdPartyCallHandler(accountId, requestUuid, delegateLogService, startTime, endTime);
    CallDetails callDetails = CallDetails.builder().build();
    assertThatThrownBy(() -> thirdPartyCallHandler.accept(callDetails))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Call Details request is null.");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category({UnitTests.class})
  public void testAccept_withNULLBody() {
    ArgumentCaptor<ApiCallLogDTO> apiCallLogCaptor = ArgumentCaptor.forClass(ApiCallLogDTO.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    ThirdPartyCallHandler thirdPartyCallHandler =
        new ThirdPartyCallHandler(accountId, requestUuid, delegateLogService, startTime, endTime);
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.request()).thenReturn(request);
    Response<String> response = Response.success(null);
    CallDetails callDetails =
        CallDetails.builder().request(call).response(response).requestTime(startTime).responseTime(endTime).build();
    thirdPartyCallHandler.accept(callDetails);
    verify(delegateLogService, times(1)).save(accountIdCaptor.capture(), apiCallLogCaptor.capture());
    assertThat(accountIdCaptor.getValue()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getAccountId()).isEqualTo(accountId);
    assertThat(apiCallLogCaptor.getValue().getStartTime()).isEqualTo(startTime.toEpochMilli());
    assertThat(apiCallLogCaptor.getValue().getEndTime()).isEqualTo(endTime.toEpochMilli());
  }
}
