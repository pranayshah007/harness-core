/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.idp.proxy.ngmanager.IdpAuthInterceptor.AUTHORIZATION;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.gitintegration.utils.delegateselectors.DelegateSelectorsCache;
import io.harness.idp.proxy.delegate.DelegateProxyApiImpl;
import io.harness.idp.proxy.delegate.DelegateProxyRequestForwarder;
import io.harness.idp.proxy.delegate.beans.BackstageProxyRequest;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DelegateProxyApiImplTest extends CategoryTest {
  private static final String GITHUB_HOST = "github.com";
  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String DELEGATE_SELECTOR1 = "d1";
  private static final String DELEGATE_SELECTOR2 = "d2";
  private static final String REQUEST_URL =
      "https://api.github.com/repos/harness/harness-core/contents/idp-service/.sample-catalog-entities/three.yaml";
  private static final String REQUEST_BODY = "";
  private static final String REQUEST_METHOD = "GET";
  private static final String TEST_RESPONSE_BODY = "Response body";
  AutoCloseable openMocks;
  @Mock DelegateProxyRequestForwarder delegateProxyRequestForwarder;
  @Mock DelegateSelectorsCache delegateSelectorsCache;
  @Mock HttpHeaders httpHeaders;
  @Mock UriInfo uriInfo;
  @InjectMocks DelegateProxyApiImpl delegateProxyApi;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardProxyWithNoResponse() throws ExecutionException, JsonProcessingException {
    BackstageProxyRequest backstageProxyRequest = getProxyRequest();
    List<HttpHeaderConfig> headers = new ArrayList<>();
    Set<String> delegateSelectors = new HashSet<>();
    delegateSelectors.add(DELEGATE_SELECTOR1);
    delegateSelectors.add(DELEGATE_SELECTOR2);
    ObjectMapper mapper = new ObjectMapper();
    String backstageProxyRequestString = mapper.writeValueAsString(backstageProxyRequest);

    when(httpHeaders.getHeaderString("Harness-Account")).thenReturn(ACCOUNT_IDENTIFIER);
    when(delegateProxyRequestForwarder.createHeaderConfig(backstageProxyRequest.getHeaders())).thenReturn(headers);
    when(delegateSelectorsCache.get(ACCOUNT_IDENTIFIER, GITHUB_HOST)).thenReturn(delegateSelectors);

    Response actualResponse = delegateProxyApi.forwardProxy(uriInfo, httpHeaders, "", backstageProxyRequestString);

    assertEquals(500, actualResponse.getStatus());
    assertTrue(actualResponse.getEntity() instanceof ResponseMessage);
    assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, ((ResponseMessage) actualResponse.getEntity()).getCode());
    assertEquals("Did not receive response from Delegate", ((ResponseMessage) actualResponse.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardProxy() throws ExecutionException, JsonProcessingException {
    BackstageProxyRequest backstageProxyRequest = getProxyRequest();
    List<HttpHeaderConfig> headers = new ArrayList<>();
    HttpStepResponse expectedResponse = HttpStepResponse.builder()
                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                            .httpResponseBody(TEST_RESPONSE_BODY)
                                            .build();
    Set<String> delegateSelectors = new HashSet<>();
    delegateSelectors.add(DELEGATE_SELECTOR1);
    delegateSelectors.add(DELEGATE_SELECTOR2);
    ObjectMapper mapper = new ObjectMapper();
    String backstageProxyRequestString = mapper.writeValueAsString(backstageProxyRequest);

    when(httpHeaders.getHeaderString("Harness-Account")).thenReturn(ACCOUNT_IDENTIFIER);
    when(delegateProxyRequestForwarder.createHeaderConfig(backstageProxyRequest.getHeaders())).thenReturn(headers);
    when(delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER, backstageProxyRequest.getUrl(),
             headers, backstageProxyRequest.getBody(), backstageProxyRequest.getMethod(), delegateSelectors))
        .thenReturn(expectedResponse);
    when(delegateSelectorsCache.get(ACCOUNT_IDENTIFIER, GITHUB_HOST)).thenReturn(delegateSelectors);

    Response actualResponse = delegateProxyApi.forwardProxy(uriInfo, httpHeaders, "", backstageProxyRequestString);

    assertEquals(expectedResponse.getHttpResponseCode(), actualResponse.getStatus());
    assertEquals(expectedResponse.getHttpResponseBody(), actualResponse.getEntity());
  }

  @Test(expected = MismatchedInputException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardProxyWithInvalidBody() throws JsonProcessingException, ExecutionException {
    when(httpHeaders.getHeaderString("Harness-Account")).thenReturn(ACCOUNT_IDENTIFIER);
    delegateProxyApi.forwardProxy(uriInfo, httpHeaders, "", "");
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardProxyWithInvalidUrl() throws JsonProcessingException, ExecutionException {
    BackstageProxyRequest backstageProxyRequest = getProxyRequest();
    backstageProxyRequest.setUrl("Invalid Url");
    ObjectMapper mapper = new ObjectMapper();
    String backstageProxyRequestString = mapper.writeValueAsString(backstageProxyRequest);
    when(httpHeaders.getHeaderString("Harness-Account")).thenReturn(ACCOUNT_IDENTIFIER);
    delegateProxyApi.forwardProxy(uriInfo, httpHeaders, "", backstageProxyRequestString);
  }

  private BackstageProxyRequest getProxyRequest() {
    Map<String, String> headersMap = new HashMap<>();
    headersMap.put("Harness-Account", ACCOUNT_IDENTIFIER);
    headersMap.put(AUTHORIZATION, "testString");
    headersMap.put("Accept", "application/vnd.github.v3.raw");
    headersMap.put("Accept-Encoding", "gzip,deflate");
    BackstageProxyRequest backstageProxyRequest = new BackstageProxyRequest();
    backstageProxyRequest.setUrl(REQUEST_URL);
    backstageProxyRequest.setBody(REQUEST_BODY);
    backstageProxyRequest.setMethod(REQUEST_METHOD);
    backstageProxyRequest.setHeaders(headersMap);
    return backstageProxyRequest;
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
