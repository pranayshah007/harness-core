package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.NGRestUtils;
import io.harness.scope.remote.ScopeInfoClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
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
  private final ScopeInfo scopeInfo;
  private final ScopeInfoClient scopeInfoClient;
  @Context private ResourceInfo resourceInfo;
//  private final Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping;
//  private final Map<String, String> serviceToSecretMapping;

//  @Inject
  public ScopeInfoFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
                         Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping, ScopeInfo scopeInfo, @Named("PRIVILEGED") ScopeInfoClient scopeInfoClient) {
    super(predicate, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
//  this.serviceToJWTTokenHandlerMapping = serviceToJWTTokenHandlerMapping;
//  this.serviceToSecretMapping = serviceToSecretMapping;
    this.scopeInfo = scopeInfo;
    this.scopeInfoClient = scopeInfoClient;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (scopeInfo == null || getAccountIdentifierFrom(requestContext).isEmpty()
        || getAccountIdentifierFrom(requestContext).get().isEmpty()) {
      return;
    }
    if (scopeInfo.getUniqueId() != null && scopeInfo.getScopeType() != null) {
      return;
    }
    if (isInternalRequest(resourceInfo)) {
      super.filter(requestContext/*, serviceToJWTTokenHandlerMapping, serviceToSecretMapping*/);
    } else {
      Optional<String> accountIdentifierOptional = getAccountIdentifierFrom(requestContext);
      if (accountIdentifierOptional.isEmpty()) {
        throw new InvalidRequestException("Account detail is not present in the request");
      }
      String accountIdentifier = accountIdentifierOptional.get();
      Optional<ScopeInfo> optionalScopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier,
          getOrgIdentifierFrom(requestContext).isPresent() ? getOrgIdentifierFrom(requestContext).get() : null,
          getProjectIdentifierFrom(requestContext).isPresent() ? getProjectIdentifierFrom(requestContext).get() : null));
      scopeInfo.setAccountIdentifier(getAccountIdentifierFrom(requestContext).get());
      scopeInfo.setOrgIdentifier(
          getOrgIdentifierFrom(requestContext).isPresent() ? getOrgIdentifierFrom(requestContext).get() : null);
      scopeInfo.setProjectIdentifier(
          getProjectIdentifierFrom(requestContext).isPresent() ? getProjectIdentifierFrom(requestContext).get() : null);
      scopeInfo.setScopeType(optionalScopeInfo.get().getScopeType());
      scopeInfo.setUniqueId(optionalScopeInfo.get().getUniqueId());
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
