/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.exception;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.ReportTarget.REST_API;
import static io.harness.rest.RestResponse.Builder.aRestResponse;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;

import java.util.List;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class WingsExceptionMapper implements ExceptionMapper<WingsException> {
  @Override
  public Response toResponse(WingsException exception) {
    ExceptionLogger.logProcessedMessages_asWarn(exception, MANAGER, log);
    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(exception, REST_API);

    ErrorCode errorCode = exception.getCode() != null ? exception.getCode() : ErrorCode.UNKNOWN_ERROR;

    return Response.status(resolveHttpStatus(responseMessages))
        .entity(aRestResponse().withResponseMessages(responseMessages).build())
        .header("X-Harness-Error", errorCode.toString())
        .build();
  }

  private Status resolveHttpStatus(List<ResponseMessage> responseMessageList) {
    ErrorCode errorCode = null;
    if (isNotEmpty(responseMessageList)) {
      errorCode = responseMessageList.get(responseMessageList.size() - 1).getCode();
    }
    if (errorCode != null) {
      return Status.fromStatusCode(errorCode.getStatus().getCode());
    } else {
      return INTERNAL_SERVER_ERROR;
    }
  }
}
