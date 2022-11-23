/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.exceptionmappers;

import io.harness.eraro.ErrorCode;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.FailureDTO;

import javax.ws.rs.NotAllowedException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotAllowedExceptionMapper implements ExceptionMapper<NotAllowedException> {
  @Override
  public Response toResponse(NotAllowedException exception) {
    FailureDTO failureDTO =
        FailureDTO.toBody(Status.FAILURE, ErrorCode.RESOURCE_NOT_FOUND_EXCEPTION, exception.getMessage(), null);
    return Response.status(Response.Status.METHOD_NOT_ALLOWED)
        .entity(failureDTO)
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
