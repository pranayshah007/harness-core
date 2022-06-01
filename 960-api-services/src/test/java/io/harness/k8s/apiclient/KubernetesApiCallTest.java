/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.apiclient;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExplanationException;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Pair;
import java.lang.reflect.Type;
import java.util.function.Supplier;
import okhttp3.Call;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class KubernetesApiCallTest extends CategoryTest {
  private static final String REQUEST_METHOD = "GET";
  private static final String REQUEST_URL = "https://app.harness.io/";
  private static final int RESPONSE_CODE = 400;
  private static final String EXCEPTION_MESSAGE = "Sample exception message";

  @Mock ApiClient apiClient;
  @Mock Call call;
  Request request;
  Request requestWithUrl = new Request.Builder().method(REQUEST_METHOD, null).url(REQUEST_URL).build();

  @Before
  public void setup() throws ApiException {
    MockitoAnnotations.initMocks(this);
    doReturn(call).when(apiClient).buildCall(anyString(), anyString(), anyListOf(Pair.class), anyListOf(Pair.class),
        anyObject(), anyMapOf(String.class, String.class), anyMapOf(String.class, String.class),
        anyMapOf(String.class, Object.class), any(), any(ApiCallback.class));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sApiCallWithGenericError() throws ApiException {
    doReturn(request).when(call).request();
    doThrow(ApiException.class).when(apiClient).execute(any(Call.class), any(Type.class));
    KubernetesApiCall.ApiCallSupplier apiCallSupplier = mock(KubernetesApiCall.ApiCallSupplier.class);
    doReturn(call).when(apiCallSupplier).get();

    Supplier<Void> versionApiCall = () -> {
      KubernetesApiCall.call(apiClient, apiCallSupplier);
      return null;
    };
    assertThatThrownBy(versionApiCall::get).isInstanceOf(ExplanationException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sApiCallWithConnectionFailure() throws ApiException {
    doReturn(requestWithUrl).when(call).request();
    doThrow(new ApiException(0, EXCEPTION_MESSAGE)).when(apiClient).execute(any(Call.class), any(Type.class));
    KubernetesApiCall.ApiCallSupplier apiCallSupplier = mock(KubernetesApiCall.ApiCallSupplier.class);
    doReturn(call).when(apiCallSupplier).get();

    Supplier<Void> versionApiCall = () -> {
      KubernetesApiCall.call(apiClient, apiCallSupplier);
      return null;
    };

    assertThatThrownBy(versionApiCall::get).matches(throwable -> {
      ExplanationException apiException = (ExplanationException) throwable;
      assertThat(apiException.getMessage())
          .isEqualTo(String.format("Connection failed on HTTP API call %s %s with message: %s", REQUEST_METHOD,
              REQUEST_URL, EXCEPTION_MESSAGE));
      return true;
    });
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sApiCallWithApiFailure() throws ApiException {
    doReturn(requestWithUrl).when(call).request();
    doThrow(new ApiException(RESPONSE_CODE, EXCEPTION_MESSAGE))
        .when(apiClient)
        .execute(any(Call.class), any(Type.class));
    KubernetesApiCall.ApiCallSupplier apiCallSupplier = mock(KubernetesApiCall.ApiCallSupplier.class);
    doReturn(call).when(apiCallSupplier).get();

    Supplier<Void> versionApiCall = () -> {
      KubernetesApiCall.call(apiClient, apiCallSupplier);
      return null;
    };

    assertThatThrownBy(versionApiCall::get).matches(throwable -> {
      ExplanationException apiException = (ExplanationException) throwable;
      assertThat(apiException.getMessage())
          .contains(String.format(
              "HTTP API call %s %s failed with error code %d", REQUEST_METHOD, REQUEST_URL, RESPONSE_CODE));
      return true;
    });
  }
}
