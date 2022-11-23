/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.exceptionmappers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.ReportTarget.REST_API;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;

import java.util.List;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class WingsExceptionMapperV2 implements ExceptionMapper<WingsException> {
  @Context private ResourceInfo resourceInfo;

  @Override
  public Response toResponse(WingsException exception) {
    ExceptionLogger.logProcessedMessages_asWarn(exception, MANAGER, log);

    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(exception, REST_API);
    ErrorCode errorCode = exception.getCode() != null ? exception.getCode() : ErrorCode.UNKNOWN_ERROR;
    ErrorDTO errorBody = ErrorDTO.newError(Status.ERROR, errorCode, null);
    errorBody.setResponseMessages(responseMessages);
    errorBody.setMetadata(exception.getMetadata());

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
