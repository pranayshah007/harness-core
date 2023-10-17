/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.filter;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.utils.JerseyFilterUtils.isDelegateRequest;
import static software.wings.utils.JerseyFilterUtils.isNextGenManagerRequest;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import software.wings.exception.AccountMigratedException;
import software.wings.security.AuthenticationFilter;
import software.wings.service.intfc.ApiKeyService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(HarnessTeam.SPG)
@Provider
@Priority(DisableFirstGenFilter.FILTER_PRIORITY)
@Singleton
@Slf4j
public class DisableFirstGenFilter implements ContainerRequestFilter {
  // DISABLED FILTER IS APPLIED AFTER AUTHENTICATION AND AUTHORIZATION FILTERS
  public static final int FILTER_PRIORITY = Priorities.AUTHORIZATION + 100;

  @Inject private FeatureFlagService ffService;
  @Inject private ApiKeyService apiKeyService;

  @Context private ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    // WE CANNOT FILTER WITHOUT AN ACCOUNT IDENTIFIER
    String accountId = getAccountIdFromRequest(requestContext);
    if (isEmpty(accountId)) {
      return;
    }

    // SKIP EVALUATION FOR SOME RESOURCES
    if (isDelegateRequest(requestContext, resourceInfo) || isNextGenManagerRequest(resourceInfo)) {
      return;
    }

    if (ffService.isEnabled(FeatureName.CDS_DISABLE_FIRST_GEN_CD, accountId)) {
      Exception cause = new AccountMigratedException(accountId);
      throw new WebApplicationException(cause, Response.Status.MOVED_PERMANENTLY);
    }
  }

  private String getAccountIdFromRequest(ContainerRequestContext requestContext) {
    MultivaluedMap<String, String> pathParameters = requestContext.getUriInfo().getPathParameters();
    MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();

    String accountId = getRequestParamFromContext("accountId", pathParameters, queryParameters);
    if (isEmpty(accountId)) {
      accountId = getRequestParamFromContext("routingId", pathParameters, queryParameters);
      if (isEmpty(accountId)) {
        accountId =
            apiKeyService.getAccountIdFromApiKey(requestContext.getHeaderString(AuthenticationFilter.API_KEY_HEADER));
      }
    }

    return accountId;
  }

  private String getRequestParamFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key) != null ? queryParameters.getFirst(key) : pathParameters.getFirst(key);
  }
}
