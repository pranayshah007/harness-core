/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.NGRestUtils;
import io.harness.scope.remote.ScopeInfoClient;

import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import io.harness.security.annotations.InternalApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.wings.security.JWT_CATEGORY;

@OwnedBy(PL)
@Singleton
@Priority(6000)
@Slf4j
@Provider
public class ScopeInfoFilter extends JWTAuthenticationFilter /*implements ContainerRequestFilter*/ {
  private final ScopeInfoClient scopeInfoClient;
  @Context private ResourceInfo resourceInfo;

  public ScopeInfoFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
                         Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping, @Named("PRIVILEGED") ScopeInfoClient scopeInfoClient) {
    super(predicate, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
    this.scopeInfoClient = scopeInfoClient;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    Object requestCtxObject = requestContext.getProperty("scopeInfo");
    if (requestContext instanceof ScopeInfo) {
      ScopeInfo scopeInfo = (ScopeInfo) requestCtxObject;

      if (requestContext.getProperty("scopeInfo") == null || getAccountIdentifierFrom(requestContext).isEmpty()
          || getAccountIdentifierFrom(requestContext).get().isEmpty()) {
        return;
      }
      if (scopeInfo.getUniqueId() != null && scopeInfo.getScopeType() != null) {
        requestContext.setProperty("scopeInfo", scopeInfo);
        return;
      }
      if (isInternalRequest(resourceInfo)) {
        super.filter(requestContext);
      } else {
        Optional<String> accountIdentifierOptional = getAccountIdentifierFrom(requestContext);
        if (accountIdentifierOptional.isEmpty()) {
          throw new InvalidRequestException("Account detail is not present in the request");
        }
        String accountIdentifier = accountIdentifierOptional.get();
        String orgIdentifier = getOrgIdentifierFrom(requestContext).isPresent() ? getOrgIdentifierFrom(requestContext).get() : null;
        String projectIdentifier = getProjectIdentifierFrom(requestContext).isPresent() ? getProjectIdentifierFrom(requestContext).get() : null;
        Optional<ScopeInfo> optionalScopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier,
            orgIdentifier,
            projectIdentifier));
        scopeInfo.setAccountIdentifier(accountIdentifier);
        scopeInfo.setOrgIdentifier(orgIdentifier);
        scopeInfo.setProjectIdentifier(projectIdentifier);
        scopeInfo.setScopeType(optionalScopeInfo.get().getScopeType());
        scopeInfo.setUniqueId(optionalScopeInfo.get().getUniqueId());
        scopeInfo.setScopeType(ScopeLevel.ACCOUNT);
        requestContext.setProperty("scopeInfo", scopeInfo);
      }
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
