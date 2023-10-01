/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.exception;

import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;

import io.harness.annotations.ExposeInternalException;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ErrorCodeName;
import io.harness.eraro.Level;
import io.harness.eraro.MessageManager;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionUtils;
import io.harness.rest.RestResponse;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class GenericExceptionMapper.
 *
 * @param <T> the generic type
 */
@Slf4j
public class GenericExceptionMapper<T> implements ExceptionMapper<Throwable> {
  @Context private ResourceInfo resourceInfo;
  @Override
  public Response toResponse(Throwable exception) {
    log.error("Exception occurred: " + ExceptionUtils.getMessage(exception), exception);
    if (hasExposeExceptionAnnotation()) {
      RestResponse<T> restResponse = new RestResponse<>();
      restResponse.getResponseMessages().add(ResponseMessage.builder()
                                                 .code(ErrorCode.DEFAULT_ERROR_CODE)
                                                 .level(Level.ERROR)
                                                 .exception(exposeStackTrace() ? exception : null)
                                                 .message(exception.toString())
                                                 .build());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(restResponse)
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
    if (exception instanceof ClientErrorException) {
      return getHttpErrorResponse((ClientErrorException) exception);
    } else if (exception instanceof WebApplicationException && causeIs(AccountMigratedException.class, exception)) {
      return getHttpErrorResponse((WebApplicationException) exception);
    } else {
      return getDefaultResponse();
    }
  }

  private <T> boolean causeIs(Class<T> clazz, Throwable ex) {
    return ex.getCause() != null && clazz.isInstance(ex.getCause());
  }

  private boolean hasExposeExceptionAnnotation() {
    if (resourceInfo == null || resourceInfo.getResourceClass() == null) { // can be null in case of 404
      return false;
    }
    return resourceInfo.getResourceClass().isAnnotationPresent(ExposeInternalException.class)
        || resourceInfo.getResourceMethod().isAnnotationPresent(ExposeInternalException.class);
  }

  private boolean exposeStackTrace() {
    if (resourceInfo.getResourceClass().getAnnotation(ExposeInternalException.class) != null) {
      return resourceInfo.getResourceClass().getAnnotation(ExposeInternalException.class).withStackTrace();
    } else if (resourceInfo.getResourceMethod().getAnnotation(ExposeInternalException.class) != null) {
      return resourceInfo.getResourceMethod().getAnnotation(ExposeInternalException.class).withStackTrace();
    }
    throw new IllegalStateException("Check if it has ExposeInternalException annotation.");
  }

  private Response getHttpErrorResponse(WebApplicationException exception) {
    return Response.status(exception.getResponse().getStatus())
        .entity(new RestResponse<>())
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  private Response getDefaultResponse() {
    RestResponse<T> restResponse = new RestResponse<>();

    restResponse.getResponseMessages().add(
        ResponseMessage.builder()
            .code(ErrorCode.DEFAULT_ERROR_CODE)
            .level(Level.ERROR)
            .message(MessageManager.getInstance().prepareMessage(
                ErrorCodeName.builder().value(DEFAULT_ERROR_CODE.name()).build(), null, null))
            .build());

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
        .entity(restResponse)
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
