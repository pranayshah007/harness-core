/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.annotations.InternalApi;

import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Priority(6000)
@Slf4j
@Provider
public class ScopeInfoFilter implements ContainerRequestFilter {
  private final ScopeInfoClient scopeInfoClient;
  @Context private ResourceInfo resourceInfo;

  public ScopeInfoFilter(@Named("PRIVILEGED") ScopeInfoClient scopeInfoClient) {
    this.scopeInfoClient = scopeInfoClient;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    Object requestCtxObject = requestContext.getProperty("scopeInfo");
    if (requestCtxObject instanceof ScopeInfo) {
      ScopeInfo scopeInfo = (ScopeInfo) requestCtxObject;
      if (scopeInfo.getUniqueId() != null && scopeInfo.getScopeType() != null) {
        requestContext.setProperty("scopeInfo", scopeInfo);
        return;
      }
    }

    Optional<String> accountIdentifierOptional = getAccountIdentifierFrom(requestContext);
    if (accountIdentifierOptional.isEmpty() || accountIdentifierOptional.get().isEmpty()) {
      return;
    }

    if (!isInternalRequest(resourceInfo)) {
      String accountIdentifier = accountIdentifierOptional.get();
      String orgIdentifier =
          getOrgIdentifierFrom(requestContext).isPresent() ? getOrgIdentifierFrom(requestContext).get() : null;
      String projectIdentifier =
          getProjectIdentifierFrom(requestContext).isPresent() ? getProjectIdentifierFrom(requestContext).get() : null;
      Optional<ScopeInfo> optionalScopeInfo =
          NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
      if (optionalScopeInfo.isEmpty()) {
        log.warn(format(
            "No Scope with given identifiers - accountIdentifier: [%s], orgIdentifier: [%s], projectIdentifier: [%s] present",
            accountIdentifier, orgIdentifier, projectIdentifier));
        return;
      }
      ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).build();
      scopeInfo.setOrgIdentifier(optionalScopeInfo.get().getOrgIdentifier());
      scopeInfo.setProjectIdentifier(optionalScopeInfo.get().getProjectIdentifier());
      scopeInfo.setScopeType(optionalScopeInfo.get().getScopeType());
      scopeInfo.setUniqueId(optionalScopeInfo.get().getUniqueId());
      requestContext.setProperty("scopeInfo", scopeInfo);
    }
  }

  private Optional<String> getAccountIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String accountIdentifier = containerRequestContext.getHeaderString(ACCOUNT_HEADER);

    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT);
    }
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    final String accountIdStringConstant = "accountId";
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier = containerRequestContext.getUriInfo().getQueryParameters().getFirst(accountIdStringConstant);
    }
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier = containerRequestContext.getUriInfo().getPathParameters().getFirst(accountIdStringConstant);
    }
    return StringUtils.isEmpty(accountIdentifier) ? Optional.empty() : Optional.of(accountIdentifier);
  }

  private Optional<String> getOrgIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String orgIdentifier =
        containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ORG_KEY);

    if (StringUtils.isEmpty(orgIdentifier)) {
      orgIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ORG_KEY);
    }
    return StringUtils.isEmpty(orgIdentifier) ? Optional.empty() : Optional.of(orgIdentifier);
  }

  private Optional<String> getProjectIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String projectIdentifier =
        containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.PROJECT_KEY);

    if (StringUtils.isEmpty(projectIdentifier)) {
      projectIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.PROJECT_KEY);
    }
    return StringUtils.isEmpty(projectIdentifier) ? Optional.empty() : Optional.of(projectIdentifier);
  }

  private boolean isInternalRequest(ResourceInfo requestResourceInfo) {
    return requestResourceInfo.getResourceMethod().getAnnotation(InternalApi.class) != null
        || requestResourceInfo.getResourceClass().getAnnotation(InternalApi.class) != null;
  }
}