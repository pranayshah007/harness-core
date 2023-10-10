/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ScopeInfoFilter implements ContainerRequestFilter {
  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    UriInfo uriInfo = containerRequestContext.getUriInfo();
    final MultivaluedMap<String, String> pathParameters = uriInfo.getPathParameters();
    final MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
    Optional<String> accountIdentifierOptional = getAccountIdentifierFrom(containerRequestContext);
    if (accountIdentifierOptional.isEmpty()) {
      throw new InvalidRequestException("Account detail is not present in the request");
    }
    String accountIdentifier = accountIdentifierOptional.get();
    String orgIdentifier = queryParameters.getFirst("orgIdentifier");
    String projIdentifier = queryParameters.getFirst("projectIdentifier");
    containerRequestContext.setProperty("scopeInfo",
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projIdentifier)
            .scopeType(ScopeLevel.of(accountIdentifier, orgIdentifier, projIdentifier)) // resolve scope
            .uniqueId("uniqueId")
            .build());
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
}
