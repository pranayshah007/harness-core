/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.proxy.delegate.DelegateProxyRequestForwarder;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DelegateProxyRequestForwarderTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String REQUEST_URL =
      "https://api.github.com/repos/harness/harness-core/contents/idp-service/.sample-catalog-entities/three.yaml";
  private static final String REQUEST_BODY =
      "{\"url\":\"https://github.com/harness/harness-core/blob/develop/idp-service/.sample-catalog-entities/three.yaml\",\"method\":\"GET\",\"headers\":{\"Accept\":\"application/vnd.github.v3.raw\",\"User-Agent\":\"node-fetch/1.0 (+https://github.com/bitinn/node-fetch)\",\"Accept-Encoding\":\"gzip,deflate\",\"Connection\":\"close\"}}";
  private static final String REQUEST_METHOD = "GET";
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @InjectMocks DelegateProxyRequestForwarder delegateProxyRequestForwarder;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateHeaderConfig() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("Authorization", "Bearer token123");

    List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.createHeaderConfig(headers);

    assertEquals(3, headerList.size());
    assertEquals("Authorization", headerList.get(0).getKey());
    assertEquals("Bearer token123", headerList.get(0).getValue());
    assertEquals("Accept", headerList.get(1).getKey());
    assertEquals("application/json", headerList.get(1).getValue());
    assertEquals("Content-Type", headerList.get(2).getKey());
    assertEquals("application/json", headerList.get(2).getValue());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateHeaderConfigWithInvalidHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Length", "100");
    headers.put("Host", "example.com");
    headers.put("Connection", "keep-alive");

    List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.createHeaderConfig(headers);

    assertEquals(0, headerList.size());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegate() {
    String testResponse = "Test Response";
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody(testResponse).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);
    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(
        ACCOUNT_IDENTIFIER, REQUEST_URL, new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>());

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateWithError() {
    String testResponse = "Could not get response";
    DelegateResponseData expectedResponse = ErrorNotifyResponseData.builder().errorMessage(testResponse).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);
    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(
        ACCOUNT_IDENTIFIER, REQUEST_URL, new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>());

    assertNull(actualResponse);
  }

  @Test(expected = Exception.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateWhenException() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenThrow(new RuntimeException());
    delegateProxyRequestForwarder.forwardRequestToDelegate(
        ACCOUNT_IDENTIFIER, REQUEST_URL, new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>());
  }
}
