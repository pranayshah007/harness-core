/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.exception.mapper;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.client.ServiceCallException;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;

import java.util.List;
import java.util.Objects;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

@OwnedBy(CV)
@Slf4j
public class ServiceCallExceptionMapper implements ExceptionMapper<ServiceCallException> {
  @Context private ResourceInfo resourceInfo;

  @Override
  public Response toResponse(ServiceCallException exception) {
    ExceptionLogger.logProcessedMessages(exception, MANAGER, log);

    List<ResponseMessage> responseMessages = exception.getResponseMessages();
    ErrorCode errorCode =
        Objects.nonNull(exception.getErrorCode()) ? exception.getErrorCode() : ErrorCode.UNKNOWN_ERROR;
    String errorMessage = StringUtils.isNotEmpty(ExceptionUtils.getMessage(exception))
        ? ExceptionUtils.getMessage(exception)
        : ExceptionUtils.getRootCauseMessage(exception);
    ErrorDTO errorBody = ErrorDTO.newError(Status.ERROR, errorCode, errorMessage);
    errorBody.setResponseMessages(responseMessages);
    return Response.status(resolveHttpStatus(errorCode)).entity(errorBody).build();
  }

  private Response.Status resolveHttpStatus(ErrorCode errorCode) {
    return Response.Status.fromStatusCode(errorCode.getStatus().getCode());
  }
}
