/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.exception;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.ReportTarget.REST_API;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.WingsException;
import io.harness.logging.ExceptionLogger;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;

import java.util.List;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class GenericExceptionMapper.
 *
 * The current implementation is taken from 400-rest, it hasn't been moved to 12-commons as this one will evolve over
 * time
 * @param <T> the generic type
 */
@Slf4j
@Provider
public class WingsExceptionMapper<T> implements ExceptionMapper<WingsException> {
  @Context private ResourceInfo resourceInfo;

  @Override
  public Response toResponse(WingsException exception) {
    ExceptionLogger.logProcessedMessages(exception, MANAGER, log);

    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(exception, REST_API);
    ErrorCode errorCode = exception.getCode() != null ? exception.getCode() : ErrorCode.UNKNOWN_ERROR;
    ErrorDTO errorBody = ErrorDTO.newError(Status.ERROR, errorCode, null);

    if (!responseMessages.isEmpty()) {
      errorBody.setMessage(responseMessages.get(0).getMessage());
    }

    return Response.status(resolveHttpStatus(responseMessages)).entity(errorBody).build();
  }

  private Response.Status resolveHttpStatus(List<ResponseMessage> responseMessageList) {
    ErrorCode errorCode = null;
    if (isNotEmpty(responseMessageList)) {
      errorCode = responseMessageList.get(responseMessageList.size() - 1).getCode();
    }
    if (errorCode != null) {
      return Response.Status.fromStatusCode(errorCode.getStatus().getCode());
    } else {
      return INTERNAL_SERVER_ERROR;
    }
  }
}
