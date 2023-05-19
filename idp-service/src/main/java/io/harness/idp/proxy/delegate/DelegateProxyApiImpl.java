/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.gitintegration.utils.delegateselectors.DelegateSelectorsCache;
import io.harness.idp.proxy.delegate.beans.BackstageProxyRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DelegateProxyApiImpl implements DelegateProxyApi {
  private final DelegateProxyRequestForwarder delegateProxyRequestForwarder;
  private final DelegateSelectorsCache delegateSelectorsCache;

  @POST
  public Response forwardProxy(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers,
      @PathParam("url") String urlString, String body) throws JsonProcessingException, ExecutionException {
    var accountIdentifier = headers.getHeaderString("accountId");
    BackstageProxyRequest backstageProxyRequest;
    try {
      ObjectMapper mapper = new ObjectMapper();
      backstageProxyRequest = mapper.readValue(body, BackstageProxyRequest.class);
    } catch (Exception err) {
      log.info("Error parsing backstageProxyRequest. Request: {}", body, err);
      throw err;
    }
    log.info("Parsed request body: {}", backstageProxyRequest);

    Set<String> delegateSelectors = getDelegateSelectors(backstageProxyRequest.getUrl(), accountIdentifier);
    List<HttpHeaderConfig> headerList =
        delegateProxyRequestForwarder.createHeaderConfig(backstageProxyRequest.getHeaders());

    HttpStepResponse httpResponse =
        delegateProxyRequestForwarder.forwardRequestToDelegate(accountIdentifier, backstageProxyRequest.getUrl(),
            headerList, backstageProxyRequest.getBody(), backstageProxyRequest.getMethod(), delegateSelectors);

    if (httpResponse == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder()
                      .message("Did not receive response from Delegate")
                      .code(ErrorCode.INTERNAL_SERVER_ERROR)
                      .build())
          .build();
    }

    return Response.status(httpResponse.getHttpResponseCode()).entity(httpResponse.getHttpResponseBody()).build();
  }

  private Set<String> getDelegateSelectors(String urlString, String accountIdentifier) throws ExecutionException {
    URL url;
    try {
      url = new URL(urlString);
    } catch (MalformedURLException e) {
      throw new RuntimeException("Error parsing the url while fetching the delegate selectors", e);
    }
    String host = url.getHost();

    // Remove the api. prefix in api.github.com calls
    if (url.getHost().startsWith("api.")) {
      host = host.replace("api.", "");
    }

    return delegateSelectorsCache.get(accountIdentifier, host);
  }
}
