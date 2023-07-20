/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.NGCommonEntityConstants.APPLICATION_GZIP_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_OCTET_STREAM_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.CONTENT_TYPE_HEADER;
import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.idp.proxy.services.IdpAuthInterceptor.AUTHORIZATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.proxy.delegate.beans.BackstageProxyRequest;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.NextGenManagerAuth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(IDP)
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DelegateProxyApiImpl implements DelegateProxyApi {
  private static final String HEADER_STRING_PATTERN = "%s:%s; ";
  private static final String HEADER_SEPARATOR = ";";
  private static final String KEY_VALUE_SEPARATOR = ":";

  private final DelegateProxyRequestForwarder delegateProxyRequestForwarder;
  private final DelegateSelectorsCache delegateSelectorsCache;

  @IdpServiceAuthIfHasApiKey
  @Override
  @POST
  public Response forwardProxy(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers,
      @PathParam("url") String urlString, String body) throws JsonProcessingException {
    var accountIdentifier = headers.getHeaderString("Harness-Account");
    try (AutoLogContext ignore1 =
             new AccountLogContext(accountIdentifier, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      BackstageProxyRequest backstageProxyRequest;
      try {
        ObjectMapper mapper = new ObjectMapper();
        backstageProxyRequest = mapper.readValue(body, BackstageProxyRequest.class);
      } catch (Exception err) {
        log.info("Error parsing backstageProxyRequest. Request: {}", body, err);
        throw err;
      }
      log.info("Parsed request body url: {}", backstageProxyRequest.getUrl());
      log.info("Parsed request body method: {}", backstageProxyRequest.getMethod());
      StringBuilder headerString = new StringBuilder();
      backstageProxyRequest.getHeaders().forEach((key, value) -> {
        if (!key.equals(AUTHORIZATION)) {
          headerString.append(String.format(HEADER_STRING_PATTERN, key, value));
        } else {
          log.debug("Skipped logging {} header", AUTHORIZATION);
        }
      });
      log.info("Parsed request body headers: {}", headerString);

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

      Response.ResponseBuilder responseBuilder = Response.status(httpResponse.getHttpResponseCode());

      String responseHeaders = httpResponse.getHeader();
      boolean isByteResponse = false;
      if (responseHeaders != null) {
        for (String header : responseHeaders.split(HEADER_SEPARATOR)) {
          String[] nameValue = header.split(KEY_VALUE_SEPARATOR);
          String name = nameValue[0].trim();
          String value = nameValue[1].trim();
          if (name.equals(CONTENT_TYPE_HEADER)
              && (value.equals(APPLICATION_GZIP_MEDIA_TYPE) || value.equals(APPLICATION_OCTET_STREAM_MEDIA_TYPE))) {
            isByteResponse = true;
          }
          responseBuilder.header(name, value);
        }
      }

      if (isByteResponse) {
        responseBuilder.entity(httpResponse.getHttpResponseBodyInBytes());
      } else {
        responseBuilder.entity(httpResponse.getHttpResponseBody());
      }

      return responseBuilder.build();
    }
  }

  private Set<String> getDelegateSelectors(String urlString, String accountIdentifier) {
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
