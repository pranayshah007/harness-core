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
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.idp.proxy.delegate.beans.BackstageProxyRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DelegateProxyApiImpl implements DelegateProxyApi {
  private final DelegateProxyRequestForwarder delegateProxyRequestForwarder;
  private final GitIntegrationService gitIntegrationService;

  @POST
  public Response forwardProxy(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers,
      @PathParam("url") String urlString, String body) throws JsonProcessingException {
    var accountIdentifier = headers.getHeaderString("accountId");
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
    String providerType = getProviderTypeFromUrl(urlString);
    Set<String> delegateSelectors = getDelegateSelectorForProvider(accountIdentifier, providerType);

    HttpStepResponse httpResponse =
        delegateProxyRequestForwarder.forwardRequestToDelegate(accountIdentifier, backstageProxyRequest.getUrl(),
            headerList, backstageProxyRequest.getBody(), backstageProxyRequest.getMethod(), delegateSelectors);

    if (httpResponse == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Did not receive response from Delegate").build())
          .build();
    }

    return Response.status(httpResponse.getHttpResponseCode()).entity(httpResponse.getHttpResponseBody()).build();
  }

  private Set<String> getDelegateSelectorForProvider(String accountIdentifier, String providerType) {
    Set<String> delegateSelectors = new HashSet<>();
    Optional<CatalogConnectorEntity> catalogConnectorOpt =
        gitIntegrationService.findByAccountIdAndProviderType(accountIdentifier, providerType);
    if (catalogConnectorOpt.isPresent()) {
      delegateSelectors = catalogConnectorOpt.get().getDelegateSelectors();
    }
    return delegateSelectors;
  }

  private String getProviderTypeFromUrl(String urlString) {
    // TODO: This logic assumes the url would have the respective integration name somewhere.
    //  This logic needs to be improved as there are chances that the url might not have the integration name
    //  and also especially when we start supporting multiple connectors per type.
    URL url;
    try {
      url = new URL(urlString);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    if (url.getHost().contains(GitIntegrationConstants.GITHUB_CONNECTOR_TYPE.toLowerCase())
        || url.getHost().contains(GitIntegrationConstants.GITHUB_ENTERPRISE_URL_PREFIX.toLowerCase())) {
      return GitIntegrationConstants.GITHUB_CONNECTOR_TYPE;
    } else if (url.getHost().contains(GitIntegrationConstants.GITLAB_CONNECTOR_TYPE.toLowerCase())) {
      return GitIntegrationConstants.GITLAB_CONNECTOR_TYPE;
    } else if (url.getHost().contains(GitIntegrationConstants.BITBUCKET_CONNECTOR_TYPE.toLowerCase())) {
      return GitIntegrationConstants.BITBUCKET_CONNECTOR_TYPE;
    } else if (url.getHost().contains(GitIntegrationConstants.AZURE_HOST.toLowerCase())) {
      return GitIntegrationConstants.AZURE_REPO_CONNECTOR_TYPE;
    } else {
      throw new UnsupportedOperationException(
          String.format("URL host %s does not match any provider type", url.getHost()));
    }
  }
}
