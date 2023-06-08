/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.ngmanager;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;
import io.harness.security.ServiceTokenGenerator;

import java.io.IOException;
import java.util.Collections;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import okhttp3.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class NgManagerProxyApiImplTest extends CategoryTest {
  private static final String TEST_HEADER_KEY = "testHeaderKey";
  private static final String TEST_HEADER_VALUE = "testHeaderValue";
  private static final String TEST_QUERY_PARAM_KEY = "testQueryParamKey";
  private static final String TEST_QUERY_PARAM_VALUE = "testQueryParamValue";
  private final String TEST_ACCOUNT = "TEST_ACCOUNT";
  private final String BASE_URL = "http://example.com/";
  private final String PATH1 = "v1/idp-proxy/ng-manager/user/aggregate";
  AutoCloseable openMocks;
  @Mock private OkHttpClient client;
  @Mock private UriInfo uriInfo;
  @Mock private HttpHeaders headers;
  @Mock private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @Mock private ServiceTokenGenerator tokenGenerator;
  @Mock private Call call;
  private NgManagerProxyApiImpl ngManagerProxyApiImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    String ngManagerServiceSecret = "TEST_SECRET";
    ngManagerProxyApiImpl = Mockito.spy(
        new NgManagerProxyApiImpl(ngManagerServiceHttpClientConfig, ngManagerServiceSecret, tokenGenerator));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteProxyNgManager() throws IOException {
    MultivaluedMap<String, String> headersMap = new MultivaluedHashMap<>();
    headersMap.put(TEST_HEADER_KEY, Collections.singletonList(TEST_HEADER_VALUE));
    MultivaluedMap<String, String> queryParamsMap = new MultivaluedHashMap<>();
    queryParamsMap.put(TEST_QUERY_PARAM_KEY, Collections.singletonList(TEST_QUERY_PARAM_VALUE));
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("success")
            .request(request)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "Response from NG Manager"))
            .build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(queryParamsMap);
    when(headers.getRequestHeaders()).thenReturn(headersMap);
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.deleteProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT);

    assertEquals(javax.ws.rs.core.Response.Status.OK.getStatusCode(), result.getStatus());
    assertEquals("Response from NG Manager", result.getEntity().toString());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteProxyNgManagerError() throws IOException {
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder().code(200).message("success").request(request).protocol(Protocol.HTTP_2).build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenThrow(new RuntimeException());
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.deleteProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT);

    assertEquals(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), result.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetProxyNgManager() throws IOException {
    MultivaluedMap<String, String> headersMap = new MultivaluedHashMap<>();
    headersMap.put(TEST_HEADER_KEY, Collections.singletonList(TEST_HEADER_VALUE));
    MultivaluedMap<String, String> queryParamsMap = new MultivaluedHashMap<>();
    queryParamsMap.put(TEST_QUERY_PARAM_KEY, Collections.singletonList(TEST_QUERY_PARAM_VALUE));
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("success")
            .request(request)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "Response from NG Manager"))
            .build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(queryParamsMap);
    when(headers.getRequestHeaders()).thenReturn(headersMap);
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.getProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT);

    assertEquals(javax.ws.rs.core.Response.Status.OK.getStatusCode(), result.getStatus());
    assertEquals("Response from NG Manager", result.getEntity().toString());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetProxyNgManagerForFilteredPath() throws IOException {
    MultivaluedMap<String, String> headersMap = new MultivaluedHashMap<>();
    headersMap.put(TEST_HEADER_KEY, Collections.singletonList(TEST_HEADER_VALUE));
    MultivaluedMap<String, String> queryParamsMap = new MultivaluedHashMap<>();
    queryParamsMap.put(TEST_QUERY_PARAM_KEY, Collections.singletonList(TEST_QUERY_PARAM_VALUE));
    String pathToBeFiltered = "v1/idp-proxy/ng-manager/secrets";
    Request request = new Request.Builder().url(BASE_URL + pathToBeFiltered).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("success")
            .request(request)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "Response from NG Manager"))
            .build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(pathToBeFiltered);
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(responseSuccess);

    ngManagerProxyApiImpl.getProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetProxyNgManagerError() throws IOException {
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder().code(200).message("success").request(request).protocol(Protocol.HTTP_2).build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenThrow(new RuntimeException());
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.getProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT);

    assertEquals(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), result.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPostProxyNgManager() throws IOException {
    MultivaluedMap<String, String> headersMap = new MultivaluedHashMap<>();
    headersMap.put(TEST_HEADER_KEY, Collections.singletonList(TEST_HEADER_VALUE));
    MultivaluedMap<String, String> queryParamsMap = new MultivaluedHashMap<>();
    queryParamsMap.put(TEST_QUERY_PARAM_KEY, Collections.singletonList(TEST_QUERY_PARAM_VALUE));
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("success")
            .request(request)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "Response from NG Manager"))
            .build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(queryParamsMap);
    when(headers.getRequestHeaders()).thenReturn(headersMap);
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.postProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT, "");

    assertEquals(javax.ws.rs.core.Response.Status.OK.getStatusCode(), result.getStatus());
    assertEquals("Response from NG Manager", result.getEntity().toString());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPostProxyNgManagerError() throws IOException {
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder().code(200).message("success").request(request).protocol(Protocol.HTTP_2).build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenThrow(new RuntimeException());
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.postProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT, "");

    assertEquals(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), result.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPutProxyNgManager() throws IOException {
    MultivaluedMap<String, String> headersMap = new MultivaluedHashMap<>();
    headersMap.put(TEST_HEADER_KEY, Collections.singletonList(TEST_HEADER_VALUE));
    MultivaluedMap<String, String> queryParamsMap = new MultivaluedHashMap<>();
    queryParamsMap.put(TEST_QUERY_PARAM_KEY, Collections.singletonList(TEST_QUERY_PARAM_VALUE));
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("success")
            .request(request)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "Response from NG Manager"))
            .build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(queryParamsMap);
    when(headers.getRequestHeaders()).thenReturn(headersMap);
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.putProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT, "");

    assertEquals(javax.ws.rs.core.Response.Status.OK.getStatusCode(), result.getStatus());
    assertEquals("Response from NG Manager", result.getEntity().toString());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPutProxyNgManagerError() throws IOException {
    Request request = new Request.Builder().url(BASE_URL + PATH1).method("GET", null).build();
    Response responseSuccess =
        new Response.Builder().code(200).message("success").request(request).protocol(Protocol.HTTP_2).build();

    when(ngManagerServiceHttpClientConfig.getBaseUrl()).thenReturn(BASE_URL);
    when(uriInfo.getPath()).thenReturn(PATH1);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    when(ngManagerProxyApiImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenThrow(new RuntimeException());
    when(call.execute()).thenReturn(responseSuccess);

    javax.ws.rs.core.Response result =
        ngManagerProxyApiImpl.putProxyNgManager(uriInfo, headers, BASE_URL + PATH1, TEST_ACCOUNT, "");

    assertEquals(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), result.getStatus());
  }
}
