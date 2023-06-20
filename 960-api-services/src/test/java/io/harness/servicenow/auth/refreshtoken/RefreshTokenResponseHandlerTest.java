/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.INVALID_CREDENTIALS;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.NOT_FOUND;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.REFRESH_TOKEN_ERROR_PREFIX;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ServiceNowOIDCException;
import io.harness.rule.Owner;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Response;

@OwnedBy(CDC)
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class RefreshTokenResponseHandlerTest extends CategoryTest {
  private static final String BEARER_TOKEN = "Bearer test token&&%$%^%$test token";
  private static final String FAILURE_MESSAGE = "Failed to get access token using refresh token grant type";
  @Mock Response<AccessTokenResponse> mockedResponse;
  @Mock ResponseBody mockedResponseBody;

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHandleSuccessfulResponse() {
    Response<AccessTokenResponse> response =
        Response.success(200, AccessTokenResponse.builder().authToken(BEARER_TOKEN).expiresIn(1800).build());
    assertThatCode(() -> RefreshTokenResponseHandler.handleResponse(response, FAILURE_MESSAGE))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHandleUnauthorizedResponse() {
    Response<AccessTokenResponse> response = Response.error(401, ResponseBody.create("", MediaType.parse("JSON")));
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(response, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, INVALID_CREDENTIALS));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHandleNotFoundResponse() {
    Response<AccessTokenResponse> response = Response.error(404, ResponseBody.create("", MediaType.parse("JSON")));
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(response, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, NOT_FOUND));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFailedResponseWithoutErrorBody() {
    when(mockedResponse.errorBody()).thenReturn(null);
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(mockedResponse, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(
            String.format("%s: %s : %s", REFRESH_TOKEN_ERROR_PREFIX, FAILURE_MESSAGE, mockedResponse.message()));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFailedResponseWithErrorBody() throws IOException {
    when(mockedResponse.errorBody()).thenReturn(mockedResponseBody);
    when(mockedResponse.code()).thenReturn(400);
    when(mockedResponseBody.string())
        .thenReturn("{\n"
            + "  \"error_description\": \"access_denied\",\n"
            + "  \"error\": \"server_error\"\n"
            + "}");
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(mockedResponse, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: [%s] : %s", REFRESH_TOKEN_ERROR_PREFIX, "server_error", "access_denied"));

    when(mockedResponseBody.string())
        .thenReturn("{\n"
            + "  \"errorCode\": \"invalid_client\",\n"
            + "  \"errorSummary\": \"Invalid value for 'client_id' parameter.\",\n"
            + "  \"errorLink\": \"invalid_client\",\n"
            + "  \"errorId\": \"LnD6JtDSHeCKuJKR7fDPQ\",\n"
            + "  \"errorCauses\": []\n"
            + "}");
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(mockedResponse, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format(
            "%s: [%s] : %s", REFRESH_TOKEN_ERROR_PREFIX, "invalid_client", "Invalid value for 'client_id' parameter."));

    when(mockedResponseBody.string())
        .thenReturn("{\n"
            + "  \"errorCode\": \"invalid_client\"\n"
            + "}");
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(mockedResponse, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, "invalid_client"));

    when(mockedResponseBody.string())
        .thenReturn("{\n"
            + "  \"errorCode\": \"invalid_client\",\n"
            + "  \"errorLink\": \"invalid_client\",\n"
            + "  \"errorId\": \"LnD6JtDSHeCKuJKR7fDPQ\",\n"
            + "  \"errorCauses\": []\n"
            + "}");
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(mockedResponse, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, "invalid_client"));

    when(mockedResponseBody.string())
        .thenReturn("{\n"
            + "  \"error_description\": \"access_denied\"\n"
            + "}");
    assertThatThrownBy(() -> RefreshTokenResponseHandler.handleResponse(mockedResponse, FAILURE_MESSAGE))
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX,
            "{\n"
                + "  \"error_description\": \"access_denied\"\n"
                + "}"));
  }
}