/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.instrumentation;

import static software.wings.security.AuthenticationFilter.API_KEY_HEADER;

import static com.google.common.base.Strings.nullToEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.network.Localhost;
import io.harness.serializer.JsonUtils;

import software.wings.audit.ApiKeyAuditDetails;
import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.Builder;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.HttpMethod;
import software.wings.common.AuditHelper;
import software.wings.graphql.utils.GraphQLConstants;
import software.wings.service.intfc.ApiKeyService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLAuditInstrumentation extends SimpleInstrumentation {
  @Inject private AuditHelper auditHelper;
  @Inject private ApiKeyService apiKeyService;

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters) {
    try {
      if (shouldAudit(parameters)) {
        createAuditHeaderFromHttpRequest(parameters);
      }
      return SimpleInstrumentationContext.whenCompleted(this::handleRequestCompletion);
    } catch (Exception e) {
      throw new UnexpectedException("Error while creating audit information", e);
    }
  }

  private AuditHeader createAuditHeaderFromHttpRequest(InstrumentationExecuteOperationParameters parameters) {
    final GraphQLContext graphQLContext = (GraphQLContext) parameters.getExecutionContext().getContext();
    final String accountId = graphQLContext.get("accountId");
    final HttpServletRequest httpServletRequest =
        (HttpServletRequest) (graphQLContext.getOrEmpty(GraphQLConstants.HTTP_SERVLET_REQUEST)
                                  .orElseThrow(
                                      () -> new InvalidRequestException("httpservlet request object missing")));
    final Builder auditHeaderBuilder =
        populateAuditHeaderDetails(httpServletRequest, Builder.anAuditHeader(), accountId);
    return auditHelper.create(auditHeaderBuilder.build());
  }

  private Builder populateAuditHeaderDetails(
      HttpServletRequest httpServletRequest, Builder auditHeaderBuilder, String accountId) {
    return auditHeaderBuilder.withUrl(httpServletRequest.getRequestURL().toString())
        .withHeaderString(getHeaderString(httpServletRequest, accountId))
        .withQueryParams(getQueryParams(httpServletRequest))
        .withRequestMethod(HttpMethod.POST)
        .withResourcePath(httpServletRequest.getPathInfo())
        .withRequestTime(System.currentTimeMillis())
        .withRemoteHostName(httpServletRequest.getRemoteHost())
        .withRemoteIpAddress(httpServletRequest.getRemoteAddr())
        .withLocalHostName(Localhost.getLocalHostName())
        .withLocalIpAddress(Localhost.getLocalHostAddress())
        .withApiKeyAuditDetails(getApiKeyAuditDetails(httpServletRequest.getHeader(API_KEY_HEADER), accountId));
  }

  private String getQueryParams(HttpServletRequest httpServletRequest) {
    return httpServletRequest.getQueryString();
  }

  @VisibleForTesting
  boolean shouldAudit(InstrumentationExecuteOperationParameters parameters) {
    final OperationDefinition operationDefinition = parameters.getExecutionContext().getOperationDefinition();
    if (operationDefinition != null) {
      return operationDefinition.getOperation() == Operation.MUTATION;
    }
    return false;
  }

  @VisibleForTesting
  void handleRequestCompletion(ExecutionResult executionResult, Throwable throwable) {
    try {
      final AuditHeader header = auditHelper.get();
      if (header != null) {
        String resultStr = "";
        int statusCode = 200;
        if (executionResult != null) {
          resultStr = JsonUtils.asJson(executionResult.toSpecification());
          if (EmptyPredicate.isNotEmpty(executionResult.getErrors())) {
            statusCode = 500;
          }
        } else if (throwable != null) {
          resultStr = "Error message: " + throwable.getMessage();
          statusCode = 500;
        }
        header.setResponseTime(System.currentTimeMillis());
        header.setResponseStatusCode(statusCode);
        auditHelper.finalizeAudit(header, nullToEmpty(resultStr).getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      log.error("error while finalizing audit header", e);
    }
  }

  private String getHeaderString(HttpServletRequest httpServletRequest, String accountId) {
    final Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
    final ArrayList<String> queryStringList = new ArrayList<>();
    if (headerNames != null) {
      while (headerNames.hasMoreElements()) {
        final String headerName = headerNames.nextElement();
        if (!shouldFilterHeader(headerName)) {
          queryStringList.add(headerToString(headerName, httpServletRequest.getHeader(headerName), accountId));
        }
      }
    }
    return String.join(",", queryStringList);
  }

  private String headerToString(final String headerName, final String headerValue, final String accountId) {
    String finalHeaderName = headerName;
    String finalHeaderValue = headerValue;
    if ("Authorization".equalsIgnoreCase(headerName)) {
      finalHeaderValue = "********";
    }
    return finalHeaderName + "=" + finalHeaderValue;
  }

  private ApiKeyAuditDetails getApiKeyAuditDetails(final String headerValue, final String accountId) {
    if (EmptyPredicate.isEmpty(headerValue)) {
      return null;
    }
    final ApiKeyEntry apiKeyEntry = apiKeyService.getByKey(headerValue, accountId);
    if (apiKeyEntry != null) {
      return ApiKeyAuditDetails.builder().apiKeyId(apiKeyEntry.getUuid()).apiKeyName(apiKeyEntry.getName()).build();
    }
    return null;
  }

  private boolean shouldFilterHeader(final String headerName) {
    return API_KEY_HEADER.equalsIgnoreCase(headerName);
  }
}
