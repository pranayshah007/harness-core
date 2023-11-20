/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.idp.app.IdpServiceRequestInterceptor.REQUEST_START_TIME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.metrics.IdpServiceApiMetricsPublisher;

import com.google.inject.Inject;
import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Provider
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpServiceResponseInterceptor implements ContainerResponseFilter {
  public static final String HARNESS_ACCOUNT_HEADER = "Harness-Account";
  public static final String ACCOUNT_IDENTIFIER_PARAM = "accountIdentifier";
  @Inject IdpServiceApiMetricsPublisher idpServiceApiMetricsPublisher;

  @Override
  public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext)
      throws IOException {
    try {
      int status = containerResponseContext.getStatus();
      String accountIdentifier = getAccountIdentifier(containerRequestContext);
      String path = containerRequestContext.getUriInfo().getPath();
      String method = containerRequestContext.getMethod();
      long startTime = (long) containerRequestContext.getProperty(REQUEST_START_TIME);
      long duration = System.currentTimeMillis() - startTime;
      log.info("ACCOUNT {} - API REQUEST {} - METHOD {} - RESPONSE STATUS {}", accountIdentifier, path, method, status);
      if (StringUtils.isNotBlank(accountIdentifier)) {
        idpServiceApiMetricsPublisher.recordMetric(accountIdentifier, path, status, method, duration);
      }
    } catch (Exception e) {
      log.warn("Error intercepting response", e);
    }
  }

  private String getAccountIdentifier(ContainerRequestContext containerRequestContext) {
    String accountIdentifier = containerRequestContext.getHeaderString(HARNESS_ACCOUNT_HEADER);
    if (accountIdentifier != null) {
      return accountIdentifier;
    }

    MultivaluedMap<String, String> queryParams = containerRequestContext.getUriInfo().getQueryParameters();
    return queryParams.getFirst(ACCOUNT_IDENTIFIER_PARAM);
  }
}
