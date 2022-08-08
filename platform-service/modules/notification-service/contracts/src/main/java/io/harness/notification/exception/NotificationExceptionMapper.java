/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.exception;

import io.harness.eraro.ErrorCode;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationExceptionMapper implements ExceptionMapper<NotificationException> {
  @Override
  public Response toResponse(NotificationException exception) {
    log.error("Exception occurred: {}", exception.getMessage(), exception);
    ErrorDTO errorDTO = ErrorDTO.newError(Status.ERROR, ErrorCode.UNKNOWN_ERROR, exception.getMessage());
    return Response.status(Response.Status.BAD_REQUEST).entity(errorDTO).type(MediaType.APPLICATION_JSON).build();
  }
}
