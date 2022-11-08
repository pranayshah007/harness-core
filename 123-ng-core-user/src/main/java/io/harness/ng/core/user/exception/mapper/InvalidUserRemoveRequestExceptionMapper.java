/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.exception.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.user.exception.InvalidUserRemoveRequestException;

import java.util.stream.Collectors;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class InvalidUserRemoveRequestExceptionMapper implements ExceptionMapper<InvalidUserRemoveRequestException> {
  @Context private ResourceInfo resourceInfo;

  @Override
  public Response toResponse(InvalidUserRemoveRequestException exception) {
    ExceptionLogger.logProcessedMessages(exception, MANAGER, log);

    ErrorCode errorCode = exception.getCode() != null ? exception.getCode() : ErrorCode.UNKNOWN_ERROR;
    ErrorDTO errorBody = ErrorDTO.newError(Status.ERROR, errorCode, null);
    errorBody.setResponseMessages(exception.getLastAdminScopes()
                                      .stream()
                                      .map(scopeString -> ResponseMessage.builder().message(scopeString).build())
                                      .collect(Collectors.toList()));

    errorBody.setMessage(exception.getMessage());

    return Response.status(BAD_REQUEST).entity(errorBody).build();
  }
}
