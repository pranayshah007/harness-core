/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionLogger;
import io.harness.ng.core.Status;

import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class NGAccessDeniedExceptionMapper implements ExceptionMapper<NGAccessDeniedException> {
  @Context private ResourceInfo resourceInfo;

  @Override
  public Response toResponse(NGAccessDeniedException exception) {
    ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    ErrorCode errorCode = exception.getCode() != null ? exception.getCode() : ErrorCode.UNKNOWN_ERROR;
    AccessDeniedErrorDTO errorBody = new AccessDeniedErrorDTO(
        Status.ERROR, errorCode, exception.getMessage(), null, exception.getFailedPermissionChecks());
    return Response.status(Response.Status.fromStatusCode(errorCode.getStatus().getCode())).entity(errorBody).build();
  }
}
