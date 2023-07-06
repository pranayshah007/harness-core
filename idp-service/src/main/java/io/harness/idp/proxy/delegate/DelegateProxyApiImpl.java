/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.idp.proxy.ngmanager.IdpAuthInterceptor.AUTHORIZATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.proxy.delegate.beans.BackstageProxyRequest;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.NextGenManagerAuth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

@OwnedBy(IDP)
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DelegateProxyApiImpl implements DelegateProxyApi {
  private static final String HEADER_STRING_PATTERN = "%s:%s; ";
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

      CloseableHttpClient httpClient = HttpClients.createDefault();
      HttpGet httpGet = new HttpGet(backstageProxyRequest.getUrl());

      backstageProxyRequest.getHeaders().forEach((key, value) -> {
        if (!key.equals(AUTHORIZATION)) {
          headerString.append(String.format(HEADER_STRING_PATTERN, key, value));
        } else {
          log.debug("Skipped logging {} header", AUTHORIZATION);
        }
        httpGet.setHeader(key, value);
      });
      log.info("Parsed request body headers: {}", headerString);

      HttpResponse httpResponse;
      ByteArrayOutputStream outStream;
      try {
        httpResponse = httpClient.execute(httpGet);
        Response.ResponseBuilder responseBuilder = Response.status(httpResponse.getStatusLine().getStatusCode());
        Header contentTypeHeader = httpResponse.getFirstHeader("Content-Type");
        if (contentTypeHeader != null && contentTypeHeader.getValue().equals("application/x-gzip")) {
          InputStream bodyStream = new GZIPInputStream(httpResponse.getEntity().getContent());
          outStream = new ByteArrayOutputStream();
          byte[] buffer = new byte[4096];
          int length;
          while ((length = bodyStream.read(buffer)) > 0) {
            outStream.write(buffer, 0, length);
          }
          responseBuilder.entity(outStream.toByteArray());
          //      for (Header header : httpResponse.getAllHeaders()) {
          //        responseBuilder.header(header.getName(), header.getValue());
          //      }
          responseBuilder.header("Content-Type", "application/x-gzip");
        } else {
          responseBuilder.entity(EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8));
        }
        return responseBuilder.build();
      } catch (ClientProtocolException e) {
        throw new RuntimeException(e);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
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
