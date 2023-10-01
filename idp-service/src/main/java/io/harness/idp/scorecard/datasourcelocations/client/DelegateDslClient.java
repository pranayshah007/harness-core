/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.UnexpectedException;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.proxy.delegate.DelegateProxyRequestForwarder;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;

import com.google.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class DelegateDslClient implements DslClient {
  DelegateSelectorsCache delegateSelectorsCache;
  DelegateProxyRequestForwarder delegateProxyRequestForwarder;

  @Override
  public Response call(String accountIdentifier, ApiRequestDetails apiRequestDetails) {
    URL urlObj;
    String url = apiRequestDetails.getUrl();
    try {
      urlObj = new URL(url);
    } catch (MalformedURLException e) {
      throw new UnexpectedException("Error parsing the url while fetching the delegate selectors", e);
    }
    String host = urlObj.getHost();
    Set<String> delegateSelectors = delegateSelectorsCache.get(accountIdentifier, host);

    List<HttpHeaderConfig> headerList = new ArrayList<>();
    apiRequestDetails.getHeaders().forEach(
        (k, v) -> headerList.add(HttpHeaderConfig.builder().key(k).value(v).build()));

    HttpStepResponse httpResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(accountIdentifier, url,
        headerList, apiRequestDetails.getRequestBody(), apiRequestDetails.getMethod(), delegateSelectors);

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
}
