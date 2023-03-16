/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response.Builder;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class RequestExecutorTest extends CategoryTest {
  @Mock private DelegateLogService delegateLogService;
  private RequestExecutor requestExecutor;
  private String stateExecutionId = UUID.randomUUID().toString();
  private String accountId = UUID.randomUUID().toString();
  private String title = "api call title";

  @Before
  public void setupTests() throws IllegalAccessException {
    initMocks(this);
    requestExecutor = new RequestExecutor();
    FieldUtils.writeField(requestExecutor, "delegateLogService", delegateLogService, true);
    CVConstants.RETRY_SLEEP_DURATION = Duration.ofMillis(1); // to run retry based test faster.
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void executeRequestAndGenerateCorrectThirdPartyAPILogs() throws IOException {
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> response = Response.success(responseStr);
    when(call.execute()).thenReturn(response);
    String returnedStr = requestExecutor.executeRequest(create(), call);
    assertThat(returnedStr).isEqualTo(responseStr);
    ArgumentCaptor<ThirdPartyApiCallLog> thirdPartyApiCallLogArgumentCaptor =
        ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(delegateLogService).save(eq(accountId), thirdPartyApiCallLogArgumentCaptor.capture());
    ThirdPartyApiCallLog thirdPartyApiCallLog = thirdPartyApiCallLogArgumentCaptor.getValue();
    assertThat(thirdPartyApiCallLog.getTitle()).isEqualTo(title);
    assertThat(thirdPartyApiCallLog.getRequest().get(0).getName()).isEqualTo("Url");
    assertThat(thirdPartyApiCallLog.getRequest().get(0).getValue()).isEqualTo("http://example.com/test");
    assertThat(thirdPartyApiCallLog.getRequest().get(0).getType()).isEqualTo(FieldType.URL);
    assertThat(thirdPartyApiCallLog.getResponse().get(0).getValue()).isEqualTo("200");
    assertThat(thirdPartyApiCallLog.getResponse().get(0).getType()).isEqualTo(FieldType.NUMBER);
    assertThat(thirdPartyApiCallLog.getResponse().get(0).getName()).isEqualTo("Status Code");
    assertThat(thirdPartyApiCallLog.getResponse().get(1).getValue()).isEqualTo(responseStr);
    assertThat(thirdPartyApiCallLog.getResponse().get(1).getType()).isEqualTo(FieldType.JSON);
    assertThat(thirdPartyApiCallLog.getResponse().get(1).getName()).isEqualTo("Response Body");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void executeRequest_CorrectBodyInTheThirdPartyAPILogs() throws IOException {
    Request request = new Request.Builder()
                          .url("http://example.com/test")
                          .post(RequestBody.create(MediaType.parse("json"), "[{\"a\": \"b\"}]"))
                          .build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> response = Response.success(responseStr);
    when(call.execute()).thenReturn(response);
    String returnedStr = requestExecutor.executeRequest(create(), call);
    assertThat(returnedStr).isEqualTo(responseStr);
    ArgumentCaptor<ThirdPartyApiCallLog> thirdPartyApiCallLogArgumentCaptor =
        ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(delegateLogService).save(eq(accountId), thirdPartyApiCallLogArgumentCaptor.capture());
    ThirdPartyApiCallLog thirdPartyApiCallLog = thirdPartyApiCallLogArgumentCaptor.getValue();
    assertThat(thirdPartyApiCallLog.getTitle()).isEqualTo(title);
    ThirdPartyApiCallField thirdPartyApiCallField =
        thirdPartyApiCallLog.getRequest().stream().filter(field -> field.getName().equals("body")).findFirst().get();
    assertThat("[{\"a\": \"b\"}]").isEqualTo(thirdPartyApiCallField.getValue());
    assertThat(FieldType.JSON).isEqualTo(thirdPartyApiCallField.getType());
    assertThat("body").isEqualTo(thirdPartyApiCallField.getName());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void executeRequestWithRetryIfException() throws IOException {
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    when(call.execute()).thenThrow(new IOException("exception from test"));
    assertThatThrownBy(() -> requestExecutor.executeRequest(create(), call))
        .isInstanceOf(DataCollectionException.class);

    ArgumentCaptor<ThirdPartyApiCallLog> thirdPartyApiCallLogArgumentCaptor =
        ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(delegateLogService, times(3)).save(eq(accountId), thirdPartyApiCallLogArgumentCaptor.capture());
    List<ThirdPartyApiCallLog> thirdPartyApiCallLogs = thirdPartyApiCallLogArgumentCaptor.getAllValues();
    assertThat(thirdPartyApiCallLogs.size()).isEqualTo(3);
    for (int i = 0; i < thirdPartyApiCallLogs.size(); i++) {
      ThirdPartyApiCallLog thirdPartyApiCallLog = thirdPartyApiCallLogs.get(i);
      assertThat(thirdPartyApiCallLog.getRequest().get(0).getValue()).isEqualTo("http://example.com/test");
      if (i != 0) {
        assertThat(thirdPartyApiCallLog.getRequest().get(1).getName()).isEqualTo("RETRY");
        assertThat(thirdPartyApiCallLog.getRequest().get(1).getValue()).isEqualTo(String.valueOf(i)); // retry count
        assertThat(thirdPartyApiCallLog.getRequest().get(1).getType()).isEqualTo(FieldType.NUMBER);
      }
      assertThat(thirdPartyApiCallLog.getResponse().get(0).getType()).isEqualTo(FieldType.NUMBER);
      assertThat(thirdPartyApiCallLog.getResponse().get(0).getValue()).isEqualTo("400");
      assertThat(thirdPartyApiCallLog.getResponse().get(0).getName()).isEqualTo("Status Code");
      assertThat(thirdPartyApiCallLog.getResponse().get(1).getType()).isEqualTo(FieldType.TEXT);
      assertThat(thirdPartyApiCallLog.getResponse().get(1).getValue()).contains("exception from test");
      assertThat(thirdPartyApiCallLog.getResponse().get(1).getName()).isEqualTo("Response Body");
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteRequestRetrySuccessOnRateLimitExceeded() throws IOException {
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> response = Response.success(responseStr);
    Response<String> rateLimitResponse = tooManyRequestsResponse(responseStr);
    when(call.execute()).thenReturn(rateLimitResponse).thenReturn(response);
    String returnedStr = requestExecutor.executeRequest(create(), call);
    assertThat(returnedStr).isEqualTo(responseStr);
    ArgumentCaptor<ThirdPartyApiCallLog> thirdPartyApiCallLogArgumentCaptor =
        ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(delegateLogService, times(2)).save(eq(accountId), thirdPartyApiCallLogArgumentCaptor.capture());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteRequestRetryFailureAfterMaxRetriesOnRateLimitExceeded() throws IOException {
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> rateLimitResponse = tooManyRequestsResponse(responseStr);
    when(call.execute()).thenReturn(rateLimitResponse);
    assertThatThrownBy(() -> requestExecutor.executeRequest(create(), call))
        .isInstanceOf(DataCollectionException.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMaskFieldsInThirdPartyCalls() throws Exception {
    Request request = new Request.Builder().url("http://example.com/test&apiKey=12345&appKey=98765").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> response = Response.success(responseStr);
    when(call.execute()).thenReturn(response);
    Map<String, String> maskMap = new HashMap<>();
    maskMap.put("12345", "<apiKey>");
    maskMap.put("98765", "<appKey>");
    String returnedStr = requestExecutor.executeRequest(create(), call, maskMap);
    assertThat(returnedStr).isEqualTo(responseStr);
    ArgumentCaptor<ThirdPartyApiCallLog> thirdPartyApiCallLogArgumentCaptor =
        ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(delegateLogService, times(1)).save(eq(accountId), thirdPartyApiCallLogArgumentCaptor.capture());
    assertThat(thirdPartyApiCallLogArgumentCaptor.getValue()).isNotNull();
    ThirdPartyApiCallLog thirdPartyApiCallLog = thirdPartyApiCallLogArgumentCaptor.getValue();
    thirdPartyApiCallLog.getRequest().forEach(requestLog -> {
      if (requestLog.getType().equals(FieldType.URL)) {
        assertThat(requestLog.getValue()).isEqualTo("http://example.com/test&apiKey=<apiKey>&appKey=<appKey>");
      }
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteRequest_ifSuccessful() throws IOException {
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    String responseStr = "This is test response";
    Response<String> response = Response.success(responseStr);
    when(call.execute()).thenReturn(response);
    assertThat(requestExecutor.executeRequest(call)).isEqualTo(responseStr);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteRequest_Non200Response() throws IOException {
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    Response<String> response = tooManyRequestsResponse("to many requests");
    when(call.execute()).thenReturn(response);
    assertThatThrownBy(() -> requestExecutor.executeRequest(call))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Response code: 429, Message: test, Error: to many requests");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteRequest_InCaseOfException() throws IOException {
    Request request = new Request.Builder().url("http://example.com/test").build();
    Call<String> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.request()).thenReturn(request);
    when(call.execute()).thenThrow(new IOException("execute call failed"));
    assertThatThrownBy(() -> requestExecutor.executeRequest(call))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("execute call failed")
        .hasCauseInstanceOf(IOException.class);
  }

  private Response tooManyRequestsResponse(String responseStr) {
    return Response.error(ResponseBody.create(MediaType.parse("text/plain"), responseStr),
        new Builder()
            .code(429)
            .protocol(Protocol.HTTP_1_1)
            .message("test")
            .request(new Request.Builder().url("http://localhost/").build())
            .build());
  }

  private ThirdPartyApiCallLog create() {
    return ThirdPartyApiCallLog.builder().stateExecutionId(stateExecutionId).accountId(accountId).title(title).build();
  }
}
