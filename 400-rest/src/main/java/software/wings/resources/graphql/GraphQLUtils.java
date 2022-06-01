/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.graphql;

import software.wings.graphql.utils.GraphQLConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import graphql.ExecutionResultImpl;
import graphql.GraphqlErrorBuilder;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * @author marklu on 9/27/19
 */
public class GraphQLUtils {
  @Inject private GraphQLRateLimiter graphQLRateLimiter;

  private static ObjectMapper objectMapper = new ObjectMapper();

  public void validateGraphQLCall(String accountId, boolean isInternalGraphQLCall) {
    if (graphQLRateLimiter.isOverApiRateLimit(accountId, isInternalGraphQLCall)) {
      throw new WebApplicationException(Response.status(429).entity(getRateLimitReachedErrorMessage()).build());
    }
  }

  WebApplicationException getInvalidApiKeyException() {
    return new WebApplicationException(
        Response.status(Status.UNAUTHORIZED).entity(getInvalidApiKeyErrorMessage()).build());
  }

  WebApplicationException getUnauthorizedException() {
    return new WebApplicationException(
        Response.status(Status.UNAUTHORIZED).entity(getUnauthorizedErrorMessage()).build());
  }

  WebApplicationException getUnauthorizedExceptionForSupportUserWithMutation() {
    return new WebApplicationException(
        Response.status(Status.UNAUTHORIZED).entity(getUnauthorizedErrorMessageForSupportUserWithMutation()).build());
  }

  WebApplicationException getInvalidTokenException() {
    return new WebApplicationException(
        Response.status(Status.UNAUTHORIZED).entity(getInvalidTokenErrorMessage()).build());
  }

  WebApplicationException getFeatureNotEnabledException() {
    return new WebApplicationException(
        Response.status(Status.BAD_REQUEST).entity(getFeatureNotEnabledErrorMessage()).build());
  }

  WebApplicationException getException(String message, Throwable rootCause) {
    WebApplicationException exception =
        new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(message).build());
    exception.initCause(rootCause);
    return exception;
  }

  private String getRateLimitReachedErrorMessage() {
    return getErrorMessage(GraphQLConstants.RATE_LIMIT_REACHED);
  }

  private String getInvalidApiKeyErrorMessage() {
    String message = GraphQLConstants.INVALID_API_KEY;
    return getErrorMessage(message);
  }

  private String getUnauthorizedErrorMessage() {
    String message = GraphQLConstants.NOT_AUTHORIZED;
    return getErrorMessage(message);
  }

  private String getUnauthorizedErrorMessageForSupportUserWithMutation() {
    String message = GraphQLConstants.NOT_AUTHORIZED_SUPPORT_USER_FOR_MUTATION;
    return getErrorMessage(message);
  }

  private String getInvalidTokenErrorMessage() {
    String message = GraphQLConstants.INVALID_TOKEN;
    return getErrorMessage(message);
  }

  private String getFeatureNotEnabledErrorMessage() {
    String message = GraphQLConstants.FEATURE_NOT_ENABLED;
    return getErrorMessage(message);
  }

  private String getErrorMessage(String rootMessage) {
    try {
      return objectMapper.writeValueAsString(ExecutionResultImpl.newExecutionResult()
                                                 .addError(GraphqlErrorBuilder.newError().message(rootMessage).build())
                                                 .build()
                                                 .toSpecification());
    } catch (JsonProcessingException e) {
      // Should never happen.
      return rootMessage;
    }
  }
}
