/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.HttpResponseException;
import io.harness.rule.Owner;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class SafeHttpCallTest extends CategoryTest {
  @Test(expected = GeneralException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNullResponseExecuteWithExceptions() throws IOException {
    Call<String> call = spy(Call.class);
    doReturn(null).when(call).execute();
    SafeHttpCall.executeWithExceptions(call);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testSuccessfulExecuteWithExceptions() throws IOException {
    String msg = "hello";
    Call<String> call = spy(Call.class);
    doReturn(Response.success(msg)).when(call).execute();
    String retVal = SafeHttpCall.executeWithExceptions(call);
    assertThat(retVal).isEqualTo(msg);
  }

  @Test(expected = HttpResponseException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUnsuccessfulExecuteWithExceptions() throws IOException {
    Call<String> call = spy(Call.class);
    doReturn(prepareErrorResponse(501)).when(call).execute();
    SafeHttpCall.executeWithExceptions(call);
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidateNullResponse() throws IOException {
    SafeHttpCall.validateResponse(null);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidateSuccessfulResponse() throws IOException {
    SafeHttpCall.validateResponse(Response.success("hello"));
  }

  @Test(expected = HttpResponseException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidateUnsuccessfulResponse() throws IOException {
    SafeHttpCall.validateResponse(prepareErrorResponse(401));
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidateSuccessfulRawResponse() throws IOException {
    SafeHttpCall.validateResponse(Response.success("test", prepareRawResponse(200)));
  }

  @Test(expected = HttpResponseException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidateUnsuccessfulRawResponse() throws IOException {
    SafeHttpCall.validateResponse(
        Response.error(ResponseBody.create(MediaType.parse("text/plain"), "MSG"), prepareRawResponse(401)));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testValidateErrorMessageFromErrorBody() throws IOException {
    assertThatThrownBy(()
                           -> SafeHttpCall.validateResponse(Response.error(
                               ResponseBody.create(MediaType.parse("text/plain"), "MSG"), prepareRawResponse(400))))
        .isInstanceOf(HttpResponseException.class)
        .hasMessage("Unsuccessful HTTP call: status code = 400, message = MSG");
  }

  private Response<String> prepareErrorResponse(int code) {
    return error(code, ResponseBody.create(MediaType.parse("text/plain"), "MSG"));
  }

  private okhttp3.Response prepareRawResponse(int code) {
    Request request = new Request.Builder().url("https://dummyurl.com").method("GET", null).build();
    return new okhttp3.Response.Builder()
        .code(code)
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .message("MSG")
        .build();
  }

  private <String> Response<String> error(int code, ResponseBody body) {
    if (code < 400) {
      throw new IllegalArgumentException("code < 400: " + code);
    }
    return Response.error(body,
        new okhttp3.Response
            .Builder() //
            .code(code)
            .protocol(Protocol.HTTP_1_1)
            .request(new Request.Builder().url("http://localhost/").build())
            .message("err")
            .build());
  }
}
