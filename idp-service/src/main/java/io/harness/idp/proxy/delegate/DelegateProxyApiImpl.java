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
import io.harness.eraro.ResponseMessage;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.proxy.delegate.beans.BackstageProxyRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(IDP)
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DelegateProxyApiImpl implements DelegateProxyApi {
  DelegateProxyRequestForwarder delegateProxyRequestForwarder;

  @POST
  public Response forwardProxy(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers,
      @PathParam("url") String url, String body) throws JsonProcessingException {
    var accountId = headers.getHeaderString("accountId");
    BackstageProxyRequest backstageProxyRequest;
    try {
      ObjectMapper mapper = new ObjectMapper();
      backstageProxyRequest = mapper.readValue(body, BackstageProxyRequest.class);
    } catch (Exception err) {
      log.info("Error parsing backstageProxyRequest ", err);
      throw err;
    }
    log.info("Parsed request body: {}", backstageProxyRequest);

    List<HttpHeaderConfig> headerList =
        delegateProxyRequestForwarder.createHeaderConfig(backstageProxyRequest.getHeaders());

    HttpStepResponse httpResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(accountId,
        backstageProxyRequest.getUrl(), headerList, backstageProxyRequest.getBody(), backstageProxyRequest.getMethod());

    if (httpResponse == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Did not receive response from Delegate").build())
          .build();
    }

    return Response.status(httpResponse.getHttpResponseCode()).entity(httpResponse.getHttpResponseBody()).build();
  }
}
